"""FastAPI application factory and server entry point."""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from riseup import __version__
from riseup.ai import build_llm_client
from riseup.api.routers import ROUTERS
from riseup.cache import make_cache
from riseup.clock import Clock
from riseup.config import Settings, get_settings
from riseup.container import Container
from riseup.db import create_all, make_engine, make_session_factory
from riseup.errors import DomainError
from riseup.scheduling import APSJobScheduler, JobScheduler
from riseup.tools import build_tool_manager

log = logging.getLogger("riseup")


def build_container(
    settings: Settings,
    scheduler: JobScheduler | None = None,
    clock: Clock | None = None,
    tool_manager=None,
    llm_client=None,
) -> Container:
    engine = make_engine(settings.database_url)
    if settings.auto_create_tables:
        create_all(engine)
    container = Container(
        settings=settings,
        session_factory=make_session_factory(engine),
        scheduler=scheduler or APSJobScheduler(),
        tool_manager=tool_manager or build_tool_manager(settings),
        llm_client=llm_client or build_llm_client(settings),
        clock=clock,
        cache=make_cache(settings.redis_url),
    )
    container.register_default_observers()
    with container.unit_of_work() as s:
        s.repos.users.ensure_demo_user(settings.demo_token)
    return container


def rehydrate_jobs(container: Container) -> None:
    """In-memory jobs are lost on restart: rebuild them from the database."""
    with container.unit_of_work() as s:
        for alarm in s.repos.alarms.active():
            if not alarm.is_running:
                s.alarm_scheduler.schedule_alarm(alarm)
        for reminder in s.repos.reminders.open_all():
            if reminder.status == "scheduled":
                s.reminder_scheduler.schedule_reminder(reminder)
            elif reminder.status == "notifying":
                container.scheduler.schedule(
                    f"reminder-miss:{reminder.id}",
                    s.now(),
                    container.jobs.reminder_missed,
                    reminder_id=reminder.id,
                )
        for checkin in s.repos.checkins.pending():
            if checkin.status == "scheduled":
                container.scheduler.schedule(
                    f"checkin-send:{checkin.id}",
                    max(checkin.scheduled_at, s.now()),
                    container.jobs.send_checkin,
                    checkin_id=checkin.id,
                )
    container.scheduler.add_daily(
        "nightly-learning",
        container.settings.learning_cron_hour,
        0,
        container.jobs.nightly_learning,
    )


def create_app(settings: Settings | None = None, container: Container | None = None) -> FastAPI:
    settings = settings or get_settings()
    container = container or build_container(settings)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        container.hub.set_loop(asyncio.get_running_loop())
        if settings.enable_scheduler:
            rehydrate_jobs(container)
            container.scheduler.start()
        yield
        container.scheduler.shutdown()

    app = FastAPI(
        title="RiseUp API",
        version=__version__,
        description="Intelligent alarm and task reminder system (Stage 2). "
        "Shared by the React GUI and the `riseup` CLI.",
        lifespan=lifespan,
    )
    app.state.container = container
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list or ["*"],
        allow_origin_regex=settings.cors_origin_regex or None,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.exception_handler(DomainError)
    async def domain_error_handler(_: Request, exc: DomainError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content={"detail": exc.message, "code": exc.code, **exc.extra},
        )

    for router in ROUTERS:
        app.include_router(router)
    return app


def run() -> None:
    import uvicorn

    settings = get_settings()
    logging.basicConfig(
        level=logging.DEBUG if settings.debug else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    uvicorn.run(
        "riseup.main:create_app", factory=True, host=settings.host, port=settings.port, reload=False
    )


if __name__ == "__main__":
    run()
