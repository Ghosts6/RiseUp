"""NLCommandParser (F07): LLM extracts fields; deterministic code validates them."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import TYPE_CHECKING, Any

from riseup.ai import LLMClient, LLMError, ParseError, PromptBuilder, ResponseParser
from riseup.timeutil import to_local

if TYPE_CHECKING:
    from riseup.models import User

MANUAL_HINT = 'Couldn\'t parse. Use: riseup task add --title "..." --due "YYYY-MM-DD HH:MM"'


@dataclass
class ParseResult:
    status: str  # ok | needs_clarification | past_time | failed
    draft: dict[str, Any] | None
    question: str | None
    message: str
    decided_by: str

    def as_dict(self) -> dict[str, Any]:
        return {
            "status": self.status,
            "draft": self.draft,
            "question": self.question,
            "message": self.message,
            "decided_by": self.decided_by,
        }


class NLCommandParser:
    def __init__(self, llm_client: LLMClient) -> None:
        self.llm = llm_client
        self.prompts = PromptBuilder()
        self.parser = ResponseParser()

    def parse(self, text: str, user: "User", now_utc: datetime) -> ParseResult:
        text = (text or "").strip()
        if not text:
            return ParseResult(
                "needs_clarification",
                None,
                "What should I remind you about?",
                "Empty request",
                "system",
            )
        now_local = to_local(now_utc, user.timezone).replace(tzinfo=None, second=0, microsecond=0)
        prompt = self.prompts.build_parse_prompt(text, now_local.isoformat(), user.timezone)
        decided_by = "system"
        dto = None
        for _ in range(2):  # malformed JSON gets one retry
            try:
                response = self.llm.send_request(prompt, "parse", self.prompts.system)
                decided_by = response.decided_by
            except LLMError:
                break
            try:
                dto = self.parser.parse_reminder(response.text)
                break
            except ParseError:
                continue
        if dto is None:
            return ParseResult(
                "failed", {"title": text, "notes": None}, None, MANUAL_HINT, decided_by
            )

        draft = {
            "title": dto.title.strip(),
            "due": dto.due,
            "priority": dto.priority,
            "category": dto.category,
            "repeat_pattern": dto.repeat,
        }
        if dto.needs_clarification or not draft["title"]:
            question = dto.clarification_question or "Could you add more detail?"
            return ParseResult("needs_clarification", draft, question, question, decided_by)
        try:
            due_local = datetime.fromisoformat(dto.due or "")
        except ValueError:
            return ParseResult(
                "needs_clarification", draft, "At what time?", "Missing or invalid time", decided_by
            )
        due_local = due_local.replace(tzinfo=None, second=0, microsecond=0)
        draft["due"] = due_local.strftime("%Y-%m-%dT%H:%M")
        if due_local < now_local:
            suggestion = due_local
            while suggestion < now_local:
                suggestion += timedelta(days=1)
            draft["due"] = suggestion.strftime("%Y-%m-%dT%H:%M")
            return ParseResult(
                "past_time",
                draft,
                "This time has passed. Set for tomorrow instead?",
                "Time is in the past; suggested the next day",
                decided_by,
            )
        return ParseResult("ok", draft, None, "Parsed", decided_by)
