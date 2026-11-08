"""Request bodies (pydantic). Business validation lives in the domain layer."""

from __future__ import annotations

from pydantic import BaseModel, Field


class AlarmIn(BaseModel):
    time: str | None = None
    label: str | None = None
    sound: str | None = None
    vibration: str | None = None
    repeat_pattern: str | None = None
    snooze_enabled: bool | None = None
    verification_task_type: str | None = None
    custom_task_text: str | None = None
    priority: int | None = None
    is_active: bool | None = None


class ToggleIn(BaseModel):
    active: bool | None = None


class VerifyIn(BaseModel):
    answer: str | None = None
    completed: bool = False
    image_base64: str | None = None
    camera_error: bool = False


class AcknowledgeIn(BaseModel):
    via: str = "dashboard"


class PreferencesIn(BaseModel):
    enabled_channels: list[str] | None = None
    escalation_order: list[str] | None = None
    notification_methods: list[str] | None = None
    quiet_hours_start: str | None = Field(default=None)
    quiet_hours_end: str | None = Field(default=None)
    checkin_enabled: bool | None = None
    checkin_delay_minutes: int | None = None
    default_task_type: str | None = None
    category_preferred_times: dict[str, str] | None = None
    push_token: str | None = None
    phone: str | None = None
    name: str | None = None
    email: str | None = None
    timezone: str | None = None


class ContactIn(BaseModel):
    name: str
    phone: str


class TestChannelIn(BaseModel):
    channel: str


class ReminderIn(BaseModel):
    title: str | None = None
    notes: str | None = None
    due: str | None = None
    priority: str | None = None
    category: str | None = None
    repeat_pattern: str | None = None
    notification_type: str | None = None
    extra_times: list[str] | None = None
    allow_past: bool = False


class ParseIn(BaseModel):
    text: str


class CompleteIn(BaseModel):
    already_done: bool = False


class SnoozeIn(BaseModel):
    minutes: int | None = None
