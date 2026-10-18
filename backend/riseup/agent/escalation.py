"""EscalationEngine: context gathering, AI decision with validation, fallback ladder, execution."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import TYPE_CHECKING, Any

from riseup.ai import LLMError, ParseError, PromptBuilder, ResponseParser
from riseup.models import Alarm, EscalationLog
from riseup.models.user import ESCALATION_CHANNELS
from riseup.timeutil import in_quiet_hours, to_local
from riseup.tools import ToolResult

if TYPE_CHECKING:
    from riseup.container import AppServices
    from riseup.models import EmergencyContact, User, UserPreferences, UserProfile

log = logging.getLogger(__name__)

FALLBACK_LADDER = ["call", "sms", "alexa", "push", "emergency_contact"]
INTRUSIVE_METHODS = {"call", "sms", "emergency_contact"}


@dataclass
class EscalationDecision:
    method: str
    reasoning: str
    confidence: float | None
    fallback: str | None
    decided_by: str
    fallback_used: bool
    inputs: dict = field(default_factory=dict)


@dataclass
class EscalationContext:
    alarm: Alarm
    user: "User"
    prefs: "UserPreferences"
    profile: "UserProfile"
    contact: "EmergencyContact | None"
    trigger: str
    episode: str
    now: datetime
    attempts: list[EscalationLog]
    history: list[EscalationLog]
    last_result: ToolResult | None = None

    @property
    def tried(self) -> list[str]:
        return [a.method for a in self.attempts if a.method]


def followup_job_id(log_id: int) -> str:
    return f"escalation-followup:{log_id}"


class EscalationEngine:
    def __init__(self, services: "AppServices") -> None:
        self.s = services
        self.prompts = PromptBuilder()
        self.parser = ResponseParser()

    # observe --------------------------------------------------------------
    def build_context(self, alarm_id: int, episode: str, trigger: str) -> EscalationContext | None:
        alarm = self.s.repos.alarms.get(alarm_id)
        if alarm is None:
            return None
        user = self.s.user(alarm.user_id)
        return EscalationContext(
            alarm=alarm,
            user=user,
            prefs=self.s.repos.prefs.for_user(user.id),
            profile=self.s.repos.profiles.for_user(user.id),
            contact=self.s.repos.prefs.consented_contact(user.id),
            trigger=trigger,
            episode=episode,
            now=self.s.now(),
            attempts=self.s.repos.escalations.episode(episode),
            history=self.s.repos.escalations.recent(user.id, 15),
        )

    def available_methods(self, ctx: EscalationContext) -> list[str]:
        enabled = set(ctx.prefs.enabled_channels or [])
        quiet = in_quiet_hours(
            ctx.now, ctx.user.timezone, ctx.prefs.quiet_hours_start, ctx.prefs.quiet_hours_end
        )
        order = [
            m for m in (ctx.prefs.escalation_order or FALLBACK_LADDER) if m in ESCALATION_CHANNELS
        ]
        order += [m for m in FALLBACK_LADDER if m not in order]
        result = []
        for method in order:
            if method not in enabled or method in ctx.tried:
                continue
            if quiet and method in INTRUSIVE_METHODS:
                continue
            if not self.s.tool_manager.channel_ready(method, ctx.user, ctx.contact):
                continue
            result.append(method)
        return result

    def decision_inputs(self, ctx: EscalationContext, available: list[str]) -> dict[str, Any]:
        local = to_local(ctx.now, ctx.user.timezone)
        last_success = next(
            (h.method for h in ctx.history if h.status == "confirmed" and h.method), None
        )
        return {
            "user": ctx.user.name,
            "trigger": ctx.trigger,
            "alarm": {"label": ctx.alarm.label, "time": ctx.alarm.time},
            "current_time": local.strftime("%H:%M"),
            "day_of_week": local.strftime("%A"),
            "timezone": ctx.user.timezone,
            "available_methods": available,
            "escalation_order": ctx.prefs.escalation_order,
            "escalation_stats": ctx.profile.escalation_stats or {},
            "last_successful_method": last_success,
            "tried_this_episode": [
                {"method": a.method, "status": a.status, "error": a.error} for a in ctx.attempts
            ],
            "recent_history": [
                {"method": h.method, "status": h.status, "at": h.created_at.isoformat()}
                for h in ctx.history[:10]
                if h.method
            ],
            "last_tool_result": (
                {
                    "channel": ctx.last_result.channel,
                    "success": ctx.last_result.success,
                    "detail": ctx.last_result.detail,
                }
                if ctx.last_result
                else None
            ),
        }

    # decide ---------------------------------------------------------------
    def decide_escalation(self, ctx: EscalationContext, available: list[str]) -> EscalationDecision:
        inputs = self.decision_inputs(ctx, available)
        prompt = self.prompts.build_escalation_prompt(inputs)
        errors: list[str] = []
        for _ in range(2):  # malformed / invalid output gets one retry
            try:
                response = self.s.llm_client.send_request(prompt, "escalation", self.prompts.system)
            except LLMError as exc:
                errors.append(f"LLM unavailable: {exc}")
                break
            try:
                dto = self.parser.parse_decision(response.text, available)
            except ParseError as exc:
                errors.append(str(exc))
                continue
            return EscalationDecision(
                method=dto.method,
                reasoning=dto.reasoning,
                confidence=dto.confidence,
                fallback=dto.fallback,
                decided_by=f"{response.decided_by}:{response.model}",
                fallback_used=False,
                inputs=inputs,
            )
        return self.fallback_ladder(ctx, available, "; ".join(errors), inputs)

    def fallback_ladder(
        self, ctx: EscalationContext, available: list[str], reason: str, inputs: dict | None = None
    ) -> EscalationDecision:
        """Deterministic safety net (UX-01): last method that worked, then the ladder."""
        last_success = next(
            (h.method for h in ctx.history if h.status == "confirmed" and h.method in available),
            None,
        )
        ladder = [m for m in FALLBACK_LADDER if m in available]
        if last_success and last_success != "emergency_contact":
            method = last_success
            why = f"used {method} because it worked last time"
        else:
            method = ladder[0]
            why = f"followed the default ladder ({' > '.join(ladder)})"
        remaining = [m for m in ladder if m != method]
        return EscalationDecision(
            method=method,
            reasoning=f"Rule-based fallback, {why}. Reason: {reason or 'LLM unavailable'}",
            confidence=None,
            fallback=remaining[0] if remaining else None,
            decided_by="fallback",
            fallback_used=True,
            inputs=inputs or self.decision_inputs(ctx, available),
        )

    # act ------------------------------------------------------------------
    def execute(
        self, ctx: EscalationContext, decision: EscalationDecision
    ) -> tuple[EscalationLog, ToolResult]:
        result = self.s.tool_manager.execute_escalation(
            decision.method, ctx.user, ctx.prefs, ctx.contact
        )
        now = self.s.now()
        entry = EscalationLog(
            user_id=ctx.user.id,
            alarm_id=ctx.alarm.id,
            episode=ctx.episode,
            attempt=len(ctx.attempts) + 1,
            trigger=ctx.trigger,
            method=decision.method,
            decided_by=decision.decided_by,
            reasoning=decision.reasoning,
            confidence=decision.confidence,
            fallback_method=decision.fallback,
            fallback_used=decision.fallback_used,
            inputs=decision.inputs,
            status="sent" if result.success else "failed",
            error=None if result.success else result.detail,
            sent_at=now if result.success else None,
        )
        self.s.repos.escalations.record_escalation(entry)
        if not result.success:
            ctx.profile.record_escalation_outcome(decision.method, False, None)
        ctx.attempts.append(entry)
        ctx.last_result = result
        self.s.emitter.emit(
            "escalation",
            entry,
            entry.status,
            payload={
                "method": entry.method,
                "reasoning": entry.reasoning,
                "error": entry.error,
                "alarm_id": ctx.alarm.id,
            },
        )
        return entry, result

    def schedule_followup(self, entry: EscalationLog) -> None:
        self.s.scheduler.schedule(
            followup_job_id(entry.id),
            self.s.now() + timedelta(seconds=self.s.settings.escalation_response_seconds),
            self.s.jobs.escalation_followup,
            log_id=entry.id,
        )

    # outcomes -------------------------------------------------------------
    def followup(self, log_id: int) -> None:
        """No acknowledgement within the response window: record and re-decide."""
        entry = self.s.repos.escalations.get(log_id)
        if entry is None or entry.status != "sent":
            return
        alarm = self.s.repos.alarms.get(entry.alarm_id) if entry.alarm_id else None
        entry.status = "no_response"
        profile = self.s.repos.profiles.for_user(entry.user_id)
        profile.record_escalation_outcome(entry.method or "", False, None)
        if (
            alarm is None
            or alarm.status != "escalating"
            or alarm.escalation_episode != entry.episode
        ):
            return
        self.s.agent.run(
            "escalate", {"alarm_id": alarm.id, "episode": entry.episode, "trigger": entry.trigger}
        )

    def acknowledge(self, alarm: Alarm, via: str) -> EscalationLog | None:
        if not alarm.escalation_episode:
            return None
        attempts = self.s.repos.escalations.episode(alarm.escalation_episode)
        pending = [a for a in attempts if a.status in ("sent", "no_response")]
        if not pending:
            return None
        entry = pending[-1]
        now = self.s.now()
        entry.status = "confirmed"
        entry.responded_at = now
        entry.response_time_seconds = (
            (now - entry.sent_at).total_seconds() if entry.sent_at else None
        )
        entry.inputs = {**(entry.inputs or {}), "acknowledged_via": via}
        self.s.scheduler.cancel(followup_job_id(entry.id))
        profile = self.s.repos.profiles.for_user(alarm.user_id)
        profile.record_escalation_outcome(entry.method or "", True, entry.response_time_seconds)
        self.s.emitter.emit(
            "escalation",
            entry,
            "confirmed",
            payload={"method": entry.method, "via": via, "alarm_id": alarm.id},
        )
        return entry

    def acknowledge_latest(self, user_id: int, via: str) -> EscalationLog | None:
        """User confirmed out-of-band (pressed 1 / replied AWAKE / dashboard button)."""
        for entry in self.s.repos.escalations.recent(user_id, 10):
            if entry.status in ("sent", "no_response") and entry.alarm_id:
                alarm = self.s.repos.alarms.get(entry.alarm_id)
                if (
                    alarm
                    and alarm.status == "escalating"
                    and alarm.escalation_episode == entry.episode
                ):
                    confirmed = self.acknowledge(alarm, via)
                    self.s.alarm_scheduler.finish(alarm, "completed")
                    return confirmed
        return None

    def mark_unanswered(self, ctx: EscalationContext, reason: str) -> None:
        if not ctx.attempts:
            entry = EscalationLog(
                user_id=ctx.user.id,
                alarm_id=ctx.alarm.id,
                episode=ctx.episode,
                attempt=1,
                trigger=ctx.trigger,
                method=None,
                decided_by="system",
                reasoning=reason,
                status="skipped",
                inputs={"enabled_channels": ctx.prefs.enabled_channels},
            )
            self.s.repos.escalations.record_escalation(entry)
        self.s.repos.logs.add_alarm_log(
            ctx.alarm, ctx.user.id, "missed", reason=reason, attempts=len(ctx.attempts)
        )
        self.s.alarm_scheduler.finish(ctx.alarm, "missed")
