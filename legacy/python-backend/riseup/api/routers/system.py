from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from riseup import __version__
from riseup.api import serializers as ser
from riseup.api.deps import current_user_id, get_container, uow
from riseup.status import health_payload

router = APIRouter(prefix="/api", tags=["system"])


@router.get("/health")
def health(request: Request) -> dict:
    return health_payload(get_container(request), __version__)


@router.get("/me")
def me(request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        return ser.user(s.user(user_id))


@router.get("/notifications")
def notifications(
    request: Request, unread: bool = False, limit: int = 30, user_id: int = Depends(current_user_id)
) -> list[dict]:
    with uow(request) as s:
        return [ser.notification(n) for n in s.repos.notifications.for_user(user_id, unread, limit)]


@router.post("/notifications/read")
def mark_read(request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        items = s.repos.notifications.for_user(user_id, unread_only=True, limit=500)
        for note in items:
            note.read = True
        return {"marked": len(items)}
