"""Test harness: an in-memory app with a fake clock and a manually driven scheduler."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any

from fastapi.testclient import TestClient

from riseup.ai.client import LLMClient
from riseup.ai.providers import MockProvider
from riseup.alarm_system.verification.tasks import TRIVIA_BANK
from riseup.clock import FixedClock
from riseup.config import Settings
from riseup.container import Container
from riseup.main import build_container, create_app
from riseup.scheduling import ManualJobScheduler
from riseup.tools.manager import ToolManager
from riseup.tools.mock import MockAlexaClient, MockFirebaseClient, MockTwilioClient

# Monday 2026-10-19 10:00 UTC == 06:00 in the demo user's timezone (America/Toronto).
START = datetime(2026, 10, 19, 10, 0)


@dataclass
class Harness:
    client: TestClient
    container: Container
    clock: FixedClock
    scheduler: ManualJobScheduler
    llm: MockProvider
    twilio: MockTwilioClient
    alexa: MockAlexaClient
    firebase: MockFirebaseClient

    def advance(self, **delta: float) -> None:
        """Move time forward and run every job that became due."""
        self.clock.advance(**delta)
        self.scheduler.run_due(self.clock.now())

    def services(self):
        return self.container.unit_of_work()


def make_settings(**overrides: Any) -> Settings:
    return Settings(
        _env_file=None,
        database_url="sqlite://",
        enable_scheduler=False,
        redis_url=None,
        **overrides,
    )


def build_harness(
    llm_mode: str = "normal", failures: set[str] | None = None, **settings_overrides: Any
) -> Harness:
    settings = make_settings(**settings_overrides)
    clock = FixedClock(START)
    scheduler = ManualJobScheduler()
    failures = failures or set()
    twilio, alexa, firebase = (
        MockTwilioClient(failures),
        MockAlexaClient(failures),
        MockFirebaseClient(failures),
    )
    llm = MockProvider(llm_mode)
    container = build_container(
        settings,
        scheduler=scheduler,
        clock=clock,
        tool_manager=ToolManager(twilio, alexa, firebase, settings.emergency_contact_attempts),
        llm_client=LLMClient({"mock": llm}, {}, max_retries=0),
    )
    client = TestClient(create_app(settings, container))
    return Harness(client, container, clock, scheduler, llm, twilio, alexa, firebase)


def solve_challenge(h: Harness, alarm_id: int) -> dict[str, Any]:
    """Build a correct verification answer from the alarm's persisted challenge state."""
    with h.services() as s:
        state = dict(s.repos.alarms.get(alarm_id).current_task)
    kind = state["type"]
    if kind == "math":
        return {"answer": str(h.container.task_factory.from_state(state).answer)}
    if kind == "trivia":
        return {"answer": str(TRIVIA_BANK[state["index"]][2])}
    if kind == "custom":
        return {"completed": True}
    raise AssertionError(f"cannot solve {kind}")


def wrong_answer(h: Harness, alarm_id: int) -> dict[str, Any]:
    correct = int(solve_challenge(h, alarm_id)["answer"])
    return {"answer": str(correct + 1)}


def create_alarm(h: Harness, **body: Any) -> dict[str, Any]:
    payload = {"time": "07:00", "label": "Work", "verification_task_type": "math", **body}
    response = h.client.post("/api/alarms", json=payload)
    assert response.status_code == 201, response.text
    return response.json()


def ring_and_timeout(h: Harness, alarm_id: int) -> None:
    assert h.client.post(f"/api/alarms/{alarm_id}/trigger").status_code == 200
    h.advance(seconds=h.container.settings.verification_timeout_seconds + 1)


def escalations(h: Harness, alarm_id: int) -> list[dict[str, Any]]:
    return h.client.get(f"/api/alarms/{alarm_id}/escalation").json()["attempts"]
