import json
from datetime import datetime, timedelta
from types import SimpleNamespace

import pytest

from riseup.ai.client import LLMClient, LLMError
from riseup.ai.mock_nlp import parse_reminder_text
from riseup.ai.parser import ParseError, ResponseParser, extract_json
from riseup.ai.providers import LLMProvider, MockProvider
from riseup.task_manager.prioritizer import TaskPrioritizer

NOW = "2026-10-19T06:00"  # Monday


class FixedProvider(LLMProvider):
    name = "fixed"
    model = "fixed-1"

    def __init__(self, text: str) -> None:
        self.text = text

    def complete(self, prompt, system=None):
        return self.text


class TestExtractJson:
    def test_plain_fenced_and_wrapped(self):
        assert extract_json('{"a": 1}') == {"a": 1}
        assert extract_json('```json\n{"a": 2}\n```') == {"a": 2}
        assert extract_json('Here you go: {"a": 3} hope it helps') == {"a": 3}

    @pytest.mark.parametrize("text", ["", "no json here", "[1, 2]", "{broken"])
    def test_rejects_garbage(self, text):
        with pytest.raises(ParseError):
            extract_json(text)


class TestResponseParser:
    parser = ResponseParser()

    def test_decision_normalised_and_validated(self):
        dto = self.parser.parse_decision(
            '{"method": " SMS ", "reasoning": "ok", "confidence": 0.8, "fallback": "sms"}',
            ["call", "sms"],
        )
        assert dto.method == "sms"
        assert dto.fallback is None  # fallback equal to method is dropped

    def test_decision_rejects_disabled_method(self):
        with pytest.raises(ParseError, match="not enabled"):
            self.parser.parse_decision('{"method": "alexa"}', ["call", "sms"])

    def test_decision_rejects_out_of_range_confidence(self):
        with pytest.raises(ParseError):
            self.parser.parse_decision('{"method": "call", "confidence": 3}', ["call"])

    def test_ranking_must_be_a_permutation(self):
        assert self.parser.parse_ranking('{"order": [3, 1, 2]}', [1, 2, 3]).order == [3, 1, 2]
        for bad in ('{"order": [1, 2]}', '{"order": [1, 1, 2]}', '{"order": [1, 2, 9]}'):
            with pytest.raises(ParseError):
                self.parser.parse_ranking(bad, [1, 2, 3])

    def test_recommendations_drop_malformed_items(self):
        text = json.dumps(
            {
                "items": [
                    {"type": "t", "title": "ok", "change": {"setting": "x", "value": 1}},
                    {"title": "missing type"},
                ]
            }
        )
        assert [r.title for r in self.parser.parse_recommendations(text)] == ["ok"]


class TestLLMClient:
    def test_retries_then_raises(self):
        provider = MockProvider("error")
        client = LLMClient({"mock": provider}, {}, max_retries=2)
        with pytest.raises(LLMError):
            client.send_request("TASK_TYPE: escalation", "escalation")
        assert len(provider.calls) == 3

    def test_routes_to_configured_provider(self):
        client = LLMClient(
            {"mock": MockProvider(), "fixed": FixedProvider("{}")}, {"parse": "fixed"}
        )
        assert client.provider_for("parse").name == "fixed"
        assert (
            client.provider_for("escalation").name == "fixed"
        )  # real providers preferred over mock
        assert client.send_request("x", "parse").decided_by == "llm:fixed"


class TestMockNLP:
    def test_tomorrow_with_time(self):
        r = parse_reminder_text("remind me to call mom tomorrow at 9am", NOW)
        assert r["title"] == "Call mom"
        assert r["due"] == "2026-10-20T09:00"
        assert r["category"] == "personal"
        assert not r["needs_clarification"]

    def test_relative_time_and_urgency(self):
        r = parse_reminder_text("submit report urgently in 30 minutes", NOW)
        assert r["due"] == "2026-10-19T06:30"
        assert (r["priority"], r["category"]) == ("critical", "work")

    def test_weekday_and_health(self):
        r = parse_reminder_text("gym friday 6pm", NOW)
        assert (r["title"], r["due"], r["category"]) == ("Gym", "2026-10-23T18:00", "health")

    def test_repeat_and_today_rollover(self):
        r = parse_reminder_text("take pills every day at 5am", NOW)
        assert r["repeat"] == "daily"
        assert r["due"] == "2026-10-20T05:00"  # 05:00 already passed today

    def test_missing_time_asks_for_clarification(self):
        r = parse_reminder_text("buy milk", NOW)
        assert r["needs_clarification"] and r["clarification_question"] == "At what time?"


def reminder(id, priority, category, minutes=0, title=None):
    return SimpleNamespace(
        id=id,
        title=title or f"r{id}",
        priority=priority,
        category=category,
        due_at=datetime(2026, 10, 19, 10, 0) + timedelta(minutes=minutes),
    )


class TestPrioritizer:
    profile = SimpleNamespace(task_completion_rates={})
    now = datetime(2026, 10, 19, 10, 0)
    items = [
        reminder(1, "low", "work"),
        reminder(2, "critical", "personal"),
        reminder(3, "high", "health"),
        reminder(4, "high", "work"),
    ]

    def test_llm_ranking_used_when_valid(self):
        result = TaskPrioritizer(LLMClient({"mock": MockProvider()}, {})).prioritize_reminders(
            self.items, self.profile, self.now
        )
        assert not result.fallback_used
        assert result.order[0] == 2 and result.order[-1] == 1
        assert result.decided_by.startswith("llm:mock")

    @pytest.mark.parametrize(
        "provider",
        [MockProvider("invalid_json"), MockProvider("error"), FixedProvider('{"order": [2, 1]}')],
    )
    def test_fallback_sort_when_llm_fails_or_drops_items(self, provider):
        result = TaskPrioritizer(
            LLMClient({"p": provider}, {}, max_retries=0)
        ).prioritize_reminders(self.items, self.profile, self.now)
        assert result.fallback_used and result.decided_by == "fallback"
        assert result.order == [2, 3, 4, 1]  # priority, then category (health before work)

    def test_single_reminder_skips_llm(self):
        provider = MockProvider()
        result = TaskPrioritizer(LLMClient({"mock": provider}, {})).prioritize_reminders(
            self.items[:1], self.profile, self.now
        )
        assert result.order == [1] and provider.calls == []
