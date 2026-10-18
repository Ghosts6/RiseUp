"""ResponseParser: turns raw LLM text into validated DTOs, or raises ParseError."""

from __future__ import annotations

import json
import re
from typing import Any, Literal

from pydantic import BaseModel, Field, ValidationError


class ParseError(Exception):
    """LLM output is malformed or violates the contract."""


class EscalationDecisionDTO(BaseModel):
    method: str
    reasoning: str = ""
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)
    fallback: str | None = None


class ParsedReminderDTO(BaseModel):
    title: str = ""
    due: str | None = None
    priority: Literal["low", "medium", "high", "critical"] = "medium"
    category: Literal["work", "personal", "health", "other"] = "other"
    repeat: Literal["none", "daily", "weekly", "weekdays"] = "none"
    needs_clarification: bool = False
    clarification_question: str | None = None


class RankingDTO(BaseModel):
    order: list[int]
    reasoning: str = ""


class RecommendationChangeDTO(BaseModel):
    setting: str
    value: Any


class RecommendationDTO(BaseModel):
    type: str
    title: str
    rationale: str = ""
    change: RecommendationChangeDTO


def extract_json(text: str) -> dict[str, Any]:
    if not text or not text.strip():
        raise ParseError("Empty LLM response")
    cleaned = re.sub(r"^```(?:json)?|```$", "", text.strip(), flags=re.M).strip()
    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", cleaned, re.S)
        if not match:
            raise ParseError("No JSON object in LLM response")
        try:
            data = json.loads(match.group(0))
        except json.JSONDecodeError as exc:
            raise ParseError(f"Malformed JSON: {exc}") from exc
    if not isinstance(data, dict):
        raise ParseError("LLM response JSON is not an object")
    return data


class ResponseParser:
    def parse_decision(self, text: str, allowed: list[str]) -> EscalationDecisionDTO:
        try:
            decision = EscalationDecisionDTO.model_validate(extract_json(text))
        except ValidationError as exc:
            raise ParseError(f"Invalid decision: {exc.errors()[0]['msg']}") from exc
        decision.method = decision.method.strip().lower()
        if decision.method not in allowed:
            raise ParseError(f"LLM chose '{decision.method}', which is not enabled/available")
        if decision.fallback and (
            decision.fallback not in allowed or decision.fallback == decision.method
        ):
            decision.fallback = None
        return decision

    def parse_reminder(self, text: str) -> ParsedReminderDTO:
        try:
            return ParsedReminderDTO.model_validate(extract_json(text))
        except ValidationError as exc:
            raise ParseError(f"Invalid reminder: {exc.errors()[0]['msg']}") from exc

    def parse_ranking(self, text: str, ids: list[int]) -> RankingDTO:
        try:
            ranking = RankingDTO.model_validate(extract_json(text))
        except ValidationError as exc:
            raise ParseError(f"Invalid ranking: {exc.errors()[0]['msg']}") from exc
        if sorted(ranking.order) != sorted(ids):
            raise ParseError("Ranking must contain every reminder exactly once")
        return ranking

    def parse_recommendations(self, text: str) -> list[RecommendationDTO]:
        data = extract_json(text)
        items = data.get("items")
        if not isinstance(items, list):
            raise ParseError("Recommendations must contain an 'items' list")
        result = []
        for item in items:
            try:
                result.append(RecommendationDTO.model_validate(item))
            except ValidationError:
                continue  # drop malformed suggestions individually
        return result
