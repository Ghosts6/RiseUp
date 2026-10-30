"""Alarm, verification, check-in and escalation commands (F01-F05)."""

from __future__ import annotations

from typing import Any

from riseup_cli.commands.base import IO, Command, Result, UserInputError

ALARM_COLUMNS = [
    ("id", "ID"),
    ("time", "Time"),
    ("label", "Label"),
    ("repeat_pattern", "Repeat"),
    ("verification_task_type", "Task"),
    ("is_active", "On"),
    ("status", "Status"),
    ("next_trigger_local", "Next"),
]
ALARM_FIELDS = ALARM_COLUMNS[:6] + [
    ("snooze_enabled", "Snooze"),
    ("sound", "Sound"),
    ("vibration", "Vibration"),
    ("status", "Status"),
    ("next_trigger_local", "Next ring"),
]
ESCALATION_COLUMNS = [
    ("id", "ID"),
    ("attempt", "#"),
    ("method", "Method"),
    ("status", "Status"),
    ("decided_by", "Decided by"),
    ("fallback_used", "Fallback"),
    ("created_at", "At"),
]


def _alarm_body(args: dict[str, Any]) -> dict[str, Any]:
    mapping = {
        "time": "time",
        "label": "label",
        "task": "verification_task_type",
        "repeat": "repeat_pattern",
        "snooze": "snooze_enabled",
        "sound": "sound",
        "vibration": "vibration",
        "custom_text": "custom_task_text",
    }
    return {api: args[cli] for cli, api in mapping.items() if args.get(cli) is not None}


class AddAlarmCommand(Command):
    name = "alarm.add"
    help = "Create an alarm"

    def execute(self, args, api, io: IO) -> Result:
        alarm = api.post("/api/alarms", _alarm_body(args))
        return Result(
            alarm,
            f"Alarm set for {alarm['time']} (next: {alarm['next_trigger_local']})",
            fields=ALARM_FIELDS,
        )


class ListAlarmsCommand(Command):
    name = "alarm.list"

    def execute(self, args, api, io: IO) -> Result:
        return Result(
            api.get("/api/alarms"),
            columns=ALARM_COLUMNS,
            empty_message="No alarms yet. Try: riseup alarm add 07:00 --task math",
        )


class EditAlarmCommand(Command):
    name = "alarm.edit"

    def execute(self, args, api, io: IO) -> Result:
        body = _alarm_body(args)
        if not body:
            raise UserInputError("Nothing to change. Pass at least one option, e.g. --time 06:30")
        alarm = api.patch(f"/api/alarms/{args['id']}", body)
        return Result(alarm, "Alarm updated.", fields=ALARM_FIELDS)


class DeleteAlarmCommand(Command):
    name = "alarm.delete"

    def execute(self, args, api, io: IO) -> Result:
        if not io.confirm(f"Delete alarm {args['id']}?", default=False):
            return Result(message="Cancelled.")
        api.delete(f"/api/alarms/{args['id']}")
        return Result({"deleted": args["id"]}, f"Alarm {args['id']} deleted.")


class ToggleAlarmCommand(Command):
    name = "alarm.toggle"

    def execute(self, args, api, io: IO) -> Result:
        alarm = api.post(f"/api/alarms/{args['id']}/toggle", {"active": args.get("active")})
        state = "enabled" if alarm["is_active"] else "disabled"
        return Result(alarm, f"Alarm {alarm['id']} {state}.")


class TriggerAlarmCommand(Command):
    name = "alarm.trigger"

    def execute(self, args, api, io: IO) -> Result:
        api.post(f"/api/alarms/{args['id']}/trigger")
        challenge = api.get(f"/api/alarms/{args['id']}/challenge")
        return ChallengeCommand.render(challenge, "Alarm is ringing!")


class ChallengeCommand(Command):
    name = "alarm.challenge"

    @staticmethod
    def render(challenge: dict, prefix: str = "") -> Result:
        task = challenge["challenge"]
        lines = [prefix] if prefix else []
        lines.append(f"Challenge ({task['type']}): {task['question']}")
        for index, choice in enumerate(task.get("choices", []) or []):
            lines.append(f"  [{index}] {choice}")
        if task["type"] == "object_detection":
            lines.append("Photo verification is GUI-only (needs a camera). Open the web app.")
        elif task["type"] == "custom":
            lines.append(f"When done: riseup alarm verify {challenge['alarm_id']} --done")
        else:
            lines.append(
                f"Answer with: riseup alarm verify {challenge['alarm_id']} --answer <value>"
            )
        return Result(challenge, "\n".join(lines))

    def execute(self, args, api, io: IO) -> Result:
        return self.render(api.get(f"/api/alarms/{args['id']}/challenge"))


class VerifyAlarmCommand(Command):
    name = "alarm.verify"

    def execute(self, args, api, io: IO) -> Result:
        challenge = api.get(f"/api/alarms/{args['id']}/challenge")
        if challenge["challenge"]["type"] == "object_detection":
            raise UserInputError(
                "Photo verification is GUI-only (needs a camera). "
                "Open the web app to take the photo."
            )
        if args.get("answer") is None and not args.get("done"):
            raise UserInputError("Pass --answer <value> (math/trivia) or --done (custom task)")
        result = api.post(
            f"/api/alarms/{args['id']}/verify",
            {"answer": args.get("answer"), "completed": bool(args.get("done"))},
        )
        if result["success"]:
            extra = f" Check-in at {result['checkin_at']}." if result.get("checkin_at") else ""
            return Result(result, result["message"] + extra)
        nxt = ChallengeCommand.render({"alarm_id": args["id"], "challenge": result["challenge"]})
        return Result(result, f"{result['message']}\n{nxt.message}", exit_code=1)


class SnoozeAlarmCommand(Command):
    name = "alarm.snooze"

    def execute(self, args, api, io: IO) -> Result:
        alarm = api.post(f"/api/alarms/{args['id']}/snooze")
        return Result(alarm, f"Snoozed ({alarm['snooze_count']} used). It will ring again soon.")


class ConfirmCheckinCommand(Command):
    name = "checkin.confirm"

    def execute(self, args, api, io: IO) -> Result:
        result = api.post("/api/checkins/confirm")
        return Result(result, result["message"])


class ListCheckinsCommand(Command):
    name = "checkin.list"

    def execute(self, args, api, io: IO) -> Result:
        return Result(
            api.get("/api/checkins"),
            columns=[
                ("id", "ID"),
                ("alarm_id", "Alarm"),
                ("status", "Status"),
                ("channel", "Channel"),
                ("scheduled_at", "Scheduled"),
                ("responded_at", "Responded"),
            ],
        )


class ExplainEscalationCommand(Command):
    name = "escalation.explain"

    def execute(self, args, api, io: IO) -> Result:
        d = api.get(f"/api/escalations/{args['id']}")
        conf = f"{d['confidence']:.2f}" if d.get("confidence") is not None else "n/a"
        lines = [
            f"Escalation #{d['id']} ({d['at']})",
            f"  Method chosen : {d['method'] or 'none'}        Confidence: {conf}",
            f"  Reasoning     : {d['reasoning']}",
            f"  Decided by    : {d['decided_by']}        Fallback used: "
            f"{'yes' if d['fallback_used'] else 'no'}",
            f"  Trigger       : {d['trigger']} (attempt {d['attempt']})",
            f"  Outcome       : {d['outcome']}"
            + (
                f" after {d['response_time_seconds']:.0f} s"
                if d.get("response_time_seconds")
                else ""
            )
            + (f" ({d['error']})" if d.get("error") else ""),
        ]
        return Result(d, "\n".join(lines))


class EscalationStatusCommand(Command):
    name = "escalation.status"

    def execute(self, args, api, io: IO) -> Result:
        status = api.get(f"/api/alarms/{args['alarm_id']}/escalation")
        chain = " -> ".join(f"{a['method']} ({a['status']})" for a in status["attempts"]) or "none"
        return Result(
            status["attempts"],
            f"Alarm {status['alarm_id']} is {status['status']}. " f"Escalation chain: {chain}",
            columns=ESCALATION_COLUMNS,
            empty_message="No escalation attempts for this alarm.",
        )


class ListEscalationsCommand(Command):
    name = "escalation.list"

    def execute(self, args, api, io: IO) -> Result:
        return Result(
            api.get("/api/escalations"),
            columns=ESCALATION_COLUMNS,
            empty_message="No escalations yet.",
        )


class AcknowledgeEscalationCommand(Command):
    name = "escalation.ack"

    def execute(self, args, api, io: IO) -> Result:
        result = api.post("/api/escalations/acknowledge", {"via": "cli"})
        return Result(result, "Confirmed awake. Escalation stopped.")


COMMANDS = [
    AddAlarmCommand,
    ListAlarmsCommand,
    EditAlarmCommand,
    DeleteAlarmCommand,
    ToggleAlarmCommand,
    TriggerAlarmCommand,
    ChallengeCommand,
    VerifyAlarmCommand,
    SnoozeAlarmCommand,
    ConfirmCheckinCommand,
    ListCheckinsCommand,
    ExplainEscalationCommand,
    EscalationStatusCommand,
    ListEscalationsCommand,
    AcknowledgeEscalationCommand,
]
