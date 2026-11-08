"""ReminderController: reminder CRUD, complete, snooze (F08)."""

from __future__ import annotations

from datetime import timedelta
from typing import TYPE_CHECKING, Any

from riseup.errors import Conflict, ValidationFailed
from riseup.models import Reminder
from riseup.task_manager.factory import parse_due
from riseup.timeutil import next_reminder_occurrence, to_local

if TYPE_CHECKING:
    from riseup.container import AppServices

EDITABLE = {"title", "notes", "priority", "category", "repeat_pattern", "notification_type"}


class ReminderController:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    def list(
        self,
        user_id: int,
        status: str | None = None,
        category: str | None = None,
        priority: str | None = None,
    ) -> list[Reminder]:
        return self.s.repos.reminders.for_user(user_id, status, category, priority)

    def get(self, user_id: int, reminder_id: int) -> Reminder:
        return self.s.repos.reminders.get_or_404(reminder_id, user_id)

    def create(self, user_id: int, data: dict[str, Any], source: str = "form") -> Reminder:
        reminder = self.s.reminder_factory.create(
            user_id, data, source=source, allow_past=bool(data.get("allow_past"))
        )
        self.s.repos.reminders.save(reminder)
        reminder.change_status(
            "scheduled",
            self.s.emitter,
            event="created",
            source=source,
            due_at=reminder.due_at.isoformat(),
        )
        self.s.reminder_scheduler.schedule_reminder(reminder)
        return reminder

    def update(
        self, user_id: int, reminder_id: int, data: dict[str, Any], scope: str = "this"
    ) -> Reminder:
        reminder = self.get(user_id, reminder_id)
        if reminder.status == "completed":
            raise Conflict("Completed reminders cannot be edited")
        if scope not in ("this", "all"):
            raise ValidationFailed("scope must be 'this' or 'all'")
        for key in EDITABLE:
            if data.get(key) is not None:
                setattr(reminder, key, data[key])
        user = self.s.user(user_id)
        due_value = data.get("due") or data.get("due_at")
        reschedule = False
        if due_value:
            reminder.due_at = parse_due(due_value, user.timezone)
            if reminder.due_at < self.s.now():
                raise ValidationFailed(
                    "This time has passed. Set for tomorrow instead?", field="due"
                )
            reschedule = True
        if "extra_times" in data and data["extra_times"] is not None:
            reminder.extra_times = [
                parse_due(t, user.timezone).isoformat() for t in data["extra_times"]
            ]
            reschedule = True
        if reminder.repeat_pattern != "none":
            reminder.series_id = reminder.series_id or f"s{reminder.id}"
            if scope == "all" or not reminder.series_time:
                reminder.series_time = to_local(reminder.due_at, user.timezone).strftime("%H:%M")
        reminder.validate()
        if reschedule:
            self.s.reminder_scheduler.cancel(reminder)
            reminder.escalation_level = 0
            reminder.first_notified_at = None
            reminder.status = "scheduled"
            self.s.reminder_scheduler.schedule_reminder(reminder)
        self.s.emitter.emit(
            "reminder", reminder, reminder.status, payload={"event": "updated", "scope": scope}
        )
        return reminder

    def complete(self, user_id: int, reminder_id: int, already_done: bool = False) -> Reminder:
        reminder = self.get(user_id, reminder_id)
        if reminder.status == "completed":
            raise Conflict("Reminder is already completed")
        now = self.s.now()
        response = (
            (now - reminder.first_notified_at).total_seconds()
            if reminder.first_notified_at
            else None
        )
        self.s.reminder_scheduler.cancel(reminder)
        reminder.mark_complete(
            now,
            self.s.emitter,
            already_done=already_done,
            response_seconds=response,
            reminders_needed=reminder.escalation_level,
        )
        self.spawn_next(reminder)
        return reminder

    def snooze(self, user_id: int, reminder_id: int, minutes: int | None = None) -> Reminder:
        reminder = self.get(user_id, reminder_id)
        if not reminder.is_open:
            raise Conflict(f"Reminder is already {reminder.status}; it cannot be snoozed")
        minutes = minutes or self.s.settings.reminder_snooze_minutes
        if not 1 <= minutes <= 24 * 60:
            raise ValidationFailed("Snooze must be between 1 minute and 24 hours")
        self.s.reminder_scheduler.cancel(reminder)
        reminder.due_at = self.s.now() + timedelta(minutes=minutes)
        reminder.escalation_level = 0
        reminder.first_notified_at = None
        reminder.snooze_count += 1
        reminder.change_status("scheduled", self.s.emitter, event="snoozed", minutes=minutes)
        self.s.reminder_scheduler.schedule_reminder(reminder)
        return reminder

    def delete(self, user_id: int, reminder_id: int) -> None:
        reminder = self.get(user_id, reminder_id)
        self.s.reminder_scheduler.cancel(reminder)
        self.s.repos.logs.add_reminder_log(user_id, "deleted", reminder, title=reminder.title)
        self.s.repos.reminders.delete(reminder)

    def spawn_next(self, reminder: Reminder) -> Reminder | None:
        """Recurring series: create the next occurrence after completion or miss."""
        user = self.s.user(reminder.user_id)
        due = next_reminder_occurrence(
            reminder.due_at, reminder.repeat_pattern, reminder.series_time, user.timezone
        )
        if due is None:
            return None
        now = self.s.now()
        while due is not None and due <= now:
            due = next_reminder_occurrence(
                due, reminder.repeat_pattern, reminder.series_time, user.timezone
            )
        if due is None:
            return None
        nxt = self.s.reminder_factory.next_occurrence(reminder, due)
        self.s.repos.reminders.save(nxt)
        nxt.change_status(
            "scheduled",
            self.s.emitter,
            event="created",
            source="recurrence",
            due_at=due.isoformat(),
        )
        self.s.reminder_scheduler.schedule_reminder(nxt)
        return nxt

    def priority_preview(self, user_id: int) -> dict[str, Any]:
        window = self.s.now() + timedelta(hours=1)
        due = [r for r in self.list(user_id, status="open") if r.due_at <= window]
        profile = self.s.repos.profiles.for_user(user_id)
        result = self.s.prioritizer.prioritize_reminders(
            due, profile, self.s.now(), self.s.user(user_id).timezone
        )
        by_id = {r.id: r for r in due}
        return {
            "order": [by_id[i] for i in result.order],
            "reasoning": result.reasoning,
            "decided_by": result.decided_by,
            "fallback_used": result.fallback_used,
        }
