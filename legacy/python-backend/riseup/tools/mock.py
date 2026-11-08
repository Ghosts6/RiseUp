"""Offline adapters used when vendor keys are missing (and in tests)."""

from __future__ import annotations

import itertools
import logging
from typing import Any

from riseup.tools.base import Tool, ToolResult

log = logging.getLogger("riseup.tools.mock")
_ids = itertools.count(1)


class _MockTool(Tool):
    is_mock = True

    def __init__(self, failures: set[str] | None = None) -> None:
        self.failures = failures if failures is not None else set()
        self.sent: list[dict[str, Any]] = []

    def _deliver(self, channel: str, payload: dict[str, Any]) -> ToolResult:
        record = {"channel": channel, **payload}
        self.sent.append(record)
        if channel in self.failures:
            log.info("[mock %s] simulated failure: %s", channel, payload)
            return ToolResult(False, channel, f"simulated {channel} failure")
        log.info("[mock %s] %s", channel, payload)
        return ToolResult(
            True, channel, f"mock {channel} delivered", external_id=f"mock-{next(_ids)}"
        )


class MockTwilioClient(_MockTool):
    name = "twilio"

    def execute(self, payload: dict[str, Any]) -> ToolResult:
        if payload.get("action") == "call":
            return self.make_call(payload["to"], payload.get("message", ""))
        return self.send_sms(payload["to"], payload.get("message", ""))

    def make_call(self, to: str, message: str) -> ToolResult:
        return self._deliver("call", {"to": to, "message": message})

    def send_sms(self, to: str, message: str) -> ToolResult:
        return self._deliver("sms", {"to": to, "message": message})


class MockAlexaClient(_MockTool):
    name = "alexa"

    def execute(self, payload: dict[str, Any]) -> ToolResult:
        return self.send_alert(payload.get("message", ""))

    def send_alert(self, message: str) -> ToolResult:
        return self._deliver("alexa", {"message": message})


class MockFirebaseClient(_MockTool):
    name = "firebase"

    def execute(self, payload: dict[str, Any]) -> ToolResult:
        return self.send_push(
            payload.get("token"), payload.get("title", ""), payload.get("body", "")
        )

    def send_push(self, token: str | None, title: str, body: str) -> ToolResult:
        return self._deliver("push", {"token": token, "title": title, "body": body})
