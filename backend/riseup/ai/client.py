"""LLMClient: provider selection, retries and timeouts. Holds no domain logic (AD-04)."""

from __future__ import annotations

import logging
from dataclasses import dataclass

from riseup.ai.providers import (
    ClaudeProvider,
    LLMProvider,
    LLMProviderError,
    MockProvider,
    OpenAIProvider,
)
from riseup.config import Settings

log = logging.getLogger(__name__)

TASKS = ("escalation", "parse", "prioritize", "recommend")


class LLMError(Exception):
    """All configured attempts failed."""


@dataclass
class LLMResponse:
    text: str
    provider: str
    model: str

    @property
    def decided_by(self) -> str:
        return f"llm:{self.provider}"


class LLMClient:
    def __init__(
        self, providers: dict[str, LLMProvider], routing: dict[str, str], max_retries: int = 1
    ) -> None:
        if not providers:
            raise ValueError("At least one LLM provider is required")
        self.providers = providers
        self.routing = routing
        self.max_retries = max_retries

    def provider_for(self, task: str) -> LLMProvider:
        name = self.routing.get(task)
        if name and name in self.providers:
            return self.providers[name]
        real = [p for n, p in self.providers.items() if n != "mock"]
        return real[0] if real else next(iter(self.providers.values()))

    def send_request(self, prompt: str, task: str, system: str | None = None) -> LLMResponse:
        provider = self.provider_for(task)
        last_error: Exception | None = None
        for attempt in range(self.max_retries + 1):
            try:
                text = provider.complete(prompt, system)
                return LLMResponse(text=text, provider=provider.name, model=provider.model)
            except LLMProviderError as exc:
                last_error = exc
                log.warning("LLM %s attempt %d failed: %s", provider.label, attempt + 1, exc)
        raise LLMError(str(last_error))

    def describe(self) -> dict[str, str]:
        return {task: self.provider_for(task).label for task in TASKS}


def build_llm_client(settings: Settings) -> LLMClient:
    providers: dict[str, LLMProvider] = {"mock": MockProvider(settings.mock_llm_mode)}
    if settings.claude_api_key:
        providers["claude"] = ClaudeProvider(
            settings.claude_api_key, settings.claude_model, settings.llm_timeout_seconds
        )
    if settings.openai_api_key:
        providers["openai"] = OpenAIProvider(
            settings.openai_api_key, settings.openai_model, settings.llm_timeout_seconds
        )
    routing = {
        "escalation": settings.llm_escalation_provider,
        "parse": settings.llm_parse_provider,
        "prioritize": settings.llm_prioritize_provider,
        "recommend": settings.llm_recommend_provider,
    }
    return LLMClient(providers, routing, settings.llm_max_retries)
