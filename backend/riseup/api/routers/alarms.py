from __future__ import annotations

from fastapi import APIRouter, Depends, Request, Response

from riseup.alarm_system.verification import VerificationResponse
from riseup.api import serializers as ser
from riseup.api.deps import current_user_id, uow
from riseup.api.schemas import AlarmIn, ToggleIn, VerifyIn

router = APIRouter(prefix="/api/alarms", tags=["alarms (F01-F02)"])
checkin_router = APIRouter(prefix="/api/checkins", tags=["check-ins (F03)"])


@router.get("")
def list_alarms(request: Request, user_id: int = Depends(current_user_id)) -> list[dict]:
    with uow(request) as s:
        tz = s.user(user_id).timezone
        return [ser.alarm(a, tz) for a in s.alarm_controller.list_alarms(user_id)]


@router.post("", status_code=201)
def create_alarm(body: AlarmIn, request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        alarm = s.alarm_controller.create_alarm(user_id, body.model_dump(exclude_none=True))
        return ser.alarm(alarm, s.user(user_id).timezone)


@router.get("/{alarm_id}")
def get_alarm(alarm_id: int, request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        return ser.alarm(s.alarm_controller.get(user_id, alarm_id), s.user(user_id).timezone)


@router.patch("/{alarm_id}")
def edit_alarm(
    alarm_id: int, body: AlarmIn, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        alarm = s.alarm_controller.edit_alarm(user_id, alarm_id, body.model_dump(exclude_none=True))
        return ser.alarm(alarm, s.user(user_id).timezone)


@router.delete("/{alarm_id}", status_code=204)
def delete_alarm(alarm_id: int, request: Request, user_id: int = Depends(current_user_id)):
    with uow(request) as s:
        s.alarm_controller.delete(user_id, alarm_id)
    return Response(status_code=204)


@router.post("/{alarm_id}/toggle")
def toggle_alarm(
    alarm_id: int,
    request: Request,
    body: ToggleIn | None = None,
    user_id: int = Depends(current_user_id),
) -> dict:
    with uow(request) as s:
        alarm = s.alarm_controller.toggle(user_id, alarm_id, body.active if body else None)
        return ser.alarm(alarm, s.user(user_id).timezone)


@router.post("/{alarm_id}/trigger")
def trigger_alarm(alarm_id: int, request: Request, user_id: int = Depends(current_user_id)) -> dict:
    """Ring the alarm now (demo/testing)."""
    with uow(request) as s:
        return ser.alarm(
            s.alarm_controller.trigger_now(user_id, alarm_id), s.user(user_id).timezone
        )


@router.get("/{alarm_id}/challenge")
def get_challenge(alarm_id: int, request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        return ser.jsonify(s.alarm_controller.get_challenge(user_id, alarm_id))


@router.post("/{alarm_id}/verify")
def verify(
    alarm_id: int, body: VerifyIn, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        result = s.alarm_controller.verify(
            user_id, alarm_id, VerificationResponse(**body.model_dump())
        )
        return ser.jsonify(result)


@router.post("/{alarm_id}/snooze")
def snooze(alarm_id: int, request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        return ser.alarm(s.alarm_controller.snooze(user_id, alarm_id), s.user(user_id).timezone)


@router.get("/{alarm_id}/escalation")
def escalation_status(
    alarm_id: int, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        status = s.alarm_controller.escalation_status(user_id, alarm_id)
        status["attempts"] = [ser.escalation(e) for e in status["attempts"]]
        return status


@checkin_router.get("")
def list_checkins(request: Request, user_id: int = Depends(current_user_id)) -> list[dict]:
    with uow(request) as s:
        return [ser.checkin(c) for c in s.checkins.list_recent(user_id)]


@checkin_router.post("/confirm")
def confirm_checkin(request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        checkin = s.checkins.record_response(user_id)
        message = (
            "Check-in confirmed late; escalation stopped."
            if checkin.status == "late"
            else "Check-in confirmed. Have a great day!"
        )
        return {**ser.checkin(checkin), "message": message}
