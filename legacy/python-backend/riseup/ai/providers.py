"""LLMProvider strategies: Claude, OpenAI, and an offline Mock."""

from __future__ import annotations

import json
import re
from abc import ABC, abstractmethod
from typing import Any

import httpx


class LLMProviderError(Exception):
    """Vendor call failed (network, auth, rate limit, timeout)."""


class LLMProvider(ABC):
    name: str = "provider"
    model: str = ""

    @abstractmethod
    def complete(self, prompt: str, system: str | None = None) -> str:
        """Send a prompt and return the raw text completion."""

    @property
    def label(self) -> str:
        return f"{self.name}:{self.model}" if self.model else self.name


class ClaudeProvider(LLMProvider):
    name = "claude"
    URL = "https://api.anthropic.com/v1/messages"

    def __init__(self, api_key: str, model: str = "claude-sonnet-4-5", timeout: float = 20):
        self.api_key = api_key
        self.model = model
        self.timeout = timeout

    def complete(self, prompt: str, system: str | None = None) -> str:
        body: dict[str, Any] = {
            "model": self.model,
            "max_tokens": 1024,
            "messages": [{"role": "user", "content": prompt}],
        }
        if system:
            body["system"] = system
        try:
            response = httpx.post(
                self.URL,
                json=body,
                headers={
                    "x-api-key": self.api_key,
                    "anthropic-version": "2023-06-01",
                    "content-type": "application/json",
                },
                timeout=self.timeout,
            )
        except httpx.HTTPError as exc:
            raise LLMProviderError(f"Claude request failed: {exc}") from exc
        if response.status_code >= 400:
            raise LLMProviderError(f"Claude error {response.status_code}: {response.text[:200]}")
        data = response.json()
        return "".join(block.get("text", "") for block in data.get("content", []))


class OpenAIProvider(LLMProvider):
    name = "openai"
    URL = "https://api.openai.com/v1/chat/completions"

    def __init__(self, api_key: str, model: str = "gpt-4o", timeout: float = 20):
        self.api_key = api_key
        self.model = model
        self.timeout = timeout

    def complete(self, prompt: str, system: str | None = None) -> str:
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": prompt})
        try:
            response = httpx.post(
                self.URL,
                json={
                    "model": self.model,
                    "messages": messages,
                    "temperature": 0.2,
                    "response_format": {"type": "json_object"},
                },
                headers={"Authorization": f"Bearer {self.api_key}"},
                timeout=self.timeout,
            )
        except httpx.HTTPError as exc:
            raise LLMProviderError(f"OpenAI request failed: {exc}") from exc
        if response.status_code >= 400:
            raise LLMProviderError(f"OpenAI error {response.status_code}: {response.text[:200]}")
        return response.json()["choices"][0]["message"]["content"]


def read_prompt(prompt: str) -> tuple[str, dict[str, Any]]:
    task = re.search(r"TASK_TYPE:\s*(\w+)", prompt)
    ctx = re.search(r"CONTEXT_JSON:\s*(\{.*?\})\s*\nTASK:", prompt, re.S)
    return (task.group(1) if task else "unknown", json.loads(ctx.group(1)) if ctx else {})


class MockProvider(LLMProvider):
    """Deterministic stand-in that answers RiseUp's structured prompts offline.

    `mode` lets tests simulate LLM failures: normal | invalid_json | error | disabled_method.
    """

    name = "mock"
    model = "riseup-mock-1"

    def __init__(self, mode: str = "normal") -> None:
        self.mode = mode
        self.calls: list[str] = []

    def complete(self, prompt: str, system: str | None = None) -> str:
        self.calls.append(prompt)
        if self.mode == "error":
            raise LLMProviderError("Simulated LLM outage")
        if self.mode == "invalid_json":
            return "Sure! I think a phone call would be best here."
        task, ctx = read_prompt(prompt)
        handler = getattr(self, f"_{task}", None)
        if handler is None:
            return json.dumps({"error": f"unknown task {task}"})
        return json.dumps(handler(ctx))

    def _escalation(self, ctx: dict[str, Any]) -> dict[str, Any]:
        available: list[str] = ctx.get("available_methods", [])
        if self.mode == "disabled_method":
            disabled = [
                m for m in ["call", "sms", "alexa", "emergency_contact"] if m not in available
            ]
            return {
                "method": disabled[0] if disabled else "carrier_pigeon",
                "reasoning": "Simulated invalid pick",
                "confidence": 0.9,
                "fallback": None,
            }
        stats: dict[str, dict] = ctx.get("escalation_stats", {})
        order: list[str] = ctx.get("escalation_order", available)

        def score(method: str) -> tuple[float, int]:
            entry = stats.get(method) or {}
            rate = entry.get("success_rate", 0.5) if entry.get("attempts") else 0.5
            if method == "emergency_contact":
                rate -= 1.0  # last resort
            rank = order.index(method) if method in order else len(order)
            return (-rate, rank)

        ranked = sorted(available, key=score)
        choice = ranked[0]
        entry = stats.get(choice) or {}
        if entry.get("attempts"):
            reason = (
                f"{choice} has woken you {entry.get('success_rate', 0):.0%} of the time "
                f"({entry.get('attempts')} attempts), the best of the enabled methods."
            )
            confidence = round(min(0.95, 0.5 + entry.get("success_rate", 0) / 2), 2)
        else:
            reason = (
                "No history for the remaining methods; "
                f"following your preferred order ({choice} first)."
            )
            confidence = 0.55
        return {
            "method": choice,
            "reasoning": reason,
            "confidence": confidence,
            "fallback": ranked[1] if len(ranked) > 1 else None,
        }

    def _parse_reminder(self, ctx: dict[str, Any]) -> dict[str, Any]:
        from riseup.ai.mock_nlp import parse_reminder_text

        return parse_reminder_text(ctx.get("text", ""), ctx.get("now_local", ""))

    def _prioritize(self, ctx: dict[str, Any]) -> dict[str, Any]:
        rank = {"critical": 0, "high": 1, "medium": 2, "low": 3}
        rates = ctx.get("completion_rates", {})
        items = ctx.get("reminders", [])
        ordered = sorted(
            items,
            key=lambda r: (
                rank.get(r.get("priority"), 2),
                -(rates.get(r.get("category"), {}) or {}).get("rate", 0.5),
                r.get("due_at", ""),
            ),
        )
        top = ordered[0] if ordered else {}
        reasoning = (
            f"'{top.get('title')}' goes first: {top.get('priority')} priority"
            + (f" and you complete {top.get('category')} tasks reliably" if top else "")
            + "; the rest follow by priority, completion rate, then due time."
        )
        return {"order": [r["id"] for r in ordered], "reasoning": reasoning}

    def _recommendations(self, ctx: dict[str, Any]) -> dict[str, Any]:
        from riseup.user_profile.recommendations import rule_based_recommendations

        return {"items": rule_based_recommendations(ctx)}
