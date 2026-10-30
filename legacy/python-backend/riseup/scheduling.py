"""Job scheduling abstraction over APScheduler (and a manual one for tests)."""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable

log = logging.getLogger(__name__)


class JobScheduler(ABC):
    @abstractmethod
    def schedule(self, job_id: str, run_at: datetime, func: Callable, **kwargs: Any) -> None:
        """Run `func(**kwargs)` at naive-UTC `run_at`; replaces a job with the same id."""

    @abstractmethod
    def cancel(self, job_id: str) -> None: ...

    @abstractmethod
    def has_job(self, job_id: str) -> bool: ...

    @abstractmethod
    def add_daily(self, job_id: str, hour: int, minute: int, func: Callable) -> None: ...

    def start(self) -> None:
        pass

    def shutdown(self) -> None:
        pass


class APSJobScheduler(JobScheduler):
    def __init__(self) -> None:
        from apscheduler.schedulers.background import BackgroundScheduler

        self._scheduler = BackgroundScheduler(
            timezone=timezone.utc, job_defaults={"misfire_grace_time": 300, "coalesce": True}
        )

    def schedule(self, job_id: str, run_at: datetime, func: Callable, **kwargs: Any) -> None:
        self._scheduler.add_job(
            func,
            trigger="date",
            run_date=run_at.replace(tzinfo=timezone.utc),
            id=job_id,
            kwargs=kwargs,
            replace_existing=True,
        )

    def cancel(self, job_id: str) -> None:
        if self._scheduler.get_job(job_id):
            self._scheduler.remove_job(job_id)

    def has_job(self, job_id: str) -> bool:
        return self._scheduler.get_job(job_id) is not None

    def add_daily(self, job_id: str, hour: int, minute: int, func: Callable) -> None:
        self._scheduler.add_job(
            func, trigger="cron", hour=hour, minute=minute, id=job_id, replace_existing=True
        )

    def start(self) -> None:
        if not self._scheduler.running:
            self._scheduler.start()

    def shutdown(self) -> None:
        if self._scheduler.running:
            self._scheduler.shutdown(wait=False)


@dataclass
class ManualJob:
    job_id: str
    run_at: datetime
    func: Callable
    kwargs: dict = field(default_factory=dict)


class ManualJobScheduler(JobScheduler):
    """Deterministic scheduler: tests advance a clock and call `run_due(now)`."""

    def __init__(self) -> None:
        self.jobs: dict[str, ManualJob] = {}
        self.daily: dict[str, Callable] = {}

    def schedule(self, job_id: str, run_at: datetime, func: Callable, **kwargs: Any) -> None:
        self.jobs[job_id] = ManualJob(job_id, run_at, func, kwargs)

    def cancel(self, job_id: str) -> None:
        self.jobs.pop(job_id, None)

    def has_job(self, job_id: str) -> bool:
        return job_id in self.jobs

    def add_daily(self, job_id: str, hour: int, minute: int, func: Callable) -> None:
        self.daily[job_id] = func

    def run_job(self, job_id: str) -> None:
        job = self.jobs.pop(job_id)
        job.func(**job.kwargs)

    def run_due(self, now: datetime, max_rounds: int = 50) -> int:
        ran = 0
        for _ in range(max_rounds):
            due = sorted((j for j in self.jobs.values() if j.run_at <= now), key=lambda j: j.run_at)
            if not due:
                break
            for job in due:
                if self.jobs.get(job.job_id) is job:
                    del self.jobs[job.job_id]
                    job.func(**job.kwargs)
                    ran += 1
        return ran
