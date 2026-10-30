"""Post-wake check-in (F03): ask again 5-10 min after verification."""

from __future__ import annotations

from datetime import timedelta
from typing import TYPE_CHECKING

from riseup.errors import NotFound
from riseup.models import CheckIn

if TYPE_CHECKING:
    from riseup.container import AppServices
    from riseup.models import Alarm


def send_job_id(checkin_id: int) -> str:
    return f"checkin-send:{checkin_id}"


def timeout_job_id(checkin_id: int) -> str:
    return f"checkin-timeout:{checkin_id}"


class CheckInService:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    def schedule(self, alarm: "Alarm") -> CheckIn | None:
        prefs = self.s.repos.prefs.for_user(alarm.user_id)
        now = self.s.now()
        if not prefs.checkin_enabled:
            skipped = CheckIn(
                alarm_id=alarm.id, user_id=alarm.user_id, scheduled_at=now, status="skipped"
            )
            self.s.db.add(skipped)
            self.s.repos.logs.add_alarm_log(alarm, alarm.user_id, "checkin_skipped")
            return None
        delay = min(max(prefs.checkin_delay_minutes, 5), 10)
        checkin = CheckIn(
            alarm_id=alarm.id, user_id=alarm.user_id, scheduled_at=now + timedelta(minutes=delay)
        )
        self.s.db.add(checkin)
        self.s.db.flush()
        self.s.scheduler.schedule(
            send_job_id(checkin.id),
            checkin.scheduled_at,
            self.s.jobs.send_checkin,
            checkin_id=checkin.id,
        )
        return checkin

    def send(self, checkin_id: int) -> CheckIn | None:
        checkin = self.s.repos.checkins.get(checkin_id)
        if checkin is None or checkin.status != "scheduled":
            return None
        user = self.s.user(checkin.user_id)
        prefs = self.s.repos.prefs.for_user(user.id)
        result = self.s.tool_manager.send_push_notification(
            user, prefs, "Are you still awake?", "Tap to confirm you're up."
        )
        if not result.success:
            result = self.s.tool_manager.send_sms(
                user.phone, "RiseUp check-in: Reply YES to confirm you're awake."
            )
        now = self.s.now()
        checkin.sent_at = now
        checkin.channel = result.channel if result.success else "dashboard"
        checkin.change_status(
            "sent", self.s.emitter, channel=checkin.channel, delivered=result.success
        )
        self.s.scheduler.schedule(
            timeout_job_id(checkin.id),
            now + timedelta(minutes=self.s.settings.checkin_response_minutes),
            self.s.jobs.checkin_timeout,
            checkin_id=checkin.id,
        )
        return checkin

    def record_response(self, user_id: int) -> CheckIn:
        checkin = self.s.repos.checkins.latest_open(user_id)
        if checkin is None:
            raise NotFound("No pending check-in")
        now = self.s.now()
        self.s.scheduler.cancel(send_job_id(checkin.id))
        self.s.scheduler.cancel(timeout_job_id(checkin.id))
        late = checkin.status == "missed"
        checkin.responded_at = now
        reference = checkin.sent_at or checkin.scheduled_at
        response_seconds = max((now - reference).total_seconds(), 0)
        checkin.change_status(
            "late" if late else "confirmed", self.s.emitter, response_seconds=response_seconds
        )
        alarm = self.s.repos.alarms.get(checkin.alarm_id) if checkin.alarm_id else None
        if alarm is not None:
            self.s.repos.logs.add_alarm_log(
                alarm,
                user_id,
                "checkin_late" if late else "checkin_confirmed",
                response_seconds=response_seconds,
            )
            if alarm.status == "escalating":
                self.s.escalation_engine.acknowledge(alarm, via="checkin")
            if alarm.status in ("awaiting_checkin", "escalating"):
                self.s.alarm_scheduler.finish(alarm, "completed")
        return checkin

    def timeout_handler(self, checkin_id: int) -> None:
        checkin = self.s.repos.checkins.get(checkin_id)
        if checkin is None or checkin.status != "sent":
            return
        checkin.change_status("missed", self.s.emitter)
        alarm = self.s.repos.alarms.get(checkin.alarm_id) if checkin.alarm_id else None
        if alarm is None:
            return
        self.s.repos.logs.add_alarm_log(alarm, alarm.user_id, "checkin_missed")
        if alarm.status == "awaiting_checkin":
            self.s.alarm_scheduler.start_escalation(alarm, "checkin_missed")

    def list_recent(self, user_id: int) -> list[CheckIn]:
        return self.s.repos.checkins.for_user(user_id)
