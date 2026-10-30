"""Observer pattern: status events emitted by Alarm / Reminder / CheckIn subjects."""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Callable, Iterable

from riseup.clock import db_now

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class StatusEvent:
    subject_type: str  # alarm | reminder | checkin | escalation
    subject_id: int
    user_id: int
    status: str
    previous: str | None = None
    payload: dict = field(default_factory=dict)
    at: datetime = field(default_factory=db_now)


class ReminderObserver(ABC):
    """Observer contract (`on_status_changed`)."""

    subject_types: tuple[str, ...] | None = None  # None = all

    def wants(self, event: StatusEvent) -> bool:
        return self.subject_types is None or event.subject_type in self.subject_types

    @abstractmethod
    def on_status_changed(self, event: StatusEvent) -> None: ...


class EventBus:
    """Holds observer subscriptions and fans out published events."""

    def __init__(self) -> None:
        self._observers: list[ReminderObserver] = []

    def subscribe(self, observer: ReminderObserver) -> None:
        self._observers.append(observer)

    def unsubscribe(self, observer: ReminderObserver) -> None:
        self._observers.remove(observer)

    def publish(self, event: StatusEvent) -> None:
        for observer in list(self._observers):
            if not observer.wants(event):
                continue
            try:
                observer.on_status_changed(event)
            except Exception:  # an observer must never break the subject
                log.exception("Observer %s failed on %s", type(observer).__name__, event)

    def publish_all(self, events: Iterable[StatusEvent]) -> None:
        for event in events:
            self.publish(event)


class EventEmitter:
    """Collects events during a unit of work; flushed to the bus after commit."""

    def __init__(self) -> None:
        self._pending: list[tuple[Any, Callable[[Any], StatusEvent]]] = []

    def emit(
        self,
        subject_type: str,
        subject: Any,
        status: str,
        previous: str | None = None,
        payload: dict | None = None,
    ) -> None:
        data = dict(payload or {})
        for attr in ("title", "label", "method"):
            value = getattr(subject, attr, None)
            if isinstance(value, str):
                data.setdefault(attr, value)

        def build(obj: Any) -> StatusEvent:
            return StatusEvent(
                subject_type=subject_type,
                subject_id=obj.id,
                user_id=obj.user_id,
                status=status,
                previous=previous,
                payload=data,
            )

        self._pending.append((subject, build))

    def drain(self) -> list[StatusEvent]:
        # ids are only known after flush, so events are built lazily here
        events = [build(subject) for subject, build in self._pending]
        self._pending.clear()
        return events

    def clear(self) -> None:
        self._pending.clear()
