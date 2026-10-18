"""ORM -> JSON-ready dicts. Datetimes are ISO-8601 UTC with a trailing 'Z'."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from riseup.models import (
    Alarm,
    CheckIn,
    EmergencyContact,
    EscalationLog,
    Notification,
    Recommendation,
    Reminder,
    User,
    UserPreferences,
    UserProfile,
)
from riseup.timeutil import to_local


def iso(dt: datetime | None) -> str | None:
    return f"{dt.isoformat()}Z" if dt else None


def local(dt: datetime | None, tz: str | None) -> str | None:
    return to_local(dt, tz).strftime("%Y-%m-%d %H:%M") if dt else None


def jsonify(value: Any) -> Any:
    if isinstance(value, datetime):
        return iso(value)
    if isinstance(value, dict):
        return {k: jsonify(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [jsonify(v) for v in value]
    return value


def user(u: User) -> dict[str, Any]:
    return {"id": u.id, "name": u.name, "email": u.email, "phone": u.phone, "timezone": u.timezone}


def alarm(a: Alarm, tz: str | None = None) -> dict[str, Any]:
    return {
        "id": a.id,
        "time": a.time,
        "label": a.label,
        "sound": a.sound,
        "vibration": a.vibration,
        "repeat_pattern": a.repeat_pattern,
        "snooze_enabled": a.snooze_enabled,
        "verification_task_type": a.verification_task_type,
        "custom_task_text": a.custom_task_text,
        "priority": a.priority,
        "is_active": a.is_active,
        "status": a.status,
        "next_trigger_at": iso(a.next_trigger_at),
        "next_trigger_local": local(a.next_trigger_at, tz),
        "triggered_at": iso(a.triggered_at),
        "snooze_count": a.snooze_count,
        "escalation_episode": a.escalation_episode,
    }


def checkin(c: CheckIn) -> dict[str, Any]:
    return {
        "id": c.id,
        "alarm_id": c.alarm_id,
        "status": c.status,
        "scheduled_at": iso(c.scheduled_at),
        "sent_at": iso(c.sent_at),
        "responded_at": iso(c.responded_at),
        "channel": c.channel,
    }


def escalation(e: EscalationLog) -> dict[str, Any]:
    return {
        "id": e.id,
        "alarm_id": e.alarm_id,
        "episode": e.episode,
        "attempt": e.attempt,
        "trigger": e.trigger,
        "method": e.method,
        "decided_by": e.decided_by,
        "reasoning": e.reasoning,
        "confidence": e.confidence,
        "fallback_method": e.fallback_method,
        "fallback_used": e.fallback_used,
        "status": e.status,
        "error": e.error,
        "created_at": iso(e.created_at),
        "sent_at": iso(e.sent_at),
        "responded_at": iso(e.responded_at),
        "response_time_seconds": e.response_time_seconds,
    }


def reminder(r: Reminder, tz: str | None = None) -> dict[str, Any]:
    extras: list[datetime] = []
    for value in r.extra_times or []:
        try:
            extras.append(
                datetime.fromisoformat(str(value).replace("Z", "+00:00")).replace(tzinfo=None)
            )
        except ValueError:
            continue
    return {
        "id": r.id,
        "title": r.title,
        "notes": r.notes,
        "due_at": iso(r.due_at),
        "due_local": local(r.due_at, tz),
        "priority": r.priority,
        "category": r.category,
        "repeat_pattern": r.repeat_pattern,
        "series_id": r.series_id,
        "notification_type": r.notification_type,
        "extra_times": [iso(dt) for dt in extras],
        "extra_times_local": [local(dt, tz) for dt in extras],
        "status": r.status,
        "escalation_level": r.escalation_level,
        "completed_at": iso(r.completed_at),
        "snooze_count": r.snooze_count,
        "source": r.source,
        "created_at": iso(r.created_at),
    }


def preferences(p: UserPreferences) -> dict[str, Any]:
    return {
        "enabled_channels": p.enabled_channels,
        "escalation_order": p.escalation_order,
        "notification_methods": p.notification_methods,
        "quiet_hours_start": p.quiet_hours_start,
        "quiet_hours_end": p.quiet_hours_end,
        "checkin_enabled": p.checkin_enabled,
        "checkin_delay_minutes": p.checkin_delay_minutes,
        "default_task_type": p.default_task_type,
        "category_preferred_times": p.category_preferred_times or {},
        "suppressed_recommendation_types": p.suppressed_recommendation_types or [],
        "push_token": p.push_token,
    }


def contact(c: EmergencyContact, consent_link: str | None = None) -> dict[str, Any]:
    data = {"id": c.id, "name": c.name, "phone": c.phone, "consent_status": c.consent_status}
    if consent_link:
        data["consent_link"] = consent_link
    return data


def profile(p: UserProfile) -> dict[str, Any]:
    return {
        "escalation_stats": p.escalation_stats or {},
        "alarm_patterns": p.alarm_patterns or {},
        "task_completion_rates": p.task_completion_rates or {},
        "verification_stats": p.verification_stats or {},
        "data_status": p.data_status,
        "skipped_rows": p.skipped_rows,
        "optimal_settings": p.get_optimal_settings(),
        "updated_at": iso(p.updated_at),
    }


def recommendation(r: Recommendation) -> dict[str, Any]:
    return {
        "id": r.id,
        "type": r.type,
        "title": r.title,
        "rationale": r.rationale,
        "change": r.change,
        "source": r.source,
        "status": r.status,
        "created_at": iso(r.created_at),
    }


def notification(n: Notification) -> dict[str, Any]:
    return {
        "id": n.id,
        "kind": n.kind,
        "title": n.title,
        "body": n.body,
        "data": n.data,
        "read": n.read,
        "created_at": iso(n.created_at),
    }
