from __future__ import annotations

from fastapi import APIRouter, Depends, Request, Response

from riseup.api import serializers as ser
from riseup.api.deps import current_user_id, uow
from riseup.api.schemas import CompleteIn, ParseIn, ReminderIn, SnoozeIn

router = APIRouter(prefix="/api/reminders", tags=["reminders (F07-F10)"])


@router.get("")
def list_reminders(
    request: Request,
    status: str | None = None,
    category: str | None = None,
    priority: str | None = None,
    user_id: int = Depends(current_user_id),
) -> list[dict]:
    with uow(request) as s:
        tz = s.user(user_id).timezone
        return [
            ser.reminder(r, tz)
            for r in s.reminder_controller.list(user_id, status, category, priority)
        ]


@router.post("", status_code=201)
def create_reminder(
    body: ReminderIn,
    request: Request,
    source: str = "form",
    user_id: int = Depends(current_user_id),
) -> dict:
    with uow(request) as s:
        reminder = s.reminder_controller.create(
            user_id, body.model_dump(exclude_none=True), source="nl" if source == "nl" else "form"
        )
        return ser.reminder(reminder, s.user(user_id).timezone)


@router.post("/parse")
def parse_reminder(
    body: ParseIn, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    """F07: natural language -> draft (not saved until the user confirms)."""
    with uow(request) as s:
        result = s.agent.run("parse_reminder", {"user_id": user_id, "text": body.text})
        return result.output.as_dict()


@router.get("/priority-preview")
def priority_preview(request: Request, user_id: int = Depends(current_user_id)) -> dict:
    with uow(request) as s:
        tz = s.user(user_id).timezone
        preview = s.reminder_controller.priority_preview(user_id)
        preview["order"] = [ser.reminder(r, tz) for r in preview["order"]]
        return preview


@router.get("/{reminder_id}")
def get_reminder(
    reminder_id: int, request: Request, user_id: int = Depends(current_user_id)
) -> dict:
    with uow(request) as s:
        return ser.reminder(
            s.reminder_controller.get(user_id, reminder_id), s.user(user_id).timezone
        )


@router.patch("/{reminder_id}")
def update_reminder(
    reminder_id: int,
    body: ReminderIn,
    request: Request,
    scope: str = "this",
    user_id: int = Depends(current_user_id),
) -> dict:
    with uow(request) as s:
        reminder = s.reminder_controller.update(
            user_id, reminder_id, body.model_dump(exclude_none=True), scope
        )
        return ser.reminder(reminder, s.user(user_id).timezone)


@router.delete("/{reminder_id}", status_code=204)
def delete_reminder(reminder_id: int, request: Request, user_id: int = Depends(current_user_id)):
    with uow(request) as s:
        s.reminder_controller.delete(user_id, reminder_id)
    return Response(status_code=204)


@router.post("/{reminder_id}/complete")
def complete_reminder(
    reminder_id: int,
    request: Request,
    body: CompleteIn | None = None,
    user_id: int = Depends(current_user_id),
) -> dict:
    with uow(request) as s:
        reminder = s.reminder_controller.complete(
            user_id, reminder_id, body.already_done if body else False
        )
        return ser.reminder(reminder, s.user(user_id).timezone)


@router.post("/{reminder_id}/snooze")
def snooze_reminder(
    reminder_id: int,
    request: Request,
    body: SnoozeIn | None = None,
    user_id: int = Depends(current_user_id),
) -> dict:
    with uow(request) as s:
        reminder = s.reminder_controller.snooze(
            user_id, reminder_id, body.minutes if body else None
        )
        return ser.reminder(reminder, s.user(user_id).timezone)
