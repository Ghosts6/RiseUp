"""AI layer: LLM providers (Strategy), LLMClient (Adapter), prompts and response parsing."""

from riseup.ai.client import LLMClient, LLMError, LLMResponse, build_llm_client
from riseup.ai.parser import ParseError, ResponseParser
from riseup.ai.prompts import PromptBuilder
from riseup.ai.providers import ClaudeProvider, LLMProvider, MockProvider, OpenAIProvider

__all__ = [
    "ClaudeProvider",
    "LLMClient",
    "LLMError",
    "LLMProvider",
    "LLMResponse",
    "MockProvider",
    "OpenAIProvider",
    "ParseError",
    "PromptBuilder",
    "ResponseParser",
    "build_llm_client",
]
