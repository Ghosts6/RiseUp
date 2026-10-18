from __future__ import annotations

from datetime import datetime

from sqlalchemy import JSON, Boolean, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from riseup.clock import db_now
from riseup.db import Base
from riseup.models.base import StatusSubjectMixin, TimestampMixin

TASK_TYPES = ["math", "trivia", "object_detection", "custom"]
VIBRATION_LEVELS = ["low", "medium", "high"]
# Alarm lifecycle: idle -> scheduled -> ringing -> (snoozed | escalating) -> awaiting_checkin
#                  -> completed | missed ; disabled when toggled off
ACTIVE_ALARM_STATES = {"ringing", "snoozed", "escalating", "awaiting_checkin"}


class Alarm(TimestampMixin, StatusSubjectMixin, Base):
    __tablename__ = "alarms"
    SUBJECT_TYPE = "alarm"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    time: Mapped[str] = mapped_column(String(5))
    label: Mapped[str] = mapped_column(String(120), default="Alarm")
    sound: Mapped[str] = mapped_column(String(64), default="classic")
    vibration: Mapped[str] = mapped_column(String(16), default="medium")
    repeat_pattern: Mapped[str] = mapped_column(String(64), default="once")
    snooze_enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    verification_task_type: Mapped[str] = mapped_column(String(32), default="math")
    custom_task_text: Mapped[str | None] = mapped_column(Text, nullable=True)
    priority: Mapped[int] = mapped_column(Integer, default=0)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    status: Mapped[str] = mapped_column(String(32), default="idle")
    next_trigger_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    triggered_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    snooze_count: Mapped[int] = mapped_column(Integer, default=0)
    failed_attempts: Mapped[int] = mapped_column(Integer, default=0)
    camera_failures: Mapped[int] = mapped_column(Integer, default=0)
    current_task: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    escalation_episode: Mapped[str | None] = mapped_column(String(64), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=db_now, onupdate=db_now)

    @property
    def is_running(self) -> bool:
        return self.status in ACTIVE_ALARM_STATES

    def validate(self) -> None:
        from riseup.errors import ValidationFailed
        from riseup.timeutil import is_valid_hhmm, validate_repeat

        if not is_valid_hhmm(self.time):
            raise ValidationFailed("Please enter time in HH:MM format", field="time")
        if self.vibration not in VIBRATION_LEVELS:
            raise ValidationFailed("Vibration must be low, medium, or high", field="vibration")
        if self.verification_task_type not in TASK_TYPES:
            raise ValidationFailed(
                f"Task type must be one of {', '.join(TASK_TYPES)}", field="verification_task_type"
            )
        if self.verification_task_type == "custom" and not (self.custom_task_text or "").strip():
            raise ValidationFailed("Custom tasks need a description", field="custom_task_text")
        if not (self.label or "").strip():
            self.label = "Alarm"
        try:
            validate_repeat(self.repeat_pattern)
        except ValueError as exc:
            raise ValidationFailed(str(exc), field="repeat_pattern") from exc

    def trigger(self, now: datetime, task_state: dict, emitter) -> None:
        self.triggered_at = now
        self.current_task = task_state
        self.failed_attempts = 0
        self.change_status("ringing", emitter, label=self.label, time=self.time)

    def stop(self, next_status: str, emitter) -> None:
        self.current_task = None
        self.change_status(next_status, emitter, label=self.label, time=self.time)


class AlarmLog(TimestampMixin, Base):
    """Alarm events used for analytics (wake times, verification stats)."""

    __tablename__ = "alarm_logs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    alarm_id: Mapped[int | None] = mapped_column(ForeignKey("alarms.id"), nullable=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    event: Mapped[str] = mapped_column(String(32))
    task_type: Mapped[str | None] = mapped_column(String(32), nullable=True)
    details: Mapped[dict] = mapped_column(JSON, default=dict)


class CheckIn(TimestampMixin, StatusSubjectMixin, Base):
    __tablename__ = "checkins"
    SUBJECT_TYPE = "checkin"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    alarm_id: Mapped[int | None] = mapped_column(ForeignKey("alarms.id"), nullable=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    scheduled_at: Mapped[datetime] = mapped_column(DateTime)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    responded_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    channel: Mapped[str | None] = mapped_column(String(16), nullable=True)
    # scheduled | sent | confirmed | late | missed | skipped
    status: Mapped[str] = mapped_column(String(16), default="scheduled")
