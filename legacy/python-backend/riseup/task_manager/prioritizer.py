"""TaskPrioritizer (F09): LLM ranking validated as a permutation, else deterministic sort."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import TYPE_CHECKING, Sequence

from riseup.ai import LLMClient, LLMError, ParseError, PromptBuilder, ResponseParser
from riseup.timeutil import to_local

if TYPE_CHECKING:
    from riseup.models import Reminder, UserProfile

PRIORITY_RANK = {"critical": 0, "high": 1, "medium": 2, "low": 3}
CATEGORY_RANK = {"health": 0, "work": 1, "personal": 2, "other": 3}


@dataclass
class PrioritizationResult:
    order: list[int]
    reasoning: str
    decided_by: str
    fallback_used: bool
    inputs: dict = field(default_factory=dict)


def time_of_day(hour: int) -> str:
    if 5 <= hour < 12:
        return "morning"
    if 12 <= hour < 17:
        return "afternoon"
    if 17 <= hour < 22:
        return "evening"
    return "night"


class TaskPrioritizer:
    def __init__(self, llm_client: LLMClient) -> None:
        self.llm = llm_client
        self.prompts = PromptBuilder()
        self.parser = ResponseParser()

    @staticmethod
    def fallback_sort(reminders: Sequence["Reminder"]) -> list[int]:
        """priority -> category -> due time (documented deterministic fallback)."""
        ordered = sorted(
            reminders,
            key=lambda r: (
                PRIORITY_RANK.get(r.priority, 2),
                CATEGORY_RANK.get(r.category, 3),
                r.due_at,
                r.id,
            ),
        )
        return [r.id for r in ordered]

    def prioritize_reminders(
        self,
        reminders: Sequence["Reminder"],
        profile: "UserProfile",
        now: datetime,
        tz_name: str | None = None,
    ) -> PrioritizationResult:
        ids = [r.id for r in reminders]
        if len(reminders) <= 1:
            return PrioritizationResult(
                ids, "Only one reminder due; no ranking needed.", "system", False
            )
        local = to_local(now, tz_name)
        inputs = {
            "time_of_day": time_of_day(local.hour),
            "local_time": local.strftime("%H:%M"),
            "completion_rates": profile.task_completion_rates or {},
            "reminders": [
                {
                    "id": r.id,
                    "title": r.title,
                    "priority": r.priority,
                    "category": r.category,
                    "due_at": r.due_at.isoformat(),
                }
                for r in reminders
            ],
        }
        prompt = self.prompts.build_ranking_prompt(inputs)
        error = ""
        try:
            response = self.llm.send_request(prompt, "prioritize", self.prompts.system)
            ranking = self.parser.parse_ranking(response.text, ids)
            return PrioritizationResult(
                ranking.order,
                ranking.reasoning,
                f"{response.decided_by}:{response.model}",
                False,
                inputs,
            )
        except (LLMError, ParseError) as exc:
            error = str(exc)
        return PrioritizationResult(
            self.fallback_sort(reminders),
            f"Rule-based fallback (priority, category, due time). Reason: {error}",
            "fallback",
            True,
            inputs,
        )
