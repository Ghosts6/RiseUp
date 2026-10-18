from __future__ import annotations

import secrets
from datetime import datetime
from typing import Generic, Sequence, TypeVar

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from riseup.errors import NotFound
from riseup.models import (
    Alarm,
    AlarmLog,
    CheckIn,
    EmergencyContact,
    EscalationLog,
    Notification,
    Recommendation,
    Reminder,
    ReminderLog,
    User,
    UserPreferences,
    UserProfile,
)

T = TypeVar("T")


class BaseRepository(Generic[T]):
    model: type[T]
    label = "Entity"

    def __init__(self, db: Session) -> None:
        self.db = db

    def get(self, entity_id: int) -> T | None:
        return self.db.get(self.model, entity_id)

    def get_or_404(self, entity_id: int, user_id: int | None = None) -> T:
        entity = self.get(entity_id)
        if entity is None or (user_id is not None and getattr(entity, "user_id", None) != user_id):
            raise NotFound(f"{self.label} {entity_id} not found")
        return entity

    def save(self, entity: T) -> T:
        self.db.add(entity)
        self.db.flush()
        return entity

    def delete(self, entity: T) -> None:
        self.db.delete(entity)
        self.db.flush()


class UserRepository(BaseRepository[User]):
    model = User
    label = "User"

    def by_token(self, token: str) -> User | None:
        return self.db.scalar(select(User).where(User.api_token == token))

    def first(self) -> User | None:
        return self.db.scalar(select(User).order_by(User.id).limit(1))

    def all_ids(self) -> list[int]:
        return list(self.db.scalars(select(User.id)))

    def ensure_demo_user(self, token: str) -> User:
        user = self.by_token(token) or self.first()
        if user is None:
            user = User(
                name="Demo User", email="demo@riseup.local", phone="+15555550100", api_token=token
            )
            user.preferences = UserPreferences()
            self.save(user)
        if user.preferences is None:
            user.preferences = UserPreferences()
            self.db.flush()
        return user


class PreferencesRepository(BaseRepository[UserPreferences]):
    model = UserPreferences
    label = "Preferences"

    def for_user(self, user_id: int) -> UserPreferences:
        prefs = self.db.get(UserPreferences, user_id)
        if prefs is None:
            prefs = UserPreferences(user_id=user_id)
            self.save(prefs)
        return prefs

    def contacts(self, user_id: int) -> list[EmergencyContact]:
        stmt = select(EmergencyContact).where(EmergencyContact.user_id == user_id)
        return list(self.db.scalars(stmt.order_by(EmergencyContact.id)))

    def contact(self, contact_id: int, user_id: int) -> EmergencyContact:
        contact = self.db.get(EmergencyContact, contact_id)
        if contact is None or contact.user_id != user_id:
            raise NotFound(f"Contact {contact_id} not found")
        return contact

    def contact_by_token(self, token: str) -> EmergencyContact | None:
        return self.db.scalar(
            select(EmergencyContact).where(EmergencyContact.consent_token == token)
        )

    def consented_contact(self, user_id: int) -> EmergencyContact | None:
        stmt = select(EmergencyContact).where(
            EmergencyContact.user_id == user_id, EmergencyContact.consent_status == "granted"
        )
        return self.db.scalar(stmt.order_by(EmergencyContact.id).limit(1))

    def add_contact(self, user_id: int, name: str, phone: str) -> EmergencyContact:
        contact = EmergencyContact(
            user_id=user_id, name=name, phone=phone, consent_token=secrets.token_urlsafe(16)
        )
        self.db.add(contact)
        self.db.flush()
        return contact


class AlarmRepository(BaseRepository[Alarm]):
    model = Alarm
    label = "Alarm"

    def for_user(self, user_id: int) -> list[Alarm]:
        return list(
            self.db.scalars(select(Alarm).where(Alarm.user_id == user_id).order_by(Alarm.time))
        )

    def count_for_user(self, user_id: int) -> int:
        return self.db.scalar(select(func.count()).where(Alarm.user_id == user_id)) or 0

    def active(self) -> list[Alarm]:
        return list(self.db.scalars(select(Alarm).where(Alarm.is_active.is_(True))))


class CheckInRepository(BaseRepository[CheckIn]):
    model = CheckIn
    label = "Check-in"

    def latest_open(self, user_id: int) -> CheckIn | None:
        stmt = (
            select(CheckIn)
            .where(CheckIn.user_id == user_id, CheckIn.status.in_(["scheduled", "sent", "missed"]))
            .order_by(CheckIn.scheduled_at.desc())
            .limit(1)
        )
        return self.db.scalar(stmt)

    def for_user(self, user_id: int, limit: int = 20) -> list[CheckIn]:
        stmt = select(CheckIn).where(CheckIn.user_id == user_id).order_by(CheckIn.id.desc())
        return list(self.db.scalars(stmt.limit(limit)))

    def pending(self) -> list[CheckIn]:
        return list(
            self.db.scalars(select(CheckIn).where(CheckIn.status.in_(["scheduled", "sent"])))
        )

    def since(self, user_id: int, since: datetime) -> list[CheckIn]:
        stmt = select(CheckIn).where(CheckIn.user_id == user_id, CheckIn.created_at >= since)
        return list(self.db.scalars(stmt))


class ReminderRepository(BaseRepository[Reminder]):
    model = Reminder
    label = "Reminder"

    def for_user(
        self,
        user_id: int,
        status: str | None = None,
        category: str | None = None,
        priority: str | None = None,
    ) -> list[Reminder]:
        stmt = select(Reminder).where(Reminder.user_id == user_id)
        if status == "open":
            stmt = stmt.where(Reminder.status.in_(["scheduled", "notifying"]))
        elif status and status != "all":
            stmt = stmt.where(Reminder.status == status)
        if category:
            stmt = stmt.where(Reminder.category == category)
        if priority:
            stmt = stmt.where(Reminder.priority == priority)
        return list(self.db.scalars(stmt.order_by(Reminder.due_at)))

    def due_scheduled(self, user_id: int, until: datetime) -> list[Reminder]:
        stmt = select(Reminder).where(
            Reminder.user_id == user_id, Reminder.status == "scheduled", Reminder.due_at <= until
        )
        return list(self.db.scalars(stmt.order_by(Reminder.due_at)))

    def open_all(self) -> list[Reminder]:
        stmt = select(Reminder).where(Reminder.status.in_(["scheduled", "notifying"]))
        return list(self.db.scalars(stmt))

    def since(self, user_id: int, since: datetime) -> list[Reminder]:
        stmt = select(Reminder).where(Reminder.user_id == user_id, Reminder.due_at >= since)
        return list(self.db.scalars(stmt))


class EscalationLogRepository(BaseRepository[EscalationLog]):
    model = EscalationLog
    label = "Escalation"

    def episode(self, episode: str) -> list[EscalationLog]:
        stmt = select(EscalationLog).where(EscalationLog.episode == episode)
        return list(self.db.scalars(stmt.order_by(EscalationLog.attempt)))

    def recent(self, user_id: int, limit: int = 20) -> list[EscalationLog]:
        stmt = select(EscalationLog).where(EscalationLog.user_id == user_id)
        return list(self.db.scalars(stmt.order_by(EscalationLog.id.desc()).limit(limit)))

    def for_alarm(self, alarm_id: int) -> list[EscalationLog]:
        stmt = select(EscalationLog).where(EscalationLog.alarm_id == alarm_id)
        return list(self.db.scalars(stmt.order_by(EscalationLog.id)))

    def since(self, user_id: int, since: datetime | None = None) -> list[EscalationLog]:
        stmt = select(EscalationLog).where(EscalationLog.user_id == user_id)
        if since:
            stmt = stmt.where(EscalationLog.created_at >= since)
        return list(self.db.scalars(stmt.order_by(EscalationLog.id)))

    def record_escalation(self, log: EscalationLog) -> EscalationLog:
        return self.save(log)


class LogRepository:
    """Alarm and reminder event logs."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def add_alarm_log(self, alarm: Alarm | None, user_id: int, event: str, **details) -> AlarmLog:
        entry = AlarmLog(
            alarm_id=alarm.id if alarm else None,
            user_id=user_id,
            event=event,
            task_type=(alarm.current_task or {}).get("type") if alarm else None,
            details=details,
        )
        self.db.add(entry)
        return entry

    def add_reminder_log(
        self, user_id: int, event: str, reminder: Reminder | None = None, **fields
    ) -> ReminderLog:
        entry = ReminderLog(
            user_id=user_id,
            reminder_id=reminder.id if reminder else fields.pop("reminder_id", None),
            category=reminder.category if reminder else fields.pop("category", None),
            event=event,
            level=fields.pop("level", None),
            channel=fields.pop("channel", None),
            details=fields.pop("details", {}) | fields,
        )
        self.db.add(entry)
        return entry

    def alarm_logs(self, user_id: int, since: datetime | None = None) -> list[AlarmLog]:
        stmt = select(AlarmLog).where(AlarmLog.user_id == user_id)
        if since:
            stmt = stmt.where(AlarmLog.created_at >= since)
        return list(self.db.scalars(stmt.order_by(AlarmLog.id)))

    def reminder_logs(
        self, user_id: int, since: datetime | None = None, event: str | None = None
    ) -> list[ReminderLog]:
        stmt = select(ReminderLog).where(ReminderLog.user_id == user_id)
        if since:
            stmt = stmt.where(ReminderLog.created_at >= since)
        if event:
            stmt = stmt.where(ReminderLog.event == event)
        return list(self.db.scalars(stmt.order_by(ReminderLog.id)))

    def reminder_log(self, log_id: int) -> ReminderLog | None:
        return self.db.get(ReminderLog, log_id)


class ProfileRepository(BaseRepository[UserProfile]):
    model = UserProfile
    label = "Profile"

    def for_user(self, user_id: int) -> UserProfile:
        profile = self.db.get(UserProfile, user_id)
        if profile is None:
            profile = UserProfile(user_id=user_id)
            self.save(profile)
        return profile


class RecommendationRepository(BaseRepository[Recommendation]):
    model = Recommendation
    label = "Recommendation"

    def for_user(self, user_id: int, status: str | None = "pending") -> list[Recommendation]:
        stmt = select(Recommendation).where(Recommendation.user_id == user_id)
        if status:
            stmt = stmt.where(Recommendation.status == status)
        return list(self.db.scalars(stmt.order_by(Recommendation.id)))

    def clear_pending(self, user_id: int) -> None:
        for rec in self.for_user(user_id, "pending"):
            self.db.delete(rec)
        self.db.flush()


class NotificationRepository(BaseRepository[Notification]):
    model = Notification
    label = "Notification"

    def for_user(
        self, user_id: int, unread_only: bool = False, limit: int = 50
    ) -> Sequence[Notification]:
        stmt = select(Notification).where(Notification.user_id == user_id)
        if unread_only:
            stmt = stmt.where(Notification.read.is_(False))
        return list(self.db.scalars(stmt.order_by(Notification.id.desc()).limit(limit)))
