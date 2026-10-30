"""Reminder commands: NL remind, task CRUD, priority preview (F07-F10)."""

from __future__ import annotations

from typing import Any

from riseup_cli.api_client import APIError
from riseup_cli.commands.base import IO, Command, Result, UserInputError

TASK_COLUMNS = [
    ("id", "ID"),
    ("title", "Title"),
    ("due_local", "Due"),
    ("priority", "Priority"),
    ("category", "Category"),
    ("repeat_pattern", "Repeat"),
    ("status", "Status"),
    ("escalation_level", "Lvl"),
]
TASK_FIELDS = TASK_COLUMNS + [("notification_type", "Notify"), ("notes", "Notes")]


def _task_body(args: dict[str, Any]) -> dict[str, Any]:
    mapping = {
        "title": "title",
        "due": "due",
        "priority": "priority",
        "category": "category",
        "repeat": "repeat_pattern",
        "notify": "notification_type",
        "notes": "notes",
    }
    body = {api: args[cli] for cli, api in mapping.items() if args.get(cli) is not None}
    extras = args.get("extra_times")
    if extras is not None:
        body["extra_times"] = list(extras)
    return body


def _describe(draft: dict) -> str:
    repeat = (
        f" | repeats {draft['repeat_pattern']}"
        if draft.get("repeat_pattern") not in (None, "none")
        else ""
    )
    return (
        f"Parsed: \"{draft.get('title')}\" | {str(draft.get('due') or '?').replace('T', ' ')} | "
        f"priority: {draft.get('priority')} | category: {draft.get('category')}{repeat}"
    )


class RemindCommand(Command):
    name = "remind"
    help = "Create a reminder from a sentence (F07)"
    MAX_CLARIFICATIONS = 2

    def execute(self, args, api, io: IO) -> Result:
        text = args["text"]
        parsed = api.post("/api/reminders/parse", {"text": text})
        for _ in range(self.MAX_CLARIFICATIONS):
            if parsed["status"] != "needs_clarification":
                break
            answer = io.prompt(parsed["question"])
            text = f"{text} {answer}"
            parsed = api.post("/api/reminders/parse", {"text": text})

        if parsed["status"] == "failed":
            raise UserInputError(parsed["message"])
        if parsed["status"] == "needs_clarification":
            raise UserInputError(f"Still unclear: {parsed['question']}")
        draft = parsed["draft"]
        io.echo(_describe(draft))
        if parsed["status"] == "past_time":
            if not io.confirm(parsed["question"]):
                return Result(message="Cancelled. Nothing saved.")
        elif not io.confirm("Save this reminder?"):
            return Result(message="Cancelled. Nothing saved.")
        reminder = api.post("/api/reminders", draft, source="nl")
        return Result(reminder, f"Reminder #{reminder['id']} scheduled for {reminder['due_local']}")


class AddTaskCommand(Command):
    name = "task.add"

    def execute(self, args, api, io: IO) -> Result:
        body = _task_body(args)
        if not body.get("title") or not body.get("due"):
            raise UserInputError(
                'Both --title and --due are required, e.g. --due "2026-10-20 09:00"'
            )
        try:
            reminder = api.post("/api/reminders", body)
        except APIError as exc:
            suggestion = exc.data.get("suggestion")
            if not suggestion or not io.confirm(f"{exc.message} ({suggestion.replace('T', ' ')})"):
                raise
            reminder = api.post("/api/reminders", {**body, "due": suggestion})
        return Result(
            reminder,
            f"Reminder #{reminder['id']} scheduled for {reminder['due_local']}",
            fields=TASK_FIELDS,
        )


class ListTasksCommand(Command):
    name = "task.list"

    def execute(self, args, api, io: IO) -> Result:
        status = args.get("status") or "open"
        data = api.get(
            "/api/reminders",
            status=None if status == "all" else status,
            category=args.get("category"),
            priority=args.get("priority"),
        )
        return Result(
            data,
            columns=TASK_COLUMNS,
            empty_message='No reminders. Try: riseup remind "call mom tomorrow at 9am"',
        )


class EditTaskCommand(Command):
    name = "task.edit"

    def execute(self, args, api, io: IO) -> Result:
        body = _task_body(args)
        if not body:
            raise UserInputError("Nothing to change. Pass at least one option.")
        reminder = api.patch(
            f"/api/reminders/{args['id']}", body, scope=args.get("scope") or "this"
        )
        return Result(reminder, "Reminder updated.", fields=TASK_FIELDS)


class DoneTaskCommand(Command):
    name = "task.done"

    def execute(self, args, api, io: IO) -> Result:
        reminder = api.post(
            f"/api/reminders/{args['id']}/complete", {"already_done": bool(args.get("already"))}
        )
        return Result(reminder, f"Done: {reminder['title']}. Reminder sequence stopped.")


class SnoozeTaskCommand(Command):
    name = "task.snooze"

    def execute(self, args, api, io: IO) -> Result:
        reminder = api.post(f"/api/reminders/{args['id']}/snooze", {"minutes": args.get("minutes")})
        return Result(reminder, f"Snoozed until {reminder['due_local']}.")


class DeleteTaskCommand(Command):
    name = "task.delete"

    def execute(self, args, api, io: IO) -> Result:
        if not io.confirm(f"Delete reminder {args['id']}?", default=False):
            return Result(message="Cancelled.")
        api.delete(f"/api/reminders/{args['id']}")
        return Result({"deleted": args["id"]}, f"Reminder {args['id']} deleted.")


class PriorityPreviewCommand(Command):
    name = "task.priority-preview"

    def execute(self, args, api, io: IO) -> Result:
        preview = api.get("/api/reminders/priority-preview")
        label = "rule-based fallback" if preview["fallback_used"] else preview["decided_by"]
        return Result(
            preview["order"],
            f"Why: {preview['reasoning']}\nDecided by: {label}",
            title="Ranking for reminders due within the hour:",
            columns=[
                ("id", "ID"),
                ("title", "Title"),
                ("priority", "Priority"),
                ("category", "Category"),
                ("due_local", "Due"),
            ],
            empty_message="No reminders due in the next hour.",
        )


COMMANDS = [
    RemindCommand,
    AddTaskCommand,
    ListTasksCommand,
    EditTaskCommand,
    DoneTaskCommand,
    SnoozeTaskCommand,
    DeleteTaskCommand,
    PriorityPreviewCommand,
]
