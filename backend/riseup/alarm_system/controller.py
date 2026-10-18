"""AlarmController: alarm CRUD (F01) and verification flow (F02)."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from riseup.alarm_system.verification import VerificationResponse
from riseup.alarm_system.verification.factory import MAX_CAMERA_FAILURES
from riseup.errors import Conflict, LimitReached
from riseup.models import Alarm

if TYPE_CHECKING:
    from riseup.container import AppServices

EDITABLE_FIELDS = {
    "time",
    "label",
    "sound",
    "vibration",
    "repeat_pattern",
    "snooze_enabled",
    "verification_task_type",
    "custom_task_text",
    "priority",
}
ALARM_DEFAULTS = {
    "label": "Alarm",
    "sound": "classic",
    "vibration": "medium",
    "repeat_pattern": "once",
    "snooze_enabled": False,
    "priority": 0,
}


class AlarmController:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    def list_alarms(self, user_id: int) -> list[Alarm]:
        return self.s.repos.alarms.for_user(user_id)

    def get(self, user_id: int, alarm_id: int) -> Alarm:
        return self.s.repos.alarms.get_or_404(alarm_id, user_id)

    def create_alarm(self, user_id: int, data: dict[str, Any]) -> Alarm:
        if self.s.repos.alarms.count_for_user(user_id) >= self.s.settings.max_alarms_per_user:
            raise LimitReached("Maximum alarms reached. Delete an alarm to add another.")
        prefs = self.s.repos.prefs.for_user(user_id)
        fields = {**ALARM_DEFAULTS, "verification_task_type": prefs.default_task_type}
        fields.update({k: v for k, v in data.items() if k in EDITABLE_FIELDS and v is not None})
        alarm = Alarm(user_id=user_id, is_active=data.get("is_active", True), **fields)
        alarm.validate()
        self.s.repos.alarms.save(alarm)
        self.s.alarm_scheduler.schedule_alarm(alarm)
        return alarm

    def edit_alarm(self, user_id: int, alarm_id: int, data: dict[str, Any]) -> Alarm:
        alarm = self.get(user_id, alarm_id)
        if alarm.is_running:
            raise Conflict("This alarm is running; edit it after it finishes.")
        for key, value in data.items():
            if key in EDITABLE_FIELDS and value is not None:
                setattr(alarm, key, value)
        alarm.validate()
        self.s.alarm_scheduler.schedule_alarm(alarm)
        return alarm

    def toggle(self, user_id: int, alarm_id: int, active: bool | None = None) -> Alarm:
        alarm = self.get(user_id, alarm_id)
        if alarm.is_running and active is False:
            raise Conflict("A running alarm cannot be disabled; complete the challenge first.")
        alarm.is_active = (not alarm.is_active) if active is None else active
        self.s.alarm_scheduler.schedule_alarm(alarm)
        return alarm

    def delete(self, user_id: int, alarm_id: int) -> None:
        alarm = self.get(user_id, alarm_id)
        if alarm.is_running:
            raise Conflict("A running alarm cannot be deleted; complete the challenge first.")
        self.s.alarm_scheduler.cancel_alarm(alarm)
        self.s.repos.alarms.delete(alarm)

    def trigger_now(self, user_id: int, alarm_id: int) -> Alarm:
        """Ring an alarm immediately (demo / testing helper)."""
        alarm = self.get(user_id, alarm_id)
        result = self.s.alarm_scheduler.trigger_alarm(alarm.id, force=True)
        return result or alarm

    def get_challenge(self, user_id: int, alarm_id: int) -> dict[str, Any]:
        alarm = self.get(user_id, alarm_id)
        if not alarm.current_task:
            raise Conflict("Alarm is not ringing")
        task = self.s.container.task_factory.from_state(alarm.current_task)
        return {
            "alarm_id": alarm.id,
            "status": alarm.status,
            "label": alarm.label,
            "triggered_at": alarm.triggered_at,
            "snooze_available": alarm.snooze_enabled
            and alarm.snooze_count < self.s.settings.max_snoozes
            and alarm.status == "ringing",
            "challenge": task.get_task_prompt(),
        }

    def verify(self, user_id: int, alarm_id: int, response: VerificationResponse) -> dict[str, Any]:
        alarm = self.get(user_id, alarm_id)
        if not alarm.current_task or alarm.status not in ("ringing", "escalating"):
            raise Conflict("Alarm is not ringing")
        factory = self.s.container.task_factory
        task = factory.from_state(alarm.current_task)
        result = task.validate(response)
        logs = self.s.repos.logs

        if not result.success:
            alarm.failed_attempts += 1
            message = result.message
            new_task = None
            if result.camera_error:
                alarm.camera_failures += 1
                if alarm.camera_failures >= MAX_CAMERA_FAILURES:
                    new_task = factory.create(
                        alarm.verification_task_type, alarm.custom_task_text, alarm.camera_failures
                    )
                    message = "Camera unavailable. Switched to a math puzzle instead."
            elif task.resets_on_failure():
                new_task = factory.create(task.task_type, alarm.custom_task_text)
            if new_task is not None:
                alarm.current_task = new_task.to_state()
            logs.add_alarm_log(
                alarm,
                user_id,
                "failed_attempt",
                attempts=alarm.failed_attempts,
                camera_error=result.camera_error,
            )
            current = new_task or task
            return {
                "success": False,
                "message": message,
                "challenge": current.get_task_prompt(),
                "attempts": alarm.failed_attempts,
            }

        now = self.s.now()
        seconds = (now - alarm.triggered_at).total_seconds() if alarm.triggered_at else None
        logs.add_alarm_log(
            alarm,
            user_id,
            "verified",
            seconds_to_verify=seconds,
            attempts=alarm.failed_attempts + 1,
        )
        if alarm.status == "escalating":
            self.s.escalation_engine.acknowledge(alarm, via="verification")
        checkin = self.s.checkins.schedule(alarm)
        if checkin is None:
            self.s.alarm_scheduler.finish(alarm, "completed")
        else:
            self.s.scheduler.cancel(f"alarm-timeout:{alarm.id}")
            alarm.stop("awaiting_checkin", self.s.emitter)
        return {
            "success": True,
            "message": "Task completed! Rest well.",
            "checkin_at": checkin.scheduled_at if checkin else None,
            "seconds_to_verify": seconds,
        }

    def snooze(self, user_id: int, alarm_id: int) -> Alarm:
        return self.s.alarm_scheduler.snooze(self.get(user_id, alarm_id))

    def escalation_status(self, user_id: int, alarm_id: int) -> dict[str, Any]:
        alarm = self.get(user_id, alarm_id)
        logs = self.s.repos.escalations.for_alarm(alarm.id)
        if alarm.escalation_episode:
            current = [entry for entry in logs if entry.episode == alarm.escalation_episode]
        else:
            current = logs[-5:]
        return {
            "alarm_id": alarm.id,
            "status": alarm.status,
            "episode": alarm.escalation_episode,
            "attempts": current,
        }
