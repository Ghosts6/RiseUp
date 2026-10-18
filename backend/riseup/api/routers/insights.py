from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from riseup.api import serializers as ser
from riseup.api.deps import current_user_id, uow
from riseup.errors import ValidationFailed
from riseup.timeutil import parse_range

router = APIRouter(prefix="/api", tags=["learning, analytics, recommendations (F11-F13)"])


@router.get("/profile")
def get_profile(request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        return ser.profile(s.repos.profiles.for_user(user_id))


@router.post("/profile/recompute")
def recompute_profile(request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        return ser.profile(s.learning.analyze_behavior(user_id))


@router.get("/stats")
def stats(request: Request, range: str = "30d", user_id: int = Depends(current_user_id)) -> dict:
    try:
        days = parse_range(range)
    except ValueError as exc:
        raise ValidationFailed(str(exc), field="range") from exc
    with uow(request) as s:
        return s.analytics.get_stats(user_id, days)


@router.get("/recommendations")
def list_recommendations(request: Request, user_id: int = Depends(current_user_id)) -> list[dict]:
    with uow(request) as s:
        return [ser.recommendation(r) for r in s.recommendations.list_pending(user_id)]


@router.post("/recommendations/generate")
def generate_recommendations(request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        result = s.agent.run("recommend", {"user_id": user_id}).output
        return {
            "items": [ser.recommendation(r) for r in result["items"]],
            "source": result["source"],
            "message": result["message"],
        }


@router.post("/recommendations/{rec_id}/accept")
def accept_recommendation(
    rec_id: int, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        return ser.recommendation(s.recommendations.accept(user_id, rec_id))


@router.post("/recommendations/{rec_id}/dismiss")
def dismiss_recommendation(
    rec_id: int, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        return ser.recommendation(s.recommendations.dismiss(user_id, rec_id))
