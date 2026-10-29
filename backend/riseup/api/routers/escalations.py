from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import PlainTextResponse

from riseup.api import serializers as ser
from riseup.api.deps import current_user_id, uow
from riseup.api.schemas import AcknowledgeIn
from riseup.errors import NotFound
from riseup.models import User

router = APIRouter(prefix="/api", tags=["escalation (F04-F05, F14)"])

AWAKE_WORDS = {"awake", "yes", "y", "1", "up", "ok"}


def _alexa_utterance(body: dict[str, Any]) -> str | None:
    """Extract free text from an Alexa Skill request or a simple {text|utterance} payload."""
    if not isinstance(body, dict):
        return None
    for key in ("text", "utterance", "speech"):
        value = body.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    request = body.get("request") or {}
    if not isinstance(request, dict):
        return None
    if request.get("type") == "LaunchRequest":
        return None
    intent = request.get("intent") or {}
    slots = intent.get("slots") or {}
    for slot_name in ("utterance", "reminder", "text", "query"):
        slot = slots.get(slot_name) or {}
        value = slot.get("value")
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def _alexa_speech_response(speech: str, end_session: bool = True) -> dict[str, Any]:
    return {
        "version": "1.0",
        "response": {
            "outputSpeech": {"type": "PlainText", "text": speech},
            "shouldEndSession": end_session,
        },
    }


@router.get("/escalations")
def list_escalations(
    request: Request, limit: int = 20, user_id: int = Depends(current_user_id)
) -> list[dict]:
    with uow(request) as s:
        return [ser.escalation(e) for e in s.repos.escalations.recent(user_id, limit)]


@router.get("/escalations/{log_id}")
def explain_escalation(
    log_id: int, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        return ser.jsonify(s.history.explain(user_id, "escalation", log_id))


@router.post("/escalations/acknowledge")
def acknowledge(
    request: Request, body: AcknowledgeIn | None = None, user_id: int = Depends(current_user_id)
) -> dict:
    """User confirms they are awake (pressed 1 / replied AWAKE / dashboard button)."""
    with uow(request) as s:
        entry = s.escalation_engine.acknowledge_latest(user_id, body.via if body else "dashboard")
        if entry is None:
            raise NotFound("No active escalation to acknowledge")
        return {"acknowledged": True, "escalation": ser.escalation(entry)}


@router.post("/webhooks/twilio/sms", response_class=PlainTextResponse)
def twilio_sms_webhook(request: Request, From: str = Form(...), Body: str = Form("")) -> str:
    """Inbound SMS: 'AWAKE' acknowledges; 'do not call' disables call escalation (UC-03 E3)."""
    text = Body.strip().lower()
    with uow(request) as s:
        user = s.db.query(User).filter(User.phone == From).first()
        if user is None:
            return "Unknown number."
        if "do not call" in text or "stop call" in text:
            prefs = s.repos.prefs.for_user(user.id)
            prefs.enabled_channels = [c for c in prefs.enabled_channels if c != "call"]
            return "OK, RiseUp will not call you for escalations."
        if text in AWAKE_WORDS:
            entry = s.escalation_engine.acknowledge_latest(user.id, "sms_reply")
            return "Great, you're confirmed awake!" if entry else "Nothing to confirm right now."
    return "Reply AWAKE to confirm you're up."


@router.post("/webhooks/alexa")
async def alexa_voice_webhook(request: Request) -> dict[str, Any]:
    """F07 Alexa voice path: parse a spoken reminder (Skill IntentRequest or {text}).

    Does not auto-save — returns a draft (or clarifying question) for confirmation,
    matching the GUI/CLI NL confirm-before-save flow.
    """
    body = await request.json()
    request_type = (body.get("request") or {}).get("type") if isinstance(body, dict) else None
    if request_type == "LaunchRequest":
        return _alexa_speech_response(
            "Welcome to RiseUp. Say remind me, then what and when.", end_session=False
        )
    if request_type == "SessionEndedRequest":
        return _alexa_speech_response("Goodbye.")

    utterance = _alexa_utterance(body if isinstance(body, dict) else {})
    if not utterance:
        speech = "I didn't catch a reminder. Try: remind me to call mom tomorrow at 9 a.m."
        payload = {"status": "needs_clarification", "message": speech, "draft": None}
        if request_type:
            return _alexa_speech_response(speech, end_session=False)
        return payload

    with uow(request) as s:
        user = s.db.query(User).order_by(User.id).first()
        if user is None:
            raise NotFound("No user configured for Alexa reminders")
        result = s.agent.run("parse_reminder", {"user_id": user.id, "text": utterance})
        parsed = result.output.as_dict()
        status = parsed.get("status")
        draft = parsed.get("draft") or {}
        if status == "ok":
            speech = (
                f"Got it: {draft.get('title')} at {draft.get('due')}. "
                "Open RiseUp Tasks to confirm and save."
            )
        elif status == "past_time":
            speech = parsed.get("question") or "That time has passed. Should I use tomorrow instead?"
        elif status == "needs_clarification":
            speech = parsed.get("question") or "When should I remind you?"
        else:
            speech = "I couldn't parse that. Try again with a time, or add it in the RiseUp app."
        if request_type:
            return {
                **_alexa_speech_response(speech, end_session=status == "ok"),
                "sessionAttributes": {"riseup_parse": parsed},
            }
        return {**parsed, "speech": speech, "source": "alexa"}
