"""Entry points for scheduled jobs. Each job runs in its own unit of work."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Callable

if TYPE_CHECKING:
    from riseup.container import AppServices, Container

log = logging.getLogger(__name__)


class JobRunner:
    def __init__(self, container: "Container") -> None:
        self.container = container

    def _run(self, name: str, fn: Callable[["AppServices"], object]) -> None:
        try:
            with self.container.unit_of_work() as services:
                fn(services)
        except Exception:
            log.exception("Scheduled job %s failed", name)

    # alarms / check-ins
    def trigger_alarm(self, alarm_id: int) -> None:
        self._run("trigger_alarm", lambda s: s.alarm_scheduler.trigger_alarm(alarm_id))

    def alarm_timeout(self, alarm_id: int) -> None:
        self._run("alarm_timeout", lambda s: s.alarm_scheduler.timeout_handler(alarm_id))

    def send_checkin(self, checkin_id: int) -> None:
        self._run("send_checkin", lambda s: s.checkins.send(checkin_id))

    def checkin_timeout(self, checkin_id: int) -> None:
        self._run("checkin_timeout", lambda s: s.checkins.timeout_handler(checkin_id))

    # escalation (agent loop)
    def run_escalation(self, alarm_id: int, episode: str, trigger: str) -> None:
        self._run(
            "run_escalation",
            lambda s: s.agent.run(
                "escalate", {"alarm_id": alarm_id, "episode": episode, "trigger": trigger}
            ),
        )

    def escalation_followup(self, log_id: int) -> None:
        self._run("escalation_followup", lambda s: s.escalation_engine.followup(log_id))

    # reminders
    def reminders_due(self, user_id: int) -> None:
        self._run("reminders_due", lambda s: s.reminder_scheduler.on_due(user_id))

    def reminder_extra(self, reminder_id: int) -> None:
        self._run("reminder_extra", lambda s: s.reminder_scheduler.send_extra_reminder(reminder_id))

    def reminder_escalation(self, reminder_id: int) -> None:
        self._run(
            "reminder_escalation",
            lambda s: s.reminder_scheduler.send_escalation_reminder(reminder_id),
        )

    def reminder_final(self, reminder_id: int) -> None:
        self._run("reminder_final", lambda s: s.reminder_scheduler.send_final_reminder(reminder_id))

    def reminder_missed(self, reminder_id: int) -> None:
        self._run("reminder_missed", lambda s: s.reminder_scheduler.mark_missed(reminder_id))

    # learning
    def nightly_learning(self) -> None:
        def run_all(s: "AppServices") -> None:
            for user_id in s.repos.users.all_ids():
                s.learning.analyze_behavior(user_id)

        self._run("nightly_learning", run_all)
