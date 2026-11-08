from __future__ import annotations

from sqlalchemy import JSON, Boolean, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from riseup.db import Base
from riseup.models.base import TimestampMixin

ESCALATION_CHANNELS = ["call", "sms", "alexa", "push", "emergency_contact"]
NOTIFICATION_METHODS = ["push", "sms", "call", "email"]


class User(TimestampMixin, Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(120), default="RiseUp User")
    email: Mapped[str | None] = mapped_column(String(255), unique=True, nullable=True)
    phone: Mapped[str | None] = mapped_column(String(32), nullable=True)
    timezone: Mapped[str] = mapped_column(String(64), default="America/Toronto")
    api_token: Mapped[str] = mapped_column(String(128), unique=True, index=True)

    preferences: Mapped["UserPreferences"] = relationship(
        back_populates="user", uselist=False, cascade="all, delete-orphan"
    )
    contacts: Mapped[list["EmergencyContact"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )


class UserPreferences(Base):
    __tablename__ = "user_preferences"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    enabled_channels: Mapped[list] = mapped_column(
        JSON, default=lambda: ["call", "sms", "alexa", "push"]
    )
    escalation_order: Mapped[list] = mapped_column(
        JSON, default=lambda: ["call", "sms", "alexa", "push", "emergency_contact"]
    )
    notification_methods: Mapped[list] = mapped_column(JSON, default=lambda: ["push"])
    quiet_hours_start: Mapped[str | None] = mapped_column(String(5), nullable=True)
    quiet_hours_end: Mapped[str | None] = mapped_column(String(5), nullable=True)
    checkin_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    checkin_delay_minutes: Mapped[int] = mapped_column(Integer, default=7)
    default_task_type: Mapped[str] = mapped_column(String(32), default="math")
    push_token: Mapped[str | None] = mapped_column(String(255), nullable=True)
    category_preferred_times: Mapped[dict] = mapped_column(JSON, default=dict)
    suppressed_recommendation_types: Mapped[list] = mapped_column(JSON, default=list)
    dismissed_recommendation_counts: Mapped[dict] = mapped_column(JSON, default=dict)

    user: Mapped[User] = relationship(back_populates="preferences")


class EmergencyContact(Base):
    __tablename__ = "emergency_contacts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    name: Mapped[str] = mapped_column(String(120))
    phone: Mapped[str] = mapped_column(String(32))
    consent_status: Mapped[str] = mapped_column(String(16), default="pending")
    consent_token: Mapped[str] = mapped_column(String(64), unique=True, index=True)

    user: Mapped[User] = relationship(back_populates="contacts")

    @property
    def has_consent(self) -> bool:
        return self.consent_status == "granted"
