from __future__ import annotations

from datetime import datetime

from sqlalchemy import JSON, Boolean, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from riseup.db import Base
from riseup.models.base import TimestampMixin


class EscalationLog(TimestampMixin, Base):
    """One escalation attempt: the agent's decision (for F14) plus its outcome (for F11)."""

    __tablename__ = "escalation_logs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    alarm_id: Mapped[int | None] = mapped_column(ForeignKey("alarms.id"), nullable=True, index=True)
    episode: Mapped[str] = mapped_column(String(64), index=True)
    attempt: Mapped[int] = mapped_column(Integer, default=1)
    trigger: Mapped[str] = mapped_column(String(32))
    method: Mapped[str | None] = mapped_column(String(32), nullable=True)
    decided_by: Mapped[str] = mapped_column(String(64))
    reasoning: Mapped[str] = mapped_column(Text, default="")
    confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    fallback_method: Mapped[str | None] = mapped_column(String(32), nullable=True)
    fallback_used: Mapped[bool] = mapped_column(Boolean, default=False)
    inputs: Mapped[dict] = mapped_column(JSON, default=dict)
    # pending | sent | failed | confirmed | no_response | skipped
    status: Mapped[str] = mapped_column(String(16), default="pending")
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    responded_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    response_time_seconds: Mapped[float | None] = mapped_column(Float, nullable=True)
