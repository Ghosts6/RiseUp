from __future__ import annotations

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import PlainTextResponse

from riseup.api import serializers as ser
from riseup.api.deps import current_user_id, uow
from riseup.api.schemas import AcknowledgeIn
from riseup.errors import NotFound
from riseup.models import User

router = APIRouter(prefix="/api", tags=["escalation (F04-F05, F14)"])

AWAKE_WORDS = {"awake", "yes", "y", "1", "up", "ok"}


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
