"""RecommendationService (F13): AI suggestions validated against a settings schema."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any, Callable

from riseup.ai import LLMError, ParseError, PromptBuilder, ResponseParser
from riseup.errors import Conflict, ValidationFailed
from riseup.models import Recommendation
from riseup.models.alarm import TASK_TYPES
from riseup.models.reminder import CATEGORIES
from riseup.models.user import ESCALATION_CHANNELS
from riseup.timeutil import is_valid_hhmm

if TYPE_CHECKING:
    from riseup.container import AppServices

MAX_SUGGESTIONS = 5
SUPPRESS_AFTER_DISMISSALS = 2
ESCALATION_ONLY = [c for c in ESCALATION_CHANNELS if c != "emergency_contact"]


def _check_order(value: Any) -> bool:
    return (
        isinstance(value, list) and len(value) > 0 and all(v in ESCALATION_CHANNELS for v in value)
    )


def _check_channels(value: Any) -> bool:
    # unsafe: disabling every escalation channel is never allowed
    return (
        isinstance(value, list)
        and all(v in ESCALATION_CHANNELS for v in value)
        and any(v in ESCALATION_ONLY for v in value)
    )


def _check_category_times(value: Any) -> bool:
    return (
        isinstance(value, dict)
        and len(value) > 0
        and all(k in CATEGORIES and is_valid_hhmm(v) for k, v in value.items())
    )


SETTINGS_SCHEMA: dict[str, Callable[[Any], bool]] = {
    "escalation_order": _check_order,
    "enabled_channels": _check_channels,
    "checkin_delay_minutes": lambda v: isinstance(v, int) and 5 <= v <= 10,
    "default_task_type": lambda v: v in TASK_TYPES,
    "category_preferred_times": _check_category_times,
}


def rule_based_recommendations(ctx: dict[str, Any]) -> list[dict[str, Any]]:
    """Deterministic tips from statistics (also used when the LLM fails)."""
    items: list[dict[str, Any]] = []
    stats: dict = ctx.get("escalation_stats", {})
    current: dict = ctx.get("current_settings", {})
    order: list = current.get("escalation_order", [])
    rated = [
        (m, s) for m, s in stats.items() if s.get("attempts", 0) >= 2 and m != "emergency_contact"
    ]
    if rated:
        best, best_stats = max(rated, key=lambda kv: kv[1].get("success_rate", 0))
        first = next((m for m in order if m in current.get("enabled_channels", [])), None)
        if best != first and best_stats.get("success_rate", 0) >= 0.6:
            first_rate = stats.get(first, {}).get("success_rate") if first else None
            comparison = f", {first} {first_rate:.0%}" if first_rate is not None else ""
            new_order = [best] + [m for m in order if m != best]
            items.append(
                {
                    "type": "escalation_order",
                    "title": f"Make {best} your first escalation",
                    "rationale": f"{best} woke you {best_stats['success_rate']:.0%} "
                    f"of the time{comparison}.",
                    "change": {"setting": "escalation_order", "value": new_order},
                }
            )
        if best not in current.get("enabled_channels", []):
            items.append(
                {
                    "type": "enable_channel",
                    "title": f"Re-enable {best} escalation",
                    "rationale": f"{best} has your best success rate "
                    f"({best_stats['success_rate']:.0%}).",
                    "change": {
                        "setting": "enabled_channels",
                        "value": current.get("enabled_channels", []) + [best],
                    },
                }
            )

    verification: dict = ctx.get("verification_stats", {})
    fastest = verification.get("fastest_task_type")
    if fastest and fastest != current.get("default_task_type"):
        avg = verification.get("by_type", {}).get(fastest, {}).get("avg_seconds")
        items.append(
            {
                "type": "default_task_type",
                "title": f"Use {fastest.replace('_', ' ')} challenges by default",
                "rationale": f"You finish {fastest} challenges fastest"
                + (f" (avg {avg:.0f}s)." if avg else "."),
                "change": {"setting": "default_task_type", "value": fastest},
            }
        )

    rates: dict = ctx.get("task_completion_rates", {})
    by_hour: dict = ctx.get("completion_by_hour", {})
    best_hour = None
    if by_hour:
        hour, entry = max(
            by_hour.items(), key=lambda kv: (kv[1].get("rate", 0), kv[1].get("total", 0))
        )
        if entry.get("rate", 0) >= 0.6:
            best_hour = f"{int(hour):02d}:00"
    for category, entry in rates.items():
        if entry.get("total", 0) >= 3 and entry.get("rate", 1) < 0.5 and best_hour:
            items.append(
                {
                    "type": f"category_time_{category}",
                    "title": f"Move {category} reminders to {best_hour}",
                    "rationale": f"You complete only {entry['rate']:.0%} of {category} tasks; "
                    f"{best_hour} is when you complete the most tasks.",
                    "change": {
                        "setting": "category_preferred_times",
                        "value": {category: best_hour},
                    },
                }
            )

    patterns: dict = ctx.get("alarm_patterns", {})
    missed_rate = patterns.get("checkin_miss_rate")
    if (
        missed_rate is not None
        and missed_rate >= 0.3
        and current.get("checkin_delay_minutes", 7) > 5
    ):
        items.append(
            {
                "type": "checkin_delay",
                "title": "Check in sooner after you verify",
                "rationale": f"You missed {missed_rate:.0%} of check-ins; "
                "a 5-minute check-in catches you before you fall back asleep.",
                "change": {"setting": "checkin_delay_minutes", "value": 5},
            }
        )
    return items[:MAX_SUGGESTIONS]


class RecommendationService:
    def __init__(self, services: "AppServices") -> None:
        self.s = services
        self.prompts = PromptBuilder()
        self.parser = ResponseParser()

    def current_settings(self, prefs) -> dict[str, Any]:
        return {
            "escalation_order": prefs.escalation_order,
            "enabled_channels": prefs.enabled_channels,
            "checkin_delay_minutes": prefs.checkin_delay_minutes,
            "default_task_type": prefs.default_task_type,
            "category_preferred_times": prefs.category_preferred_times or {},
        }

    def validate_change(self, change: dict[str, Any], current: dict[str, Any]) -> bool:
        setting: str = change.get("setting") or ""
        value = change.get("value")
        check = SETTINGS_SCHEMA.get(setting)
        if check is None or not check(value):
            return False
        if setting == "category_preferred_times" and isinstance(value, dict):
            existing = current.get(setting) or {}
            return any(existing.get(k) != v for k, v in value.items())
        return current.get(setting) != value

    def generate(self, user_id: int) -> dict[str, Any]:
        profile = self.s.learning.analyze_behavior(user_id)
        prefs = self.s.repos.prefs.for_user(user_id)
        if profile.data_status == "insufficient_data":
            self.s.repos.recommendations.clear_pending(user_id)
            return {
                "items": [],
                "source": "none",
                "message": "Not enough history yet. Use RiseUp for a few days and check back.",
            }
        current = self.current_settings(prefs)
        ctx = {
            "escalation_stats": profile.escalation_stats,
            "alarm_patterns": profile.alarm_patterns,
            "task_completion_rates": profile.task_completion_rates,
            "completion_by_hour": (profile.alarm_patterns or {}).get("completion_by_hour", {}),
            "verification_stats": profile.verification_stats,
            "current_settings": current,
            "allowed_settings": {
                "escalation_order": f"list ordered from {ESCALATION_CHANNELS}",
                "enabled_channels": f"non-empty subset of {ESCALATION_CHANNELS}",
                "checkin_delay_minutes": "integer 5-10",
                "default_task_type": f"one of {TASK_TYPES}",
                "category_preferred_times": f"object mapping {CATEGORIES} to HH:MM",
            },
        }
        source = "llm"
        message = ""
        try:
            response = self.s.llm_client.send_request(
                self.prompts.build_insight_prompt(ctx), "recommend", self.prompts.system
            )
            raw = [dto.model_dump() for dto in self.parser.parse_recommendations(response.text)]
            source = response.decided_by
        except (LLMError, ParseError) as exc:
            raw = rule_based_recommendations(ctx)
            source = "rule"
            message = f"AI unavailable ({exc}); showing rule-based tips."

        suppressed = set(prefs.suppressed_recommendation_types or [])
        accepted: list[dict[str, Any]] = []
        for item in raw:
            if item["type"] in suppressed or not self.validate_change(item["change"], current):
                continue  # invalid, unsafe, redundant, or repeatedly dismissed
            if any(a["type"] == item["type"] for a in accepted):
                continue
            accepted.append(item)
        self.s.repos.recommendations.clear_pending(user_id)
        saved = []
        for item in accepted[:MAX_SUGGESTIONS]:
            rec = Recommendation(
                user_id=user_id,
                type=item["type"],
                title=item["title"],
                rationale=item.get("rationale", ""),
                change=item["change"],
                source=source,
            )
            self.s.repos.recommendations.save(rec)
            saved.append(rec)
        if not saved and not message:
            message = "No changes to suggest: your current settings already match your data."
        return {"items": saved, "source": source, "message": message}

    def list_pending(self, user_id: int) -> list[Recommendation]:
        return self.s.repos.recommendations.for_user(user_id, "pending")

    def accept(self, user_id: int, rec_id: int) -> Recommendation:
        rec = self.s.repos.recommendations.get_or_404(rec_id, user_id)
        if rec.status != "pending":
            raise Conflict(f"Recommendation already {rec.status}")
        prefs = self.s.repos.prefs.for_user(user_id)
        if not self.validate_change(rec.change, self.current_settings(prefs)):
            raise ValidationFailed("This suggestion no longer applies to your settings")
        self.s.preferences.update(user_id, {rec.change["setting"]: rec.change["value"]})
        rec.status = "accepted"
        return rec

    def dismiss(self, user_id: int, rec_id: int) -> Recommendation:
        rec = self.s.repos.recommendations.get_or_404(rec_id, user_id)
        if rec.status != "pending":
            raise Conflict(f"Recommendation already {rec.status}")
        rec.status = "dismissed"
        prefs = self.s.repos.prefs.for_user(user_id)
        counts = dict(prefs.dismissed_recommendation_counts or {})
        counts[rec.type] = counts.get(rec.type, 0) + 1
        prefs.dismissed_recommendation_counts = counts
        if counts[rec.type] >= SUPPRESS_AFTER_DISMISSALS:
            prefs.suppressed_recommendation_types = sorted(
                set(prefs.suppressed_recommendation_types or []) | {rec.type}
            )
        return rec
