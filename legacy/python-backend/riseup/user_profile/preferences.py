"""PreferencesController: escalation channels, quiet hours, emergency contact consent (F06)."""

from __future__ import annotations

import re
from typing import TYPE_CHECKING, Any

from riseup.errors import NotFound, ValidationFailed
from riseup.models import EmergencyContact
from riseup.models.alarm import TASK_TYPES
from riseup.models.user import ESCALATION_CHANNELS, NOTIFICATION_METHODS
from riseup.timeutil import get_zone, is_valid_hhmm

if TYPE_CHECKING:
    from riseup.container import AppServices

PHONE_RE = re.compile(r"^\+?[1-9]\d{7,14}$")


def normalize_phone(phone: str) -> str:
    cleaned = re.sub(r"[\s\-().]", "", phone or "")
    if not PHONE_RE.match(cleaned):
        raise ValidationFailed(
            "Invalid phone number. Use international format, e.g. +14165550123", field="phone"
        )
    return cleaned if cleaned.startswith("+") else f"+{cleaned}"


class PreferencesController:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    def get(self, user_id: int) -> dict[str, Any]:
        user = self.s.user(user_id)
        prefs = self.s.repos.prefs.for_user(user_id)
        contacts = self.s.repos.prefs.contacts(user_id)
        return {
            "user": user,
            "preferences": prefs,
            "contacts": contacts,
            "warnings": self.warnings(prefs, contacts),
            "integrations": self.s.tool_manager.mode,
            "llm": self.s.llm_client.describe(),
        }

    def warnings(self, prefs, contacts: list[EmergencyContact]) -> list[str]:
        result = []
        if not prefs.enabled_channels:
            result.append("All escalation channels are disabled: alarms will not escalate.")
        if "emergency_contact" in prefs.enabled_channels and not any(
            c.has_consent for c in contacts
        ):
            result.append("Emergency contact is enabled but no contact has consented yet.")
        if not prefs.notification_methods:
            result.append(
                "Reminder notifications are disabled; reminders show on the dashboard only."
            )
        return result

    def update(self, user_id: int, data: dict[str, Any]) -> dict[str, Any]:
        user = self.s.user(user_id)
        prefs = self.s.repos.prefs.for_user(user_id)
        if "enabled_channels" in data and data["enabled_channels"] is not None:
            channels = list(dict.fromkeys(data["enabled_channels"]))
            bad = set(channels) - set(ESCALATION_CHANNELS)
            if bad:
                raise ValidationFailed(
                    f"Unknown channel(s): {', '.join(sorted(bad))}", field="enabled_channels"
                )
            prefs.enabled_channels = channels
        if data.get("escalation_order") is not None:
            order = [m for m in dict.fromkeys(data["escalation_order"]) if m in ESCALATION_CHANNELS]
            order += [m for m in ESCALATION_CHANNELS if m not in order]
            order.remove("emergency_contact")
            prefs.escalation_order = order + ["emergency_contact"]
        if data.get("notification_methods") is not None:
            methods = list(dict.fromkeys(data["notification_methods"]))
            bad = set(methods) - set(NOTIFICATION_METHODS)
            if bad:
                raise ValidationFailed(
                    f"Unknown method(s): {', '.join(sorted(bad))}", field="notification_methods"
                )
            prefs.notification_methods = methods
        for key in ("quiet_hours_start", "quiet_hours_end"):
            if key in data:
                value = data[key] or None
                if value is not None and not is_valid_hhmm(value):
                    raise ValidationFailed("Quiet hours must be HH:MM", field=key)
                setattr(prefs, key, value)
        if data.get("checkin_enabled") is not None:
            prefs.checkin_enabled = bool(data["checkin_enabled"])
        if data.get("checkin_delay_minutes") is not None:
            delay = int(data["checkin_delay_minutes"])
            if not 5 <= delay <= 10:
                raise ValidationFailed(
                    "Check-in delay must be 5-10 minutes", field="checkin_delay_minutes"
                )
            prefs.checkin_delay_minutes = delay
        if data.get("default_task_type") is not None:
            if data["default_task_type"] not in TASK_TYPES:
                raise ValidationFailed("Unknown task type", field="default_task_type")
            prefs.default_task_type = data["default_task_type"]
        if data.get("category_preferred_times") is not None:
            times = data["category_preferred_times"]
            if not all(is_valid_hhmm(v) for v in times.values()):
                raise ValidationFailed("Preferred times must be HH:MM")
            prefs.category_preferred_times = {**(prefs.category_preferred_times or {}), **times}
        if "push_token" in data:
            prefs.push_token = data["push_token"] or None
        if data.get("phone") is not None:
            user.phone = normalize_phone(data["phone"]) if data["phone"] else None
        if data.get("name"):
            user.name = data["name"].strip()
        if data.get("email") is not None:
            user.email = data["email"] or None
        if data.get("timezone"):
            if get_zone(data["timezone"]).key != data["timezone"]:
                raise ValidationFailed("Unknown timezone", field="timezone")
            user.timezone = data["timezone"]
        self.s.cache.delete_prefix(f"stats:{user_id}:")
        return self.get(user_id)

    # emergency contacts -------------------------------------------------
    def add_contact(self, user_id: int, name: str, phone: str) -> EmergencyContact:
        if not (name or "").strip():
            raise ValidationFailed("Contact name is required", field="name")
        contact = self.s.repos.prefs.add_contact(user_id, name.strip(), normalize_phone(phone))
        self.request_consent(contact)
        return contact

    def consent_link(self, contact: EmergencyContact) -> str:
        return f"{self.s.settings.public_base_url}/api/contacts/consent/{contact.consent_token}"

    def request_consent(self, contact: EmergencyContact) -> None:
        user = self.s.user(contact.user_id)
        self.s.tool_manager.send_sms(
            contact.phone,
            f"{user.name} added you as their RiseUp emergency contact. "
            f"Confirm: {self.consent_link(contact)}?answer=yes",
        )

    def respond_consent(self, token: str, accept: bool) -> EmergencyContact:
        contact = self.s.repos.prefs.contact_by_token(token)
        if contact is None:
            raise NotFound("Consent link is invalid or expired")
        contact.consent_status = "granted" if accept else "denied"
        return contact

    def remove_contact(self, user_id: int, contact_id: int) -> None:
        contact = self.s.repos.prefs.contact(contact_id, user_id)
        self.s.db.delete(contact)

    def test_contact(self, user_id: int, contact_id: int) -> dict[str, Any]:
        contact = self.s.repos.prefs.contact(contact_id, user_id)
        result = self.s.tool_manager.send_sms(
            contact.phone, "RiseUp test message. No action needed."
        )
        return {"success": result.success, "channel": result.channel, "detail": result.detail}

    def test_channel(self, user_id: int, channel: str) -> dict[str, Any]:
        user = self.s.user(user_id)
        prefs = self.s.repos.prefs.for_user(user_id)
        if channel not in ESCALATION_CHANNELS or channel == "emergency_contact":
            raise ValidationFailed("Test channel must be call, sms, alexa, or push")
        result = self.s.tool_manager.execute_escalation(
            channel, user, prefs, message="RiseUp test message. No action needed."
        )
        return {"success": result.success, "channel": result.channel, "detail": result.detail}
