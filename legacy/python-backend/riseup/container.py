"""Composition root: long-lived singletons (`Container`) and per-transaction services."""

from __future__ import annotations

import logging
from contextlib import contextmanager
from functools import cached_property
from types import SimpleNamespace
from typing import Iterator

from sqlalchemy.orm import Session, sessionmaker

from riseup.alarm_system.verification import VerificationTaskFactory
from riseup.cache import Cache, InMemoryCache
from riseup.clock import Clock, SystemClock, set_db_clock
from riseup.config import Settings
from riseup.events import EventBus, EventEmitter
from riseup.scheduling import JobScheduler
from riseup.tools import ToolManager

log = logging.getLogger(__name__)


class Container:
    def __init__(
        self,
        settings: Settings,
        session_factory: sessionmaker,
        scheduler: JobScheduler,
        tool_manager: ToolManager,
        llm_client,
        clock: Clock | None = None,
        cache: Cache | None = None,
        task_factory: VerificationTaskFactory | None = None,
    ) -> None:
        from riseup.jobs import JobRunner
        from riseup.realtime import WebSocketHub

        self.settings = settings
        self.session_factory = session_factory
        self.scheduler = scheduler
        self.tool_manager = tool_manager
        self.llm_client = llm_client
        self.clock = clock or SystemClock()
        set_db_clock(self.clock)
        self.cache = cache or InMemoryCache()
        self.task_factory = task_factory or VerificationTaskFactory(settings.camera_available)
        self.bus = EventBus()
        self.hub = WebSocketHub()
        self.jobs = JobRunner(self)

    def register_default_observers(self) -> None:
        from riseup.events.observers import (
            DashboardNotifier,
            EscalationTrigger,
            ReminderLogger,
            StatsInvalidator,
        )

        self.bus.subscribe(ReminderLogger(self))
        self.bus.subscribe(StatsInvalidator(self))
        self.bus.subscribe(DashboardNotifier(self))
        self.bus.subscribe(EscalationTrigger(self))

    @contextmanager
    def unit_of_work(self) -> Iterator["AppServices"]:
        db = self.session_factory()
        services = AppServices(self, db)
        try:
            yield services
            db.flush()
            events = services.emitter.drain()
            db.commit()
        except Exception:
            db.rollback()
            services.emitter.clear()
            raise
        finally:
            db.close()
        self.bus.publish_all(events)


class AppServices:
    """Builds controllers/services bound to one DB session (one transaction)."""

    def __init__(self, container: Container, db: Session) -> None:
        self.container = container
        self.db = db
        self.emitter = EventEmitter()
        self.settings = container.settings
        self.clock = container.clock
        self.scheduler = container.scheduler
        self.tool_manager = container.tool_manager
        self.llm_client = container.llm_client
        self.cache = container.cache
        self.jobs = container.jobs

    def now(self):
        return self.clock.now()

    # repositories -------------------------------------------------------
    @cached_property
    def repos(self):
        from riseup import repositories as r

        return SimpleNamespace(
            users=r.UserRepository(self.db),
            prefs=r.PreferencesRepository(self.db),
            alarms=r.AlarmRepository(self.db),
            checkins=r.CheckInRepository(self.db),
            reminders=r.ReminderRepository(self.db),
            escalations=r.EscalationLogRepository(self.db),
            logs=r.LogRepository(self.db),
            profiles=r.ProfileRepository(self.db),
            recommendations=r.RecommendationRepository(self.db),
            notifications=r.NotificationRepository(self.db),
        )

    def user(self, user_id: int):
        return self.repos.users.get_or_404(user_id)

    # alarm system -------------------------------------------------------
    @cached_property
    def alarm_scheduler(self):
        from riseup.alarm_system.scheduler import AlarmScheduler

        return AlarmScheduler(self)

    @cached_property
    def alarm_controller(self):
        from riseup.alarm_system.controller import AlarmController

        return AlarmController(self)

    @cached_property
    def checkins(self):
        from riseup.alarm_system.checkin import CheckInService

        return CheckInService(self)

    # reminders ----------------------------------------------------------
    @cached_property
    def reminder_factory(self):
        from riseup.task_manager.factory import ReminderFactory

        return ReminderFactory(self)

    @cached_property
    def reminder_controller(self):
        from riseup.task_manager.controller import ReminderController

        return ReminderController(self)

    @cached_property
    def reminder_scheduler(self):
        from riseup.task_manager.scheduler import ReminderScheduler

        return ReminderScheduler(self)

    @cached_property
    def prioritizer(self):
        from riseup.task_manager.prioritizer import TaskPrioritizer

        return TaskPrioritizer(self.llm_client)

    @cached_property
    def nl_parser(self):
        from riseup.task_manager.nl_parser import NLCommandParser

        return NLCommandParser(self.llm_client)

    # agent / AI ---------------------------------------------------------
    @cached_property
    def escalation_engine(self):
        from riseup.agent.escalation import EscalationEngine

        return EscalationEngine(self)

    @cached_property
    def agent(self):
        from riseup.agent.controller import AgentController

        return AgentController(self)

    @cached_property
    def recommendations(self):
        from riseup.user_profile.recommendations import RecommendationService

        return RecommendationService(self)

    # profile / analytics ------------------------------------------------
    @cached_property
    def preferences(self):
        from riseup.user_profile.preferences import PreferencesController

        return PreferencesController(self)

    @cached_property
    def analytics_engine(self):
        from riseup.user_profile.analytics import AnalyticsEngine

        return AnalyticsEngine(self)

    @cached_property
    def analytics(self):
        from riseup.user_profile.analytics import AnalyticsController

        return AnalyticsController(self)

    @cached_property
    def learning(self):
        from riseup.user_profile.learning import LearningEngine

        return LearningEngine(self)

    @cached_property
    def history(self):
        from riseup.history.controller import HistoryController

        return HistoryController(self)
