from __future__ import annotations

import html

from fastapi import APIRouter, Depends, Request, Response
from fastapi.responses import HTMLResponse

from riseup.api import serializers as ser
from riseup.api.deps import current_user_id, uow
from riseup.api.schemas import ContactIn, PreferencesIn, TestChannelIn

router = APIRouter(prefix="/api/prefs", tags=["preferences (F06)"])
contacts_router = APIRouter(prefix="/api/contacts", tags=["emergency contact (F06)"])


def _prefs_payload(s, data: dict) -> dict:
    return {
        "user": ser.user(data["user"]),
        "preferences": ser.preferences(data["preferences"]),
        "contacts": [ser.contact(c, s.preferences.consent_link(c)) for c in data["contacts"]],
        "warnings": data["warnings"],
        "integrations": data["integrations"],
        "llm": data["llm"],
    }


@router.get("")
def get_prefs(request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        return _prefs_payload(s, s.preferences.get(user_id))


@router.patch("")
def update_prefs(
    body: PreferencesIn, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        return _prefs_payload(s, s.preferences.update(user_id, body.model_dump(exclude_unset=True)))


@router.post("/test")
def test_channel(
    body: TestChannelIn, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        return s.preferences.test_channel(user_id, body.channel)


@contacts_router.get("")
def list_contacts(request: Request, user_id: int = Depends(current_user_id)) -> list[dict]:
    with uow(request) as s:
        return [
            ser.contact(c, s.preferences.consent_link(c)) for c in s.repos.prefs.contacts(user_id)
        ]


@contacts_router.post("", status_code=201)
def add_contact(body: ContactIn, request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        contact = s.preferences.add_contact(user_id, body.name, body.phone)
        return ser.contact(contact, s.preferences.consent_link(contact))


@contacts_router.delete("/{contact_id}", status_code=204)
def remove_contact(contact_id: int, request: Request, user_id: int = Depends(current_user_id)):
    with uow(request) as s:
        s.preferences.remove_contact(user_id, contact_id)
    return Response(status_code=204)


@contacts_router.post("/{contact_id}/test")
def test_contact(
    contact_id: int, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        return s.preferences.test_contact(user_id, contact_id)


@contacts_router.get("/consent/{token}", response_class=HTMLResponse)
def consent(token: str, request: Request, answer: str = "yes") -> str:
    """Public link sent to the contact by SMS (no auth: the token is the credential)."""
    accept = answer.lower() in ("yes", "y", "accept", "true")
    with uow(request) as s:
        contact = s.preferences.respond_consent(token, accept)
        name = contact.name
    verdict = "You are now a RiseUp emergency contact." if accept else "You declined. Thanks!"
    return (
        "<html><body style='font-family:sans-serif'>"
        f"<h2>Hi {html.escape(name)}</h2><p>{verdict}</p></body></html>"
    )
