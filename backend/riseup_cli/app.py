"""CLIApp (Command invoker) and the Typer front-end that builds argument dicts."""

from __future__ import annotations

import sys
from typing import Any, Optional

import typer

from riseup_cli.api_client import APIClient, APIError
from riseup_cli.commands.base import IO, UserInputError
from riseup_cli.config import resolve_connection
from riseup_cli.formatters import JsonFormatter, OutputFormatter, TableFormatter
from riseup_cli.registry import CommandRegistry, build_default_registry

EXIT_OK, EXIT_USER, EXIT_SERVER = 0, 1, 2


class CLIApp:
    """Looks up a Command, runs it, formats the Result and maps errors to exit codes."""

    def __init__(
        self, registry: CommandRegistry, api: APIClient, formatter: OutputFormatter, io: IO
    ) -> None:
        self.registry = registry
        self.api = api
        self.formatter = formatter
        self.io = io

    def run(self, name: str, args: dict[str, Any]) -> int:
        try:
            result = self.registry.get(name).execute(args, self.api, self.io)
        except UserInputError as exc:
            self.io_err(f"Error: {exc}")
            return EXIT_USER
        except APIError as exc:
            self.io_err(f"Error: {exc.message}")
            return EXIT_USER if exc.is_user_error else EXIT_SERVER
        output = self.formatter.format(result)
        if output:
            self.io.echo(output)
        return result.exit_code

    def io_err(self, text: str) -> None:
        typer.echo(text, err=True)


app = typer.Typer(
    help="RiseUp: intelligent alarms and task reminders (CLI).",
    no_args_is_help=True,
    add_completion=False,
)
alarm_app = typer.Typer(help="Alarms, verification challenges (F01-F02)", no_args_is_help=True)
checkin_app = typer.Typer(help="Post-wake check-in (F03)", no_args_is_help=True)
escalation_app = typer.Typer(
    help="Escalation status and explanations (F04-F05, F14)", no_args_is_help=True
)
prefs_app = typer.Typer(help="Escalation preferences (F06)", no_args_is_help=True)
contact_app = typer.Typer(help="Emergency contact (F06)", no_args_is_help=True)
task_app = typer.Typer(help="Task reminders (F08-F10)", no_args_is_help=True)
profile_app = typer.Typer(help="Learned behaviour profile (F11)", no_args_is_help=True)
for sub, name in (
    (alarm_app, "alarm"),
    (checkin_app, "checkin"),
    (escalation_app, "escalation"),
    (prefs_app, "prefs"),
    (contact_app, "contact"),
    (task_app, "task"),
    (profile_app, "profile"),
):
    app.add_typer(sub, name=name)


@app.callback()
def main_options(
    ctx: typer.Context,
    json_output: bool = typer.Option(False, "--json", help="Machine-readable JSON output"),
    server: Optional[str] = typer.Option(None, "--server", help="Backend URL"),
    token: Optional[str] = typer.Option(None, "--token", help="API token"),
    profile: Optional[str] = typer.Option(None, "--profile", help="Config profile name"),
    yes: bool = typer.Option(False, "--yes", "-y", help="Answer yes to confirmations"),
) -> None:
    conn = resolve_connection(server, token, profile)
    ctx.obj = CLIApp(
        build_default_registry(),
        APIClient(conn.server, conn.token),
        JsonFormatter() if json_output else TableFormatter(),
        IO(assume_yes=yes, interactive=sys.stdin.isatty() and not json_output),
    )


def _run(ctx: typer.Context, command: str, /, **args: Any) -> None:
    code = ctx.obj.run(command, args)
    if code:
        raise typer.Exit(code)


# alarms -------------------------------------------------------------------
TaskOpt = typer.Option(None, "--task", help="math | trivia | object_detection | custom")
RepeatOpt = typer.Option(
    None, "--repeat", help="once | daily | weekdays | weekends | custom:mon,wed"
)


@alarm_app.command("add")
def alarm_add(
    ctx: typer.Context,
    time: str = typer.Argument(..., help="HH:MM"),
    label: Optional[str] = None,
    task: Optional[str] = TaskOpt,
    repeat: Optional[str] = RepeatOpt,
    snooze: Optional[bool] = typer.Option(None, "--snooze/--no-snooze"),
    sound: Optional[str] = None,
    vibration: Optional[str] = None,
    custom_text: Optional[str] = typer.Option(None, "--custom-text"),
):
    """Create an alarm, e.g. `riseup alarm add 07:00 --task math --repeat weekdays`."""
    _run(
        ctx,
        "alarm.add",
        time=time,
        label=label,
        task=task,
        repeat=repeat,
        snooze=snooze,
        sound=sound,
        vibration=vibration,
        custom_text=custom_text,
    )


@alarm_app.command("list")
def alarm_list(ctx: typer.Context):
    _run(ctx, "alarm.list")


@alarm_app.command("edit")
def alarm_edit(
    ctx: typer.Context,
    id: int,
    time: Optional[str] = None,
    label: Optional[str] = None,
    task: Optional[str] = TaskOpt,
    repeat: Optional[str] = RepeatOpt,
    snooze: Optional[bool] = typer.Option(None, "--snooze/--no-snooze"),
    sound: Optional[str] = None,
    vibration: Optional[str] = None,
    custom_text: Optional[str] = typer.Option(None, "--custom-text"),
):
    _run(
        ctx,
        "alarm.edit",
        id=id,
        time=time,
        label=label,
        task=task,
        repeat=repeat,
        snooze=snooze,
        sound=sound,
        vibration=vibration,
        custom_text=custom_text,
    )


@alarm_app.command("delete")
def alarm_delete(ctx: typer.Context, id: int):
    _run(ctx, "alarm.delete", id=id)


@alarm_app.command("toggle")
def alarm_toggle(
    ctx: typer.Context, id: int, active: Optional[bool] = typer.Option(None, "--on/--off")
):
    _run(ctx, "alarm.toggle", id=id, active=active)


@alarm_app.command("trigger")
def alarm_trigger(ctx: typer.Context, id: int):
    """Ring an alarm now (demo/testing)."""
    _run(ctx, "alarm.trigger", id=id)


@alarm_app.command("challenge")
def alarm_challenge(ctx: typer.Context, id: int):
    """Show the current verification challenge of a ringing alarm."""
    _run(ctx, "alarm.challenge", id=id)


@alarm_app.command("verify")
def alarm_verify(
    ctx: typer.Context,
    id: int,
    answer: Optional[str] = None,
    done: bool = typer.Option(False, "--done", help="Custom task completed"),
):
    """Answer the challenge, e.g. `riseup alarm verify 12 --answer 42`."""
    _run(ctx, "alarm.verify", id=id, answer=answer, done=done)


@alarm_app.command("snooze")
def alarm_snooze(ctx: typer.Context, id: int):
    _run(ctx, "alarm.snooze", id=id)


# check-in / escalation ------------------------------------------------------
@checkin_app.command("confirm")
def checkin_confirm(ctx: typer.Context):
    """Confirm you are still awake."""
    _run(ctx, "checkin.confirm")


@checkin_app.command("list")
def checkin_list(ctx: typer.Context):
    _run(ctx, "checkin.list")


@escalation_app.command("explain")
def escalation_explain(ctx: typer.Context, id: int):
    """Why did the agent pick this escalation?"""
    _run(ctx, "escalation.explain", id=id)


@escalation_app.command("status")
def escalation_status(ctx: typer.Context, alarm_id: int):
    _run(ctx, "escalation.status", alarm_id=alarm_id)


@escalation_app.command("list")
def escalation_list(ctx: typer.Context):
    _run(ctx, "escalation.list")


@escalation_app.command("ack")
def escalation_ack(ctx: typer.Context):
    """Confirm you're awake and stop the current escalation."""
    _run(ctx, "escalation.ack")


# preferences / contacts -------------------------------------------------------
@prefs_app.command("show")
def prefs_show(ctx: typer.Context):
    _run(ctx, "prefs.show")


@prefs_app.command("set")
def prefs_set(
    ctx: typer.Context,
    channels: Optional[str] = typer.Option(None, help="e.g. call,sms,alexa,push,emergency_contact"),
    order: Optional[str] = typer.Option(None, help="Fallback/preferred order, e.g. sms,call"),
    notify: Optional[str] = typer.Option(None, help="Reminder channels, e.g. push,sms"),
    quiet: Optional[str] = typer.Option(None, help="22:00-07:00 or off"),
    checkin: Optional[bool] = typer.Option(None, "--checkin/--no-checkin"),
    checkin_delay: Optional[int] = typer.Option(None, "--checkin-delay"),
    default_task: Optional[str] = typer.Option(None, "--default-task"),
    phone: Optional[str] = None,
    timezone: Optional[str] = None,
    name: Optional[str] = None,
    email: Optional[str] = None,
):
    _run(
        ctx,
        "prefs.set",
        channels=channels,
        order=order,
        notify=notify,
        quiet=quiet,
        checkin=checkin,
        checkin_delay=checkin_delay,
        default_task=default_task,
        phone=phone,
        timezone=timezone,
        name=name,
        email=email,
    )


@prefs_app.command("test")
def prefs_test(ctx: typer.Context, channel: str):
    """Send a test via call | sms | alexa | push."""
    _run(ctx, "prefs.test", channel=channel)


@contact_app.command("add")
def contact_add(ctx: typer.Context, name: str, phone: str):
    _run(ctx, "contact.add", name=name, phone=phone)


@contact_app.command("list")
def contact_list(ctx: typer.Context):
    _run(ctx, "contact.list")


@contact_app.command("remove")
def contact_remove(ctx: typer.Context, id: int):
    _run(ctx, "contact.remove", id=id)


@contact_app.command("test")
def contact_test(ctx: typer.Context, id: int):
    _run(ctx, "contact.test", id=id)


# reminders --------------------------------------------------------------------
@app.command("remind")
def remind(
    ctx: typer.Context, text: str = typer.Argument(..., help='e.g. "call mom tomorrow at 9am"')
):
    """Create a reminder from natural language (F07)."""
    _run(ctx, "remind", text=text)


DueOpt = typer.Option(None, "--due", help='"YYYY-MM-DD HH:MM" (your timezone)')


@task_app.command("add")
def task_add(
    ctx: typer.Context,
    title: Optional[str] = typer.Option(None, "--title"),
    due: Optional[str] = DueOpt,
    priority: Optional[str] = None,
    category: Optional[str] = None,
    repeat: Optional[str] = None,
    notify: Optional[str] = None,
    notes: Optional[str] = None,
    extra_time: Optional[list[str]] = typer.Option(
        None, "--extra-time", help='Additional reminder time, repeatable (e.g. "2026-10-20 17:00")'
    ),
):
    _run(
        ctx,
        "task.add",
        title=title,
        due=due,
        priority=priority,
        category=category,
        repeat=repeat,
        notify=notify,
        notes=notes,
        extra_times=extra_time,
    )


@task_app.command("list")
def task_list(
    ctx: typer.Context,
    status: Optional[str] = typer.Option(None, help="open | completed | missed | all"),
    category: Optional[str] = None,
    priority: Optional[str] = None,
):
    _run(ctx, "task.list", status=status, category=category, priority=priority)


@task_app.command("edit")
def task_edit(
    ctx: typer.Context,
    id: int,
    title: Optional[str] = None,
    due: Optional[str] = DueOpt,
    priority: Optional[str] = None,
    category: Optional[str] = None,
    repeat: Optional[str] = None,
    notify: Optional[str] = None,
    notes: Optional[str] = None,
    scope: str = typer.Option("this", help="Recurring: this | all"),
    extra_time: Optional[list[str]] = typer.Option(
        None, "--extra-time", help="Replace additional reminder times (repeatable)"
    ),
):
    _run(
        ctx,
        "task.edit",
        id=id,
        title=title,
        due=due,
        priority=priority,
        category=category,
        repeat=repeat,
        notify=notify,
        notes=notes,
        scope=scope,
        extra_times=extra_time,
    )


@task_app.command("done")
def task_done(
    ctx: typer.Context,
    id: int,
    already: bool = typer.Option(False, "--already", help="Was already done earlier"),
):
    _run(ctx, "task.done", id=id, already=already)


@task_app.command("snooze")
def task_snooze(ctx: typer.Context, id: int, minutes: Optional[int] = None):
    _run(ctx, "task.snooze", id=id, minutes=minutes)


@task_app.command("delete")
def task_delete(ctx: typer.Context, id: int):
    _run(ctx, "task.delete", id=id)


@task_app.command("priority-preview")
def task_priority_preview(ctx: typer.Context):
    """Show how the agent would order reminders due now (F09)."""
    _run(ctx, "task.priority-preview")


# insights -----------------------------------------------------------------------
@profile_app.command("show")
def profile_show(ctx: typer.Context):
    _run(ctx, "profile.show")


@profile_app.command("recompute")
def profile_recompute(ctx: typer.Context):
    _run(ctx, "profile.recompute")


@app.command("stats")
def stats(ctx: typer.Context, range: str = typer.Option("30d", "--range", help="7d | 30d | 90d")):
    """Sleep and completion analytics (F12)."""
    _run(ctx, "stats", range=range)


@app.command("recommend")
def recommend(
    ctx: typer.Context,
    apply: Optional[int] = typer.Option(None, "--apply"),
    dismiss: Optional[int] = typer.Option(None, "--dismiss"),
):
    """AI personalized recommendations (F13)."""
    _run(ctx, "recommend", apply=apply, dismiss=dismiss)


@app.command("history")
def history(
    ctx: typer.Context,
    type: Optional[str] = typer.Option(None, "--type"),
    since: Optional[str] = typer.Option(None, "--since", help="e.g. 7d"),
    page: int = typer.Option(1, "--page"),
):
    """Event history with AI decision explanations (F14)."""
    _run(ctx, "history", type=type, since=since, page=page)


def main() -> None:
    app()


if __name__ == "__main__":
    main()
