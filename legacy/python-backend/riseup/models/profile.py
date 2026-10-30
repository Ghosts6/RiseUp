from __future__ import annotations

from datetime import datetime

from sqlalchemy import JSON, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from riseup.clock import db_now
from riseup.db import Base
from riseup.models.base import TimestampMixin


class UserProfile(Base):
    """Pre-aggregated behavioural metrics (DP-03); the agent's long-term memory."""

    __tablename__ = "user_profiles"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    escalation_stats: Mapped[dict] = mapped_column(JSON, default=dict)
    alarm_patterns: Mapped[dict] = mapped_column(JSON, default=dict)
    task_completion_rates: Mapped[dict] = mapped_column(JSON, default=dict)
    verification_stats: Mapped[dict] = mapped_column(JSON, default=dict)
    data_status: Mapped[str] = mapped_column(String(32), default="insufficient_data")
    skipped_rows: Mapped[int] = mapped_column(Integer, default=0)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=db_now)

    def update_learning_data(self, metrics: dict, now: datetime) -> None:
        self.escalation_stats = metrics.get("escalation_stats", {})
        self.alarm_patterns = metrics.get("alarm_patterns", {})
        self.task_completion_rates = metrics.get("task_completion_rates", {})
        self.verification_stats = metrics.get("verification_stats", {})
        self.data_status = metrics.get("data_status", "ok")
        self.skipped_rows = metrics.get("skipped_rows", 0)
        self.updated_at = now

    def record_escalation_outcome(self, method: str, success: bool, response_time: float | None):
        """Incremental update so the next decision sees the newest outcome immediately."""
        stats = dict(self.escalation_stats or {})
        entry = dict(stats.get(method, {"attempts": 0, "successes": 0, "avg_response_time": None}))
        entry["attempts"] = entry.get("attempts", 0) + 1
        if success:
            entry["successes"] = entry.get("successes", 0) + 1
            if response_time is not None:
                prev = entry.get("avg_response_time")
                n = entry["successes"]
                entry["avg_response_time"] = (
                    response_time if prev is None else round(prev + (response_time - prev) / n, 1)
                )
        entry["success_rate"] = round(entry["successes"] / entry["attempts"], 3)
        stats[method] = entry
        self.escalation_stats = stats

    def get_optimal_settings(self) -> dict:
        stats = self.escalation_stats or {}
        ranked = sorted(stats.items(), key=lambda kv: kv[1].get("success_rate", 0), reverse=True)
        return {
            "best_escalation_method": ranked[0][0] if ranked else None,
            "fastest_task_type": (self.verification_stats or {}).get("fastest_task_type"),
            "avg_wake_time": (self.alarm_patterns or {}).get("avg_wake_time"),
        }


class Recommendation(TimestampMixin, Base):
    __tablename__ = "recommendations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    type: Mapped[str] = mapped_column(String(48))
    title: Mapped[str] = mapped_column(String(200))
    rationale: Mapped[str] = mapped_column(Text, default="")
    change: Mapped[dict] = mapped_column(JSON, default=dict)
    source: Mapped[str] = mapped_column(String(32), default="llm")
    # pending | accepted | dismissed
    status: Mapped[str] = mapped_column(String(16), default="pending")
