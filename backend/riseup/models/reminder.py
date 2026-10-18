from __future__ import annotations

from datetime import datetime

from sqlalchemy import JSON, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from riseup.clock import db_now
from riseup.db import Base
from riseup.models.base import StatusSubjectMixin, TimestampMixin

PRIORITIES = ["low", "medium", "high", "critical"]
CATEGORIES = ["work", "personal", "health", "other"]
REMINDER_REPEATS = ["none", "daily", "weekly", "weekdays"]
# scheduled -> notifying (levels 1..3) -> completed | missed
OPEN_REMINDER_STATES = {"scheduled", "notifying"}


class Reminder(TimestampMixin, StatusSubjectMixin, Base):
    __tablename__ = "reminders"
    SUBJECT_TYPE = "reminder"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    title: Mapped[str] = mapped_column(String(200))
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)
    due_at: Mapped[datetime] = mapped_column(DateTime, index=True)
    priority: Mapped[str] = mapped_column(String(16), default="medium")
    category: Mapped[str] = mapped_column(String(16), default="other")
    repeat_pattern: Mapped[str] = mapped_column(String(16), default="none")
    series_id: Mapped[str | None] = mapped_column(String(64), nullable=True, index=True)
    series_time: Mapped[str | None] = mapped_column(String(5), nullable=True)
    notification_type: Mapped[str] = mapped_column(String(16), default="push")
    extra_times: Mapped[list] = mapped_column(JSON, default=list)
    status: Mapped[str] = mapped_column(String(16), default="scheduled")
    escalation_level: Mapped[int] = mapped_column(Integer, default=0)
    first_notified_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    snooze_count: Mapped[int] = mapped_column(Integer, default=0)
    source: Mapped[str] = mapped_column(String(16), default="form")
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=db_now, onupdate=db_now)

    @property
    def is_open(self) -> bool:
        return self.status in OPEN_REMINDER_STATES

    def validate(self, now: datetime | None = None) -> None:
        from riseup.errors import ValidationFailed

        if not (self.title or "").strip():
            raise ValidationFailed("Task title is required", field="title")
        if self.priority not in PRIORITIES:
            raise ValidationFailed(
                f"Priority must be one of {', '.join(PRIORITIES)}", field="priority"
            )
        if self.category not in CATEGORIES:
            raise ValidationFailed(
                f"Category must be one of {', '.join(CATEGORIES)}", field="category"
            )
        if self.repeat_pattern not in REMINDER_REPEATS:
            raise ValidationFailed(
                f"Repeat must be one of {', '.join(REMINDER_REPEATS)}", field="repeat_pattern"
            )
        if self.due_at is None:
            raise ValidationFailed("Due date/time is required", field="due")

    def mark_complete(self, now: datetime, emitter, **payload) -> None:
        level = self.escalation_level
        self.completed_at = now
        self.change_status("completed", emitter, event="completed", level=level, **payload)

    def mark_missed(self, emitter) -> None:
        self.change_status("missed", emitter, event="missed", level=self.escalation_level)


class ReminderLog(TimestampMixin, Base):
    """Reminder events, plus prioritization decisions (reasoning) for F14."""

    __tablename__ = "reminder_logs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    reminder_id: Mapped[int | None] = mapped_column(Integer, nullable=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    event: Mapped[str] = mapped_column(String(32))
    level: Mapped[int | None] = mapped_column(Integer, nullable=True)
    channel: Mapped[str | None] = mapped_column(String(16), nullable=True)
    category: Mapped[str | None] = mapped_column(String(16), nullable=True)
    details: Mapped[dict] = mapped_column(JSON, default=dict)
