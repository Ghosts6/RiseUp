"""AgentController: observe -> decide -> act -> observe outcome -> re-decide (AD-05)."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

from riseup.models.user import ESCALATION_CHANNELS

if TYPE_CHECKING:
    from riseup.container import AppServices

log = logging.getLogger(__name__)


@dataclass
class AgentResult:
    goal: str
    status: str
    steps: list[dict[str, Any]] = field(default_factory=list)
    output: Any = None


class AgentController:
    def __init__(self, services: "AppServices") -> None:
        self.s = services

    def run(self, goal: str, context: dict[str, Any]) -> AgentResult:
        handler = {
            "escalate": self._escalate,
            "parse_reminder": self._parse_reminder,
            "recommend": self._recommend,
        }.get(goal)
        if handler is None:
            raise ValueError(f"Unknown agent goal '{goal}'")
        return handler(context)

    def _escalate(self, context: dict[str, Any]) -> AgentResult:
        engine = self.s.escalation_engine
        result = AgentResult("escalate", "started")
        ctx = engine.build_context(context["alarm_id"], context["episode"], context["trigger"])
        if (
            ctx is None
            or ctx.alarm.status != "escalating"
            or ctx.alarm.escalation_episode != context["episode"]
        ):
            result.status = "stale"
            return result

        for _ in range(len(ESCALATION_CHANNELS)):
            available = engine.available_methods(ctx)  # observe
            result.steps.append({"observe": {"available": available, "tried": ctx.tried}})
            if not available:
                reason = (
                    "All escalation methods are disabled or unavailable"
                    if not ctx.attempts
                    else "Every available method was tried without a response"
                )
                engine.mark_unanswered(ctx, reason)
                result.status = "unanswered"
                return result
            decision = engine.decide_escalation(ctx, available)  # decide
            result.steps.append(
                {
                    "decide": {
                        "method": decision.method,
                        "decided_by": decision.decided_by,
                        "fallback_used": decision.fallback_used,
                    }
                }
            )
            entry, tool_result = engine.execute(ctx, decision)  # act
            result.steps.append(
                {
                    "act": {
                        "log_id": entry.id,
                        "success": tool_result.success,
                        "detail": tool_result.detail,
                    }
                }
            )
            if tool_result.success:  # wait for the user; follow-up re-enters this loop
                engine.schedule_followup(entry)
                result.status = "awaiting_response"
                result.output = entry
                return result
            log.info(
                "Escalation via %s failed (%s); re-deciding", decision.method, tool_result.detail
            )

        engine.mark_unanswered(ctx, "Escalation step limit reached")
        result.status = "unanswered"
        return result

    def _parse_reminder(self, context: dict[str, Any]) -> AgentResult:
        user = self.s.user(context["user_id"])
        parsed = self.s.nl_parser.parse(context["text"], user, self.s.now())
        return AgentResult("parse_reminder", parsed.status, output=parsed)

    def _recommend(self, context: dict[str, Any]) -> AgentResult:
        output = self.s.recommendations.generate(context["user_id"])
        return AgentResult("recommend", "done", output=output)
