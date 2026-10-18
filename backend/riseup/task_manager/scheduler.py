"""ReminderScheduler: due-time dispatch, prioritization hand-off, escalation levels (F09-F10)."""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import TYPE_CHECKING

from riseup.models import Reminder

if TYPE_CHECKING:
    from riseup.container import AppServices

LEVEL_COPY = {
    1: ("{title}", "Reminder: {title}"),
    2: ("Still to do: {title}", "Hey! Still need to {title_lower}. Tap to complete."),
    3: ("Last reminder: {title}", "Last reminder: {title} ({priority} priority). Complete now?"),
}


def due_job_id(user_id: int, at: datetime) -> str:
    return f"reminder-due:{user_id}:{at.strftime('%Y%m%d%H%M')}"


class ReminderScheduler:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    # scheduling -----------------------------------------------------------
    def schedule_reminder(self, reminder: Reminder) -> None:
        self.s.db.flush()
        run_at = max(reminder.due_at, self.s.now()).replace(second=0, microsecond=0)
        if run_at < reminder.due_at:
            run_at += timedelta(minutes=1)
        self.s.scheduler.schedule(
            due_job_id(reminder.user_id, run_at),
            run_at,
            self.s.jobs.reminders_due,
            user_id=reminder.user_id,
        )
        for index, iso in enumerate(reminder.extra_times or []):
            at = datetime.fromisoformat(iso)
            if at > self.s.now():
                self.s.scheduler.schedule(
                    f"reminder-extra:{reminder.id}:{index}",
                    at,
                    self.s.jobs.reminder_extra,
                    reminder_id=reminder.id,
                )

    def cancel(self, reminder: Reminder) -> None:
        for prefix in ("reminder-esc", "reminder-final", "reminder-miss"):
            self.s.scheduler.cancel(f"{prefix}:{reminder.id}")
        for index in range(len(reminder.extra_times or [])):
            self.s.scheduler.cancel(f"reminder-extra:{reminder.id}:{index}")

    # dispatch ---------------------------------------------------------------
    def on_due(self, user_id: int) -> list[Reminder]:
        now = self.s.now()
        due = self.s.repos.reminders.due_scheduled(user_id, now)
        if not due:
            return []
        ordered = due
        if len(due) > 1:
            profile = self.s.repos.profiles.for_user(user_id)
            result = self.s.prioritizer.prioritize_reminders(
                due, profile, now, self.s.user(user_id).timezone
            )
            by_id = {r.id: r for r in due}
            ordered = [by_id[i] for i in result.order]
            self.s.repos.logs.add_reminder_log(
                user_id,
                "prioritized",
                details={
                    "order": result.order,
                    "titles": [r.title for r in ordered],
                    "reasoning": result.reasoning,
                    "decided_by": result.decided_by,
                    "fallback_used": result.fallback_used,
                    "inputs": result.inputs,
                },
            )
        for rank, reminder in enumerate(ordered, start=1):
            self.send_primary_reminder(reminder, rank=rank)
        return ordered

    def _deliver(self, reminder: Reminder, level: int) -> tuple[str, bool]:
        user = self.s.user(reminder.user_id)
        prefs = self.s.repos.prefs.for_user(user.id)
        title_t, body_t = LEVEL_COPY[level]
        fmt = {
            "title": reminder.title,
            "title_lower": reminder.title[:1].lower() + reminder.title[1:],
            "priority": reminder.priority,
        }
        methods = prefs.notification_methods or []
        if not methods:
            return "dashboard", False
        channel = (
            reminder.notification_type if reminder.notification_type in methods else methods[0]
        )
        result = self.s.tool_manager.send_reminder(
            user, prefs, channel, title_t.format(**fmt), body_t.format(**fmt)
        )
        return channel, result.success

    def send_primary_reminder(self, reminder: Reminder, rank: int = 1) -> None:
        if reminder.status != "scheduled":
            return
        now = self.s.now()
        channel, delivered = self._deliver(reminder, 1)
        reminder.escalation_level = 1
        reminder.first_notified_at = now
        reminder.change_status(
            "notifying",
            self.s.emitter,
            event="primary_sent",
            level=1,
            channel=channel,
            delivered=delivered,
            rank=rank,
        )
        if not delivered:
            self.s.emitter.emit(
                "reminder",
                reminder,
                "notifying",
                payload={"event": "queued", "level": 1, "channel": "dashboard"},
            )
        self.s.scheduler.schedule(
            f"reminder-esc:{reminder.id}",
            now + timedelta(minutes=self.s.settings.reminder_escalation_minutes),
            self.s.jobs.reminder_escalation,
            reminder_id=reminder.id,
        )

    def send_extra_reminder(self, reminder_id: int) -> None:
        reminder = self.s.repos.reminders.get(reminder_id)
        if reminder is None or not reminder.is_open:
            return
        channel, delivered = self._deliver(reminder, 1)
        self.s.emitter.emit(
            "reminder",
            reminder,
            reminder.status,
            payload={"event": "extra_sent", "channel": channel, "delivered": delivered},
        )

    def send_escalation_reminder(self, reminder_id: int) -> None:
        reminder = self.s.repos.reminders.get(reminder_id)
        if reminder is None or reminder.status != "notifying" or reminder.escalation_level != 1:
            return
        channel, delivered = self._deliver(reminder, 2)
        reminder.escalation_level = 2
        self.s.emitter.emit(
            "reminder",
            reminder,
            "notifying",
            payload={
                "event": "escalation_sent",
                "level": 2,
                "channel": channel,
                "delivered": delivered,
            },
        )
        self.s.scheduler.schedule(
            f"reminder-final:{reminder.id}",
            reminder.first_notified_at + timedelta(minutes=self.s.settings.reminder_final_minutes),
            self.s.jobs.reminder_final,
            reminder_id=reminder.id,
        )

    def send_final_reminder(self, reminder_id: int) -> None:
        reminder = self.s.repos.reminders.get(reminder_id)
        if reminder is None or reminder.status != "notifying" or reminder.escalation_level != 2:
            return
        channel, delivered = self._deliver(reminder, 3)
        reminder.escalation_level = 3
        self.s.emitter.emit(
            "reminder",
            reminder,
            "notifying",
            payload={"event": "final_sent", "level": 3, "channel": channel, "delivered": delivered},
        )
        self.s.scheduler.schedule(
            f"reminder-miss:{reminder.id}",
            reminder.first_notified_at + timedelta(minutes=self.s.settings.reminder_missed_minutes),
            self.s.jobs.reminder_missed,
            reminder_id=reminder.id,
        )

    def mark_missed(self, reminder_id: int) -> None:
        reminder = self.s.repos.reminders.get(reminder_id)
        if reminder is None or reminder.status != "notifying":
            return
        reminder.mark_missed(self.s.emitter)
        self.s.reminder_controller.spawn_next(reminder)
