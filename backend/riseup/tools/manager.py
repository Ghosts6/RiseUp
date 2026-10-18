"""ToolManager: the only component that talks to `Tool` adapters."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from riseup.config import Settings
from riseup.tools.adapters import AlexaClient, FirebaseClient, TwilioClient
from riseup.tools.base import Tool, ToolResult
from riseup.tools.mock import MockAlexaClient, MockFirebaseClient, MockTwilioClient

if TYPE_CHECKING:
    from riseup.models import EmergencyContact, User, UserPreferences

log = logging.getLogger(__name__)

ESCALATION_MESSAGES = {
    "call": "This is your RiseUp alarm. Please confirm you are awake.",
    "sms": "RiseUp: Your alarm has triggered. Reply AWAKE to confirm.",
    "alexa": "Your RiseUp alarm has triggered. Say yes to confirm you're awake.",
    "push": "RiseUp: you haven't confirmed you're awake. Tap to confirm.",
}


class ToolManager:
    def __init__(self, twilio: Tool, alexa: Tool, firebase: Tool, emergency_attempts: int = 3):
        self.twilio = twilio
        self.alexa = alexa
        self.firebase = firebase
        self.emergency_attempts = emergency_attempts

    @property
    def mode(self) -> dict[str, str]:
        return {
            t.name: ("mock" if t.is_mock else "live")
            for t in (self.twilio, self.alexa, self.firebase)
        }

    def channel_ready(self, channel: str, user: "User", contact: "EmergencyContact | None") -> bool:
        if channel in ("call", "sms"):
            return bool(user.phone)
        if channel == "emergency_contact":
            return contact is not None and contact.has_consent
        return channel in ("alexa", "push")

    def execute_escalation(
        self,
        method: str,
        user: "User",
        prefs: "UserPreferences",
        contact: "EmergencyContact | None" = None,
        message: str | None = None,
    ) -> ToolResult:
        if method == "call":
            if not user.phone:
                return ToolResult(False, "call", "No phone number on file")
            return self.twilio.execute(
                {
                    "action": "call",
                    "to": user.phone,
                    "message": message or ESCALATION_MESSAGES["call"],
                }
            )
        if method == "sms":
            if not user.phone:
                return ToolResult(False, "sms", "No phone number on file")
            return self.twilio.execute(
                {
                    "action": "sms",
                    "to": user.phone,
                    "message": message or ESCALATION_MESSAGES["sms"],
                }
            )
        if method == "alexa":
            return self.alexa.execute({"message": message or ESCALATION_MESSAGES["alexa"]})
        if method == "push":
            return self.send_push_notification(
                user, prefs, "RiseUp alarm", message or ESCALATION_MESSAGES["push"]
            )
        if method == "emergency_contact":
            return self.contact_emergency(user, contact)
        return ToolResult(False, method, f"Unknown escalation method '{method}'")

    def contact_emergency(self, user: "User", contact: "EmergencyContact | None") -> ToolResult:
        if contact is None or not contact.has_consent:
            return ToolResult(False, "emergency_contact", "No consenting emergency contact")
        message = (
            f"RiseUp alert: {user.name} is not responding to their alarm. Please check on them."
        )
        last = ToolResult(False, "emergency_contact", "not attempted")
        for attempt in range(1, self.emergency_attempts + 1):
            last = self.twilio.execute({"action": "sms", "to": contact.phone, "message": message})
            if last.success:
                return ToolResult(
                    True,
                    "emergency_contact",
                    f"{contact.name} notified",
                    last.external_id,
                    {"attempts": attempt},
                )
        return ToolResult(
            False,
            "emergency_contact",
            f"{contact.name} unreachable after {self.emergency_attempts} attempts",
        )

    def send_push_notification(
        self, user: "User", prefs: "UserPreferences", title: str, body: str
    ) -> ToolResult:
        token = prefs.push_token or (f"web-{user.id}" if self.firebase.is_mock else None)
        result = self.firebase.execute({"token": token, "title": title, "body": body})
        result.channel = "push"
        return result

    def send_sms(self, phone: str | None, message: str) -> ToolResult:
        if not phone:
            return ToolResult(False, "sms", "No phone number on file")
        return self.twilio.execute({"action": "sms", "to": phone, "message": message})

    def send_reminder(
        self, user: "User", prefs: "UserPreferences", channel: str, title: str, body: str
    ) -> ToolResult:
        if channel == "push":
            return self.send_push_notification(user, prefs, title, body)
        if channel == "sms":
            return self.send_sms(user.phone, f"{title}: {body}")
        if channel == "call":
            if not user.phone:
                return ToolResult(False, "call", "No phone number on file")
            return self.twilio.execute({"action": "call", "to": user.phone, "message": body})
        if channel == "email":
            log.info("[email] to=%s %s - %s", user.email, title, body)
            return ToolResult(
                bool(user.email), "email", "email queued" if user.email else "No email"
            )
        return ToolResult(False, channel, f"Unsupported channel '{channel}'")


def build_tool_manager(settings: Settings) -> ToolManager:
    failures = settings.mock_tool_failure_set
    if settings.twilio_account_sid and settings.twilio_auth_token and settings.twilio_phone_number:
        twilio: Tool = TwilioClient(
            settings.twilio_account_sid, settings.twilio_auth_token, settings.twilio_phone_number
        )
    else:
        twilio = MockTwilioClient(failures)
    alexa: Tool = (
        AlexaClient(settings.alexa_api_key, settings.alexa_notify_url)
        if settings.alexa_api_key
        else MockAlexaClient(failures)
    )
    firebase: Tool = (
        FirebaseClient(settings.firebase_api_key)
        if settings.firebase_api_key
        else MockFirebaseClient(failures)
    )
    return ToolManager(twilio, alexa, firebase, settings.emergency_contact_attempts)
