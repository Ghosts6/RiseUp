"""Shared column helpers and the Observer subject mixin."""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING, Any

from sqlalchemy import DateTime
from sqlalchemy.orm import Mapped, mapped_column

from riseup.clock import db_now

if TYPE_CHECKING:
    from riseup.events import EventEmitter


class TimestampMixin:
    created_at: Mapped[datetime] = mapped_column(DateTime, default=db_now)


class StatusSubjectMixin:
    """Observer *subject*: changing status notifies subscribed observers.

    The emitter queues events and the unit of work publishes them after commit.
    """

    SUBJECT_TYPE = "subject"
    if TYPE_CHECKING:
        status: str
        id: int
        user_id: int

    def change_status(self, status: str, emitter: "EventEmitter", **payload: Any) -> None:
        previous = self.status
        self.status = status
        emitter.emit(
            subject_type=self.SUBJECT_TYPE,
            subject=self,
            status=status,
            previous=previous,
            payload=payload,
        )
