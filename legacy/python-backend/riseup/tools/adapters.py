"""Real vendor adapters. Each one converts the vendor REST API into `Tool.execute`."""

from __future__ import annotations

import logging
from typing import Any
from xml.sax.saxutils import escape

import httpx

from riseup.tools.base import Tool, ToolResult

log = logging.getLogger(__name__)


class TwilioClient(Tool):
    """Calls and SMS through the Twilio REST API."""

    name = "twilio"
    BASE = "https://api.twilio.com/2010-04-01/Accounts"

    def __init__(self, account_sid: str, auth_token: str, from_number: str, timeout: float = 10):
        self.account_sid = account_sid
        self.auth = (account_sid, auth_token)
        self.from_number = from_number
        self.timeout = timeout

    def execute(self, payload: dict[str, Any]) -> ToolResult:
        action = payload.get("action")
        if action == "call":
            return self.make_call(payload["to"], payload.get("message", ""))
        if action == "sms":
            return self.send_sms(payload["to"], payload.get("message", ""))
        return ToolResult(False, "twilio", f"Unsupported action {action}")

    def _post(self, resource: str, data: dict, channel: str) -> ToolResult:
        url = f"{self.BASE}/{self.account_sid}/{resource}.json"
        try:
            response = httpx.post(url, data=data, auth=self.auth, timeout=self.timeout)
            if response.status_code >= 400:
                return ToolResult(
                    False, channel, f"Twilio error {response.status_code}: {response.text[:200]}"
                )
            body = response.json()
            return ToolResult(True, channel, "delivered to Twilio", external_id=body.get("sid"))
        except httpx.HTTPError as exc:
            return ToolResult(False, channel, f"Twilio unreachable: {exc}")

    def make_call(self, to: str, message: str) -> ToolResult:
        twiml = (
            "<Response><Gather numDigits='1'><Say>"
            f"{escape(message)} Press 1 to confirm you are awake.</Say></Gather></Response>"
        )
        return self._post("Calls", {"To": to, "From": self.from_number, "Twiml": twiml}, "call")

    def send_sms(self, to: str, message: str) -> ToolResult:
        return self._post("Messages", {"To": to, "From": self.from_number, "Body": message}, "sms")


class AlexaClient(Tool):
    """Alexa announcements via a Notify-Me style skill endpoint (access code = API key)."""

    name = "alexa"

    def __init__(self, access_code: str, url: str, timeout: float = 10):
        self.access_code = access_code
        self.url = url
        self.timeout = timeout

    def execute(self, payload: dict[str, Any]) -> ToolResult:
        return self.send_alert(payload.get("message", ""))

    def send_alert(self, message: str) -> ToolResult:
        try:
            response = httpx.post(
                self.url,
                json={"notification": message, "accessCode": self.access_code},
                timeout=self.timeout,
            )
            if response.status_code >= 400:
                return ToolResult(False, "alexa", f"Alexa error {response.status_code}")
            return ToolResult(True, "alexa", "announcement queued")
        except httpx.HTTPError as exc:
            return ToolResult(False, "alexa", f"Alexa unreachable: {exc}")


class FirebaseClient(Tool):
    """Push notifications through Firebase Cloud Messaging (HTTP API)."""

    name = "firebase"
    URL = "https://fcm.googleapis.com/fcm/send"

    def __init__(self, server_key: str, timeout: float = 10):
        self.server_key = server_key
        self.timeout = timeout

    def execute(self, payload: dict[str, Any]) -> ToolResult:
        return self.send_push(
            payload.get("token"), payload.get("title", ""), payload.get("body", "")
        )

    def send_push(self, token: str | None, title: str, body: str) -> ToolResult:
        if not token:
            return ToolResult(False, "push", "No device registered for push")
        try:
            response = httpx.post(
                self.URL,
                json={"to": token, "notification": {"title": title, "body": body}},
                headers={"Authorization": f"key={self.server_key}"},
                timeout=self.timeout,
            )
            if response.status_code >= 400:
                return ToolResult(False, "push", f"FCM error {response.status_code}")
            return ToolResult(True, "push", "push sent")
        except httpx.HTTPError as exc:
            return ToolResult(False, "push", f"FCM unreachable: {exc}")
