"""AnalyticsEngine (F11/F12): pure metric computation plus cached dashboard queries."""

from __future__ import annotations

from collections import defaultdict
from datetime import timedelta
from statistics import mean
from typing import TYPE_CHECKING, Any, Iterable

from riseup.timeutil import to_local

if TYPE_CHECKING:
    from riseup.container import AppServices
    from riseup.models import AlarmLog, EscalationLog, ReminderLog

MIN_EVENTS_FOR_PROFILE = 3
OUTCOME_STATUSES = {"confirmed", "no_response", "failed"}
CHECKIN_EVENTS = {"checkin_confirmed", "checkin_late", "checkin_missed"}


def _rate(success: int, total: int) -> float | None:
    return round(success / total, 3) if total else None


def _hhmm(minutes: float) -> str:
    minutes = int(round(minutes)) % (24 * 60)
    return f"{minutes // 60:02d}:{minutes % 60:02d}"


class AnalyticsEngine:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    @staticmethod
    def compute_metrics(
        escalations: Iterable["EscalationLog"],
        alarm_logs: Iterable["AlarmLog"],
        reminder_logs: Iterable["ReminderLog"],
        tz_name: str | None = None,
    ) -> dict[str, Any]:
        skipped = 0
        events = 0

        # escalation effectiveness per method
        esc: dict[str, dict[str, Any]] = defaultdict(
            lambda: {"attempts": 0, "successes": 0, "times": []}
        )
        for entry in escalations:
            if entry.status in ("sent", "pending", "skipped"):
                continue
            if not isinstance(entry.method, str) or entry.status not in OUTCOME_STATUSES:
                skipped += 1
                continue
            bucket = esc[entry.method]
            bucket["attempts"] += 1
            events += 1
            if entry.status == "confirmed":
                bucket["successes"] += 1
                if entry.response_time_seconds is not None:
                    bucket["times"].append(entry.response_time_seconds)
        escalation_stats = {
            method: {
                "attempts": b["attempts"],
                "successes": b["successes"],
                "success_rate": _rate(b["successes"], b["attempts"]),
                "avg_response_time": round(mean(b["times"]), 1) if b["times"] else None,
            }
            for method, b in esc.items()
        }

        # alarm patterns and verification stats
        wake_minutes: list[float] = []
        hour_outcomes: dict[str, list[int]] = defaultdict(lambda: [0, 0])
        by_type: dict[str, list[float]] = defaultdict(list)
        triggered = verified = missed = snoozes = 0
        checkins: dict[str, int] = defaultdict(int)
        for row in alarm_logs:
            try:
                local = to_local(row.created_at, tz_name)
            except Exception:
                skipped += 1
                continue
            details = row.details or {}
            if row.event == "triggered" and not details.get("snoozed"):
                triggered += 1
            elif row.event == "verified":
                verified += 1
                events += 1
                wake_minutes.append(local.hour * 60 + local.minute)
                hour_outcomes[f"{local.hour:02d}"][0] += 1
                hour_outcomes[f"{local.hour:02d}"][1] += 1
                seconds = details.get("seconds_to_verify")
                if row.task_type and isinstance(seconds, (int, float)):
                    by_type[row.task_type].append(float(seconds))
            elif row.event == "missed":
                missed += 1
                events += 1
                hour_outcomes[f"{local.hour:02d}"][1] += 1
            elif row.event == "snoozed":
                snoozes += 1
            elif row.event in CHECKIN_EVENTS:
                checkins[row.event] += 1
        checkin_total = sum(checkins.values())
        verification_by_type = {
            t: {"count": len(v), "avg_seconds": round(mean(v), 1)} for t, v in by_type.items()
        }
        fastest = (
            min(verification_by_type.items(), key=lambda kv: kv[1]["avg_seconds"])[0]
            if verification_by_type
            else None
        )

        # task completion by category / hour
        categories: dict[str, list[int]] = defaultdict(lambda: [0, 0])  # completed, missed
        hours: dict[str, list[int]] = defaultdict(lambda: [0, 0])
        for rem in reminder_logs:
            if rem.event not in ("completed", "missed"):
                continue
            if not rem.category:
                skipped += 1
                continue
            events += 1
            local = to_local(rem.created_at, tz_name)
            idx = 0 if rem.event == "completed" else 1
            categories[rem.category][idx] += 1
            hours[f"{local.hour:02d}"][idx] += 1
        task_rates = {
            c: {"completed": v[0], "missed": v[1], "total": sum(v), "rate": _rate(v[0], sum(v))}
            for c, v in categories.items()
        }
        completion_by_hour = {
            h: {"completed": v[0], "total": sum(v), "rate": _rate(v[0], sum(v))}
            for h, v in sorted(hours.items())
        }

        return {
            "escalation_stats": escalation_stats,
            "alarm_patterns": {
                "avg_wake_time": _hhmm(mean(wake_minutes)) if wake_minutes else None,
                "success_rate_by_hour": {
                    h: _rate(s, t) for h, (s, t) in sorted(hour_outcomes.items())
                },
                "triggered": triggered,
                "verified": verified,
                "missed": missed,
                "verification_success_rate": _rate(verified, verified + missed),
                "avg_snoozes_per_alarm": round(snoozes / triggered, 2) if triggered else None,
                "checkins": dict(checkins),
                "checkin_miss_rate": _rate(checkins.get("checkin_missed", 0), checkin_total),
                "completion_by_hour": completion_by_hour,
            },
            "task_completion_rates": task_rates,
            "verification_stats": {"by_type": verification_by_type, "fastest_task_type": fastest},
            "data_status": "ok" if events >= MIN_EVENTS_FOR_PROFILE else "insufficient_data",
            "skipped_rows": skipped,
            "event_count": events,
        }

    def query(self, user_id: int, days: int) -> dict[str, Any]:
        key = f"stats:{user_id}:{days}"
        cached = self.s.cache.get(key)
        if cached is not None:
            return {**cached, "cached": True}
        user = self.s.user(user_id)
        since = self.s.now() - timedelta(days=days)
        escalations = self.s.repos.escalations.since(user_id, since)
        alarm_logs = self.s.repos.logs.alarm_logs(user_id, since)
        reminder_logs = self.s.repos.logs.reminder_logs(user_id, since)
        metrics = self.compute_metrics(escalations, alarm_logs, reminder_logs, user.timezone)

        wake_times = []
        for entry in alarm_logs:
            if entry.event == "verified":
                local = to_local(entry.created_at, user.timezone)
                wake_times.append(
                    {
                        "date": local.date().isoformat(),
                        "time": local.strftime("%H:%M"),
                        "minutes": local.hour * 60 + local.minute,
                        "task_type": entry.task_type,
                    }
                )
        daily_tasks: dict[str, list[int]] = defaultdict(lambda: [0, 0])
        for entry in reminder_logs:
            if entry.event in ("completed", "missed"):
                day = to_local(entry.created_at, user.timezone).date().isoformat()
                daily_tasks[day][0 if entry.event == "completed" else 1] += 1
        patterns = metrics["alarm_patterns"]
        result = {
            "range_days": days,
            "empty": metrics["event_count"] == 0 and patterns["triggered"] == 0,
            "wake_times": wake_times,
            "avg_wake_time": patterns["avg_wake_time"],
            "verification": {
                "triggered": patterns["triggered"],
                "verified": patterns["verified"],
                "missed": patterns["missed"],
                "success_rate": patterns["verification_success_rate"],
                "by_type": metrics["verification_stats"]["by_type"],
                "fastest_task_type": metrics["verification_stats"]["fastest_task_type"],
            },
            "escalation": metrics["escalation_stats"],
            "tasks": metrics["task_completion_rates"],
            "tasks_daily": [
                {"date": d, "completed": v[0], "missed": v[1]}
                for d, v in sorted(daily_tasks.items())
            ],
            "checkins": patterns["checkins"],
            "cached": False,
        }
        self.s.cache.set(key, result, ttl=60)
        return result


class AnalyticsController:
    """F12 entry point used by the API/CLI; delegates aggregation to AnalyticsEngine."""

    def __init__(self, services: "AppServices") -> None:
        self.s = services
        self.engine = AnalyticsEngine(services)

    def get_stats(self, user_id: int, days: int) -> dict[str, Any]:
        return self.engine.query(user_id, days)
