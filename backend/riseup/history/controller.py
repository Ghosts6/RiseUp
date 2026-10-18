"""HistoryController: unified event history and "Why?" explanations for AI decisions."""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import TYPE_CHECKING, Any

from riseup.errors import NotFound, ValidationFailed

if TYPE_CHECKING:
    from riseup.container import AppServices

HISTORY_TYPES = ("alarm", "escalation", "reminder", "checkin", "prioritization")
ALARM_EVENT_TEXT = {
    "triggered": "Alarm rang",
    "verified": "Challenge completed",
    "failed_attempt": "Wrong challenge answer",
    "snoozed": "Snoozed",
    "timeout": "No response to alarm",
    "missed": "Alarm went unanswered",
    "checkin_confirmed": "Check-in confirmed",
    "checkin_late": "Check-in confirmed late",
    "checkin_missed": "Check-in missed",
    "checkin_skipped": "Check-in skipped (disabled)",
}


class HistoryController:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    def list(
        self,
        user_id: int,
        kind: str | None = None,
        since_days: int | None = None,
        page: int = 1,
        page_size: int = 25,
    ) -> dict[str, Any]:
        if kind and kind not in HISTORY_TYPES:
            raise ValidationFailed(f"type must be one of {', '.join(HISTORY_TYPES)}")
        page = max(page, 1)
        page_size = min(max(page_size, 1), 100)
        since: datetime | None = self.s.now() - timedelta(days=since_days) if since_days else None
        items: list[dict[str, Any]] = []
        repos = self.s.repos

        if kind in (None, "alarm", "checkin"):
            alarms = {a.id: a for a in repos.alarms.for_user(user_id)}
            for entry in repos.logs.alarm_logs(user_id, since):
                is_checkin = entry.event.startswith("checkin")
                if (kind == "checkin" and not is_checkin) or (kind == "alarm" and is_checkin):
                    continue
                alarm = alarms.get(entry.alarm_id or -1)
                items.append(
                    {
                        "kind": "checkin" if is_checkin else "alarm",
                        "ref_id": entry.id,
                        "at": entry.created_at,
                        "title": ALARM_EVENT_TEXT.get(entry.event, entry.event),
                        "summary": f"{alarm.label} ({alarm.time})" if alarm else "Deleted alarm",
                        "status": entry.event,
                        "explainable": False,
                    }
                )
        if kind in (None, "escalation"):
            for entry in repos.escalations.since(user_id, since):
                items.append(
                    {
                        "kind": "escalation",
                        "ref_id": entry.id,
                        "at": entry.created_at,
                        "title": f"Escalation #{entry.attempt}: {entry.method or 'none'}",
                        "summary": (
                            "Rule-based fallback" if entry.fallback_used else entry.decided_by
                        )
                        + f" - {entry.status}",
                        "status": entry.status,
                        "explainable": True,
                    }
                )
        if kind in (None, "reminder", "prioritization"):
            for entry in repos.logs.reminder_logs(user_id, since):
                is_rank = entry.event == "prioritized"
                if (kind == "prioritization" and not is_rank) or (kind == "reminder" and is_rank):
                    continue
                details = entry.details or {}
                items.append(
                    {
                        "kind": "prioritization" if is_rank else "reminder",
                        "ref_id": entry.id,
                        "at": entry.created_at,
                        "title": (
                            ("Reminder order: " + " > ".join(details.get("titles", [])))
                            if is_rank
                            else f"{details.get('title') or 'Reminder'}: "
                            f"{entry.event.replace('_', ' ')}"
                        ),
                        "summary": (
                            (
                                "Rule-based fallback"
                                if details.get("fallback_used")
                                else details.get("decided_by", "")
                            )
                            if is_rank
                            else (f"level {entry.level}" if entry.level else entry.category or "")
                        ),
                        "status": entry.event,
                        "explainable": is_rank,
                    }
                )

        items.sort(key=lambda i: (i["at"], i["ref_id"]), reverse=True)
        total = len(items)
        start = (page - 1) * page_size
        return {
            "items": items[start : start + page_size],
            "total": total,
            "page": page,
            "page_size": page_size,
            "pages": max((total + page_size - 1) // page_size, 1),
        }

    def explain(self, user_id: int, kind: str, ref_id: int) -> dict[str, Any]:
        if kind == "escalation":
            entry = self.s.repos.escalations.get_or_404(ref_id, user_id)
            alarm = self.s.repos.alarms.get(entry.alarm_id) if entry.alarm_id else None
            return {
                "kind": "escalation",
                "id": entry.id,
                "at": entry.created_at,
                "alarm": (
                    {"id": alarm.id, "label": alarm.label, "time": alarm.time} if alarm else None
                ),
                "trigger": entry.trigger,
                "attempt": entry.attempt,
                "method": entry.method,
                "confidence": entry.confidence,
                "reasoning": entry.reasoning,
                "decided_by": entry.decided_by,
                "fallback_used": entry.fallback_used,
                "fallback_method": entry.fallback_method,
                "label": (
                    "Rule-based fallback (LLM unavailable or invalid)"
                    if entry.fallback_used
                    else f"AI decision ({entry.decided_by})"
                ),
                "inputs": entry.inputs,
                "outcome": entry.status,
                "error": entry.error,
                "response_time_seconds": entry.response_time_seconds,
            }
        if kind == "prioritization":
            entry = self.s.repos.logs.reminder_log(ref_id)
            if entry is None or entry.user_id != user_id or entry.event != "prioritized":
                raise NotFound(f"Prioritization {ref_id} not found")
            d = entry.details or {}
            return {
                "kind": "prioritization",
                "id": entry.id,
                "at": entry.created_at,
                "order": d.get("order", []),
                "titles": d.get("titles", []),
                "reasoning": d.get("reasoning", ""),
                "decided_by": d.get("decided_by"),
                "fallback_used": d.get("fallback_used", False),
                "label": (
                    "Rule-based fallback (LLM unavailable or invalid)"
                    if d.get("fallback_used")
                    else f"AI decision ({d.get('decided_by')})"
                ),
                "inputs": d.get("inputs", {}),
            }
        raise ValidationFailed("Only escalation and prioritization decisions can be explained")
