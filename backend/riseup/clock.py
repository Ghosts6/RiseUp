"""Time source abstraction. All datetimes inside the backend are naive UTC."""

from __future__ import annotations

from abc import ABC, abstractmethod
from datetime import datetime, timedelta, timezone


def utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


_db_clock: "Clock | None" = None


def set_db_clock(clock: "Clock | None") -> None:
    """Clock used for ORM timestamp defaults (so simulated time stays consistent)."""
    global _db_clock
    _db_clock = clock


def db_now() -> datetime:
    return _db_clock.now() if _db_clock else utcnow()


class Clock(ABC):
    @abstractmethod
    def now(self) -> datetime:
        """Current time as naive UTC."""


class SystemClock(Clock):
    def now(self) -> datetime:
        return utcnow()


class FixedClock(Clock):
    """Manually controlled clock for tests and simulations."""

    def __init__(self, start: datetime) -> None:
        self._now = start

    def now(self) -> datetime:
        return self._now

    def set(self, value: datetime) -> None:
        self._now = value

    def advance(self, **kwargs: float) -> datetime:
        self._now = self._now + timedelta(**kwargs)
        return self._now
