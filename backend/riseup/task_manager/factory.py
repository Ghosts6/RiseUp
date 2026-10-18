"""ReminderFactory (Factory Method): one place for reminder construction and defaults."""

from __future__ import annotations

import uuid
from datetime import datetime, timedelta
from typing import TYPE_CHECKING, Any

from riseup.errors import ValidationFailed
from riseup.models import Reminder
from riseup.timeutil import to_local, to_utc_naive

if TYPE_CHECKING:
    from riseup.container import AppServices

DEFAULTS = {
    "priority": "medium",
    "category": "other",
    "repeat_pattern": "none",
    "notification_type": "push",
}


def parse_due(value: Any, tz_name: str | None) -> datetime:
    """Accept a datetime or ISO string; naive values are in the user's timezone."""
    if isinstance(value, datetime):
        dt = value
    elif isinstance(value, str) and value.strip():
        try:
            dt = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
        except ValueError as exc:
            raise ValidationFailed("Invalid date/time. Use YYYY-MM-DD HH:MM", field="due") from exc
    else:
        raise ValidationFailed("Due date/time is required", field="due")
    return to_utc_naive(dt, tz_name)


class ReminderFactory:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    def create(
        self, user_id: int, fields: dict[str, Any], source: str = "form", allow_past: bool = False
    ) -> Reminder:
        user = self.s.user(user_id)
        due_at = parse_due(fields.get("due") or fields.get("due_at"), user.timezone)
        now = self.s.now()
        if due_at < now - timedelta(minutes=1) and not allow_past:
            suggestion = due_at
            while suggestion < now:
                suggestion += timedelta(days=1)
            raise ValidationFailed(
                "This time has passed. Set for tomorrow instead?",
                field="due",
                suggestion=to_local(suggestion, user.timezone).strftime("%Y-%m-%dT%H:%M"),
            )
        data = {k: fields.get(k) or v for k, v in DEFAULTS.items()}
        extra_times = [
            parse_due(t, user.timezone).isoformat() for t in (fields.get("extra_times") or [])
        ]
        reminder = Reminder(
            user_id=user_id,
            title=(fields.get("title") or "").strip(),
            notes=fields.get("notes"),
            due_at=due_at,
            extra_times=extra_times,
            source=source,
            **data,
        )
        if reminder.repeat_pattern != "none":
            reminder.series_id = uuid.uuid4().hex[:12]
            reminder.series_time = to_local(due_at, user.timezone).strftime("%H:%M")
        reminder.validate(now)
        return reminder

    def next_occurrence(self, reminder: Reminder, due_at: datetime) -> Reminder:
        return Reminder(
            user_id=reminder.user_id,
            title=reminder.title,
            notes=reminder.notes,
            due_at=due_at,
            priority=reminder.priority,
            category=reminder.category,
            repeat_pattern=reminder.repeat_pattern,
            series_id=reminder.series_id,
            series_time=reminder.series_time,
            notification_type=reminder.notification_type,
            source=reminder.source,
        )
