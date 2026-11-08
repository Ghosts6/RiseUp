"""LearningEngine (F11): nightly aggregation of logs into the UserProfile (agent memory)."""

from __future__ import annotations

import logging
from datetime import timedelta
from typing import TYPE_CHECKING

from riseup.user_profile.analytics import AnalyticsEngine

if TYPE_CHECKING:
    from riseup.container import AppServices
    from riseup.models import UserProfile

log = logging.getLogger(__name__)
LOOKBACK_DAYS = 90


class LearningEngine:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    def analyze_behavior(self, user_id: int) -> "UserProfile":
        profile = self.s.repos.profiles.for_user(user_id)
        try:
            user = self.s.user(user_id)
            since = self.s.now() - timedelta(days=LOOKBACK_DAYS)
            metrics = AnalyticsEngine.compute_metrics(
                self.s.repos.escalations.since(user_id, since),
                self.s.repos.logs.alarm_logs(user_id, since),
                self.s.repos.logs.reminder_logs(user_id, since),
                user.timezone,
            )
            profile.update_learning_data(metrics, self.s.now())
            self.s.cache.delete_prefix(f"stats:{user_id}:")
        except Exception:  # keep the previous profile if aggregation fails
            log.exception("Learning failed for user %s; previous profile kept", user_id)
        return profile
