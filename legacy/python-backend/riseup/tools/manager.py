"""ToolManager: the only component that talks to `Tool` adapters."""

from __future__ import annotations

import logging
import smtplib
from email.message import EmailMessage
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
    def __init__(
        self,
        twilio: Tool,
        alexa: Tool,
        firebase: Tool,
        emergency_attempts: int = 3,
        smtp: dict[str, object] | None = None,
    ):
        self.twilio = twilio
        self.alexa = alexa
        self.firebase = firebase
        self.emergency_attempts = emergency_attempts
        self.smtp = smtp or {}

    @property
    def mode(self) -> dict[str, str]:
        modes = {
            t.name: ("mock" if t.is_mock else "live")
            for t in (self.twilio, self.alexa, self.firebase)
        }
        modes["email"] = "live" if self.smtp.get("host") else "mock"
        return modes

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
            return self.send_email(user.email, title, body)
        return ToolResult(False, channel, f"Unsupported channel '{channel}'")

    def send_email(self, to: str | None, subject: str, body: str) -> ToolResult:
        if not to:
            return ToolResult(False, "email", "No email")
        host = self.smtp.get("host")
        if not host:
            log.info("[email/mock] to=%s %s - %s", to, subject, body)
            return ToolResult(True, "email", "email queued (mock)")
        if "email" in (self.smtp.get("failures") or set()):
            return ToolResult(False, "email", "Simulated email failure")
        msg = EmailMessage()
        msg["Subject"] = subject
        msg["From"] = str(self.smtp.get("from") or self.smtp.get("username") or "riseup@localhost")
        msg["To"] = to
        msg.set_content(body)
        try:
            port = int(self.smtp.get("port") or 587)
            if self.smtp.get("use_tls", True):
                with smtplib.SMTP(str(host), port, timeout=15) as server:
                    server.starttls()
                    user = self.smtp.get("username")
                    password = self.smtp.get("password")
                    if user and password:
                        server.login(str(user), str(password))
                    server.send_message(msg)
            else:
                with smtplib.SMTP(str(host), port, timeout=15) as server:
                    user = self.smtp.get("username")
                    password = self.smtp.get("password")
                    if user and password:
                        server.login(str(user), str(password))
                    server.send_message(msg)
            return ToolResult(True, "email", f"email sent to {to}")
        except (OSError, smtplib.SMTPException) as exc:
            log.warning("SMTP send failed: %s", exc)
            return ToolResult(False, "email", f"email failed: {exc}")


def build_tool_manager(settings: Settings) -> ToolManager:
    failures = settings.mock_tool_failure_set
    twilio_sid = Settings.configured(settings.twilio_account_sid)
    twilio_token = Settings.configured(settings.twilio_auth_token)
    twilio_from = Settings.configured(settings.twilio_phone_number)
    alexa_key = Settings.configured(settings.alexa_api_key)
    firebase_key = Settings.configured(settings.firebase_api_key)
    smtp_host = Settings.configured(settings.smtp_host)
    if twilio_sid and twilio_token and twilio_from:
        twilio: Tool = TwilioClient(twilio_sid, twilio_token, twilio_from)
    else:
        twilio = MockTwilioClient(failures)
    alexa: Tool = (
        AlexaClient(alexa_key, settings.alexa_notify_url)
        if alexa_key
        else MockAlexaClient(failures)
    )
    firebase: Tool = (
        FirebaseClient(firebase_key) if firebase_key else MockFirebaseClient(failures)
    )
    smtp = {
        "host": smtp_host,
        "port": settings.smtp_port,
        "username": Settings.configured(settings.smtp_username),
        "password": Settings.configured(settings.smtp_password),
        "from": Settings.configured(settings.smtp_from),
        "use_tls": settings.smtp_use_tls,
        "failures": failures,
    }
    return ToolManager(twilio, alexa, firebase, settings.emergency_contact_attempts, smtp)
