"""AlarmScheduler: places trigger jobs, rings alarms, handles snooze and timeout (F01-F02)."""

from __future__ import annotations

import uuid
from datetime import timedelta
from typing import TYPE_CHECKING

from riseup.errors import Conflict, LimitReached
from riseup.models import Alarm
from riseup.timeutil import next_alarm_occurrence

if TYPE_CHECKING:
    from riseup.container import AppServices


def trigger_job_id(alarm_id: int) -> str:
    return f"alarm:{alarm_id}"


def snooze_job_id(alarm_id: int) -> str:
    return f"alarm-snooze:{alarm_id}"


def timeout_job_id(alarm_id: int) -> str:
    return f"alarm-timeout:{alarm_id}"


class AlarmScheduler:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    def schedule_alarm(self, alarm: Alarm) -> None:
        if not alarm.is_active:
            self.cancel_alarm(alarm)
            alarm.next_trigger_at = None
            if not alarm.is_running:
                alarm.status = "disabled"
            return
        user = self.s.user(alarm.user_id)
        alarm.next_trigger_at = next_alarm_occurrence(
            alarm.time, alarm.repeat_pattern, user.timezone, self.s.now()
        )
        if not alarm.is_running:
            alarm.status = "scheduled"
        self.s.db.flush()
        self.s.scheduler.schedule(
            trigger_job_id(alarm.id),
            alarm.next_trigger_at,
            self.s.jobs.trigger_alarm,
            alarm_id=alarm.id,
        )

    def cancel_alarm(self, alarm: Alarm) -> None:
        for job_id in (trigger_job_id(alarm.id), snooze_job_id(alarm.id), timeout_job_id(alarm.id)):
            self.s.scheduler.cancel(job_id)

    def trigger_alarm(self, alarm_id: int, force: bool = False) -> Alarm | None:
        alarm = self.s.repos.alarms.get(alarm_id)
        if alarm is None or (not alarm.is_active and not force):
            return None
        if alarm.status in ("ringing", "escalating", "awaiting_checkin"):
            return alarm  # already in progress; ignore duplicate trigger
        snoozed = alarm.status == "snoozed"
        if not snoozed:
            alarm.snooze_count = 0
            alarm.camera_failures = 0
            alarm.escalation_episode = None
        task = self.s.container.task_factory.create(
            alarm.verification_task_type, alarm.custom_task_text, alarm.camera_failures
        )
        alarm.trigger(self.s.now(), task.to_state(), self.s.emitter)
        self.s.repos.logs.add_alarm_log(alarm, alarm.user_id, "triggered", snoozed=snoozed)
        self.s.scheduler.schedule(
            timeout_job_id(alarm.id),
            self.s.now() + timedelta(seconds=self.s.settings.verification_timeout_seconds),
            self.s.jobs.alarm_timeout,
            alarm_id=alarm.id,
        )
        if not snoozed and alarm.repeat_pattern != "once" and alarm.is_active:
            self.schedule_alarm(alarm)
        else:
            alarm.next_trigger_at = None
        return alarm

    def timeout_handler(self, alarm_id: int) -> None:
        """No verification within the timeout: hand over to the agent (F04)."""
        alarm = self.s.repos.alarms.get(alarm_id)
        if alarm is None or alarm.status != "ringing":
            return
        self.s.repos.logs.add_alarm_log(
            alarm, alarm.user_id, "timeout", seconds=self.s.settings.verification_timeout_seconds
        )
        self.start_escalation(alarm, "verification_timeout")

    def start_escalation(self, alarm: Alarm, trigger: str) -> None:
        if alarm.status == "escalating":
            return
        alarm.escalation_episode = uuid.uuid4().hex[:12]
        alarm.change_status(
            "escalating",
            self.s.emitter,
            trigger=trigger,
            episode=alarm.escalation_episode,
            label=alarm.label,
            time=alarm.time,
        )

    def snooze(self, alarm: Alarm) -> Alarm:
        if alarm.status != "ringing":
            raise Conflict("Only a ringing alarm can be snoozed")
        if not alarm.snooze_enabled:
            raise Conflict("Snooze is disabled for this alarm")
        if alarm.snooze_count >= self.s.settings.max_snoozes:
            raise LimitReached(f"Snooze limit reached ({self.s.settings.max_snoozes})")
        alarm.snooze_count += 1
        self.s.scheduler.cancel(timeout_job_id(alarm.id))
        alarm.stop("snoozed", self.s.emitter)
        resume_at = self.s.now() + timedelta(minutes=self.s.settings.snooze_minutes)
        self.s.repos.logs.add_alarm_log(alarm, alarm.user_id, "snoozed", count=alarm.snooze_count)
        self.s.scheduler.schedule(
            snooze_job_id(alarm.id), resume_at, self.s.jobs.trigger_alarm, alarm_id=alarm.id
        )
        return alarm

    def finish(self, alarm: Alarm, status: str) -> None:
        """Close an alarm run as `completed` or `missed`."""
        self.s.scheduler.cancel(timeout_job_id(alarm.id))
        alarm.snooze_count = 0
        alarm.stop(status, self.s.emitter)
        if alarm.repeat_pattern == "once":
            alarm.is_active = False
            alarm.next_trigger_at = None
