"""Concrete observers: dashboard feed + WebSocket, reminder history, escalation trigger."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from riseup.events import ReminderObserver, StatusEvent
from riseup.models import Notification, ReminderLog

if TYPE_CHECKING:
    from riseup.container import Container

log = logging.getLogger(__name__)


def describe(event: StatusEvent) -> tuple[str, str] | None:
    """Human-readable banner for events worth surfacing on the dashboard."""
    p = event.payload
    label = p.get("label") or "Alarm"
    if event.subject_type == "alarm":
        return {
            "ringing": (f"{label} is ringing", "Complete the challenge to dismiss it."),
            "escalating": (
                f"{label}: escalation started",
                "No response yet, RiseUp's agent is escalating.",
            ),
            "missed": (f"{label} went unanswered", f"Alarm at {p.get('time', '')} was missed."),
            "completed": (f"{label} completed", "Nice work, you're up!"),
        }.get(event.status)
    if event.subject_type == "checkin":
        return {
            "sent": ("Are you still awake?", "Confirm your check-in."),
            "missed": ("Check-in missed", "Escalation will begin."),
        }.get(event.status)
    if event.subject_type == "reminder":
        title = p.get("title", "Reminder")
        return {
            "primary_sent": (f"Reminder: {title}", "It's time."),
            "escalation_sent": (f"Still to do: {title}", "Second nudge."),
            "final_sent": (f"Last reminder: {title}", "Final nudge before it's marked missed."),
            "missed": (f"Missed: {title}", "Marked as missed."),
            "queued": (f"Reminder: {title}", "Delivery failed; shown here instead."),
        }.get(p.get("event", ""))
    if event.subject_type == "escalation":
        method = p.get("method", "?")
        return {
            "sent": (f"Escalation: {method} sent", p.get("reasoning", "")[:180]),
            "failed": (f"Escalation: {method} failed", p.get("error", "") or ""),
            "confirmed": ("Escalation confirmed", f"You responded via {method}."),
        }.get(event.status)
    return None


class DashboardNotifier(ReminderObserver):
    """Writes the dashboard feed and pushes every event over WebSocket."""

    def __init__(self, container: "Container") -> None:
        self.container = container

    def on_status_changed(self, event: StatusEvent) -> None:
        message = describe(event)
        notification_id = None
        if message:
            db = self.container.session_factory()
            try:
                title, body = message
                note = Notification(
                    user_id=event.user_id,
                    kind=event.subject_type,
                    title=title,
                    body=body,
                    data={"subject_id": event.subject_id, "status": event.status},
                )
                db.add(note)
                db.commit()
                notification_id = note.id
            finally:
                db.close()
        self.container.hub.broadcast(
            event.user_id,
            {
                "type": "event",
                "subject_type": event.subject_type,
                "subject_id": event.subject_id,
                "status": event.status,
                "event": event.payload.get("event"),
                "title": message[0] if message else None,
                "body": message[1] if message else None,
                "notification_id": notification_id,
                "at": f"{event.at.isoformat()}Z",
            },
        )


class ReminderLogger(ReminderObserver):
    """Persists reminder history (ReminderLog) from reminder status events."""

    subject_types = ("reminder",)

    def __init__(self, container: "Container") -> None:
        self.container = container

    def on_status_changed(self, event: StatusEvent) -> None:
        payload = dict(event.payload)
        db = self.container.session_factory()
        try:
            from riseup.models import Reminder

            reminder = db.get(Reminder, event.subject_id)
            db.add(
                ReminderLog(
                    user_id=event.user_id,
                    reminder_id=event.subject_id,
                    category=reminder.category if reminder else None,
                    event=payload.pop("event", event.status),
                    level=payload.pop("level", None),
                    channel=payload.pop("channel", None),
                    details={
                        **payload,
                        "title": reminder.title if reminder else None,
                        "priority": reminder.priority if reminder else None,
                    },
                )
            )
            db.commit()
        finally:
            db.close()


class StatsInvalidator(ReminderObserver):
    """Drops cached analytics for the user whenever their data changes."""

    def __init__(self, container: "Container") -> None:
        self.container = container

    def on_status_changed(self, event: StatusEvent) -> None:
        self.container.cache.delete_prefix(f"stats:{event.user_id}:")


class EscalationTrigger(ReminderObserver):
    """Starts the agent escalation loop when an alarm enters `escalating` (F03 -> F04)."""

    subject_types = ("alarm",)

    def __init__(self, container: "Container") -> None:
        self.container = container

    def on_status_changed(self, event: StatusEvent) -> None:
        if event.status != "escalating":
            return
        episode = event.payload.get("episode", "")
        self.container.scheduler.schedule(
            f"escalate:{event.subject_id}:{episode}",
            self.container.clock.now(),
            self.container.jobs.run_escalation,
            alarm_id=event.subject_id,
            episode=episode,
            trigger=event.payload.get("trigger", "verification_timeout"),
        )
