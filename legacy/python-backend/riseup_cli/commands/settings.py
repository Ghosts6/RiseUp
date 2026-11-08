"""Preferences and emergency contact commands (F06)."""

from __future__ import annotations

from riseup_cli.commands.base import IO, Command, Result, UserInputError

PREF_FIELDS = [
    ("name", "Name"),
    ("phone", "Phone"),
    ("timezone", "Timezone"),
    ("enabled_channels", "Escalation channels"),
    ("escalation_order", "Escalation order"),
    ("notification_methods", "Reminder channels"),
    ("quiet_hours", "Quiet hours"),
    ("checkin_enabled", "Check-in"),
    ("checkin_delay_minutes", "Check-in delay (min)"),
    ("default_task_type", "Default task"),
    ("contacts", "Emergency contacts"),
    ("integrations", "Integrations"),
    ("llm", "LLM routing"),
]


def _split(value: str | None) -> list[str] | None:
    if value is None:
        return None
    return [v.strip() for v in value.split(",") if v.strip()]


def _flatten(payload: dict) -> dict:
    p = payload["preferences"]
    quiet = (
        f"{p['quiet_hours_start']}-{p['quiet_hours_end']}"
        if p.get("quiet_hours_start") and p.get("quiet_hours_end")
        else None
    )
    contacts = [f"{c['name']} {c['phone']} ({c['consent_status']})" for c in payload["contacts"]]
    return {
        **payload["user"],
        **p,
        "quiet_hours": quiet,
        "contacts": contacts,
        "integrations": payload["integrations"],
        "llm": payload["llm"],
    }


class ShowPrefsCommand(Command):
    name = "prefs.show"

    def execute(self, args, api, io: IO) -> Result:
        payload = api.get("/api/prefs")
        warnings = "\n".join(f"! {w}" for w in payload["warnings"])
        return Result(_flatten(payload), warnings, fields=PREF_FIELDS)


class SetPrefsCommand(Command):
    name = "prefs.set"

    def execute(self, args, api, io: IO) -> Result:
        body: dict = {}
        if args.get("channels") is not None:
            body["enabled_channels"] = _split(args["channels"])
        if args.get("order") is not None:
            body["escalation_order"] = _split(args["order"])
        if args.get("notify") is not None:
            body["notification_methods"] = _split(args["notify"])
        if args.get("quiet") is not None:
            if args["quiet"].lower() in ("off", "none", ""):
                body["quiet_hours_start"] = body["quiet_hours_end"] = None
            else:
                try:
                    start, end = args["quiet"].split("-")
                except ValueError as exc:
                    raise UserInputError("--quiet must look like 22:00-07:00 or 'off'") from exc
                body["quiet_hours_start"], body["quiet_hours_end"] = start.strip(), end.strip()
        for cli, api_key in (
            ("checkin", "checkin_enabled"),
            ("checkin_delay", "checkin_delay_minutes"),
            ("default_task", "default_task_type"),
            ("phone", "phone"),
            ("timezone", "timezone"),
            ("name", "name"),
            ("email", "email"),
        ):
            if args.get(cli) is not None:
                body[api_key] = args[cli]
        if not body:
            raise UserInputError("Nothing to change. Example: riseup prefs set --channels call,sms")
        payload = api.patch("/api/prefs", body)
        warnings = "\n".join(f"! {w}" for w in payload["warnings"])
        return Result(
            _flatten(payload), ("Preferences saved.\n" + warnings).strip(), fields=PREF_FIELDS
        )


class TestChannelCommand(Command):
    name = "prefs.test"

    def execute(self, args, api, io: IO) -> Result:
        result = api.post("/api/prefs/test", {"channel": args["channel"]})
        verdict = "sent" if result["success"] else "FAILED"
        return Result(result, f"Test {result['channel']} {verdict}: {result['detail']}")


class AddContactCommand(Command):
    name = "contact.add"

    def execute(self, args, api, io: IO) -> Result:
        contact = api.post("/api/contacts", {"name": args["name"], "phone": args["phone"]})
        return Result(
            contact,
            f"Contact #{contact['id']} added. A consent request was sent; "
            f"they are only used after confirming.\nConsent link: {contact['consent_link']}",
        )


class ListContactsCommand(Command):
    name = "contact.list"

    def execute(self, args, api, io: IO) -> Result:
        return Result(
            api.get("/api/contacts"),
            columns=[
                ("id", "ID"),
                ("name", "Name"),
                ("phone", "Phone"),
                ("consent_status", "Consent"),
            ],
            empty_message="No emergency contacts.",
        )


class RemoveContactCommand(Command):
    name = "contact.remove"

    def execute(self, args, api, io: IO) -> Result:
        api.delete(f"/api/contacts/{args['id']}")
        return Result({"deleted": args["id"]}, f"Contact {args['id']} removed.")


class TestContactCommand(Command):
    name = "contact.test"

    def execute(self, args, api, io: IO) -> Result:
        result = api.post(f"/api/contacts/{args['id']}/test")
        return Result(
            result,
            f"Test message {'sent' if result['success'] else 'FAILED'}: " f"{result['detail']}",
        )


COMMANDS = [
    ShowPrefsCommand,
    SetPrefsCommand,
    TestChannelCommand,
    AddContactCommand,
    ListContactsCommand,
    RemoveContactCommand,
    TestContactCommand,
]
