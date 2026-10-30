from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from riseup.api import serializers as ser
from riseup.api.deps import current_user_id, uow
from riseup.errors import ValidationFailed
from riseup.timeutil import parse_range

router = APIRouter(prefix="/api/history", tags=["history (F14)"])


@router.get("")
def list_history(
    request: Request,
    type: str | None = None,
    since: str | None = None,
    page: int = 1,
    page_size: int = 25,
    user_id: int = Depends(current_user_id),
) -> dict:
    try:
        since_days = parse_range(since) if since else None
    except ValueError as exc:
        raise ValidationFailed(str(exc), field="since") from exc
    with uow(request) as s:
        return ser.jsonify(s.history.list(user_id, type, since_days, page, page_size))


@router.get("/{kind}/{ref_id}")
def explain(
    kind: str, ref_id: int, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        return ser.jsonify(s.history.explain(user_id, kind, ref_id))
