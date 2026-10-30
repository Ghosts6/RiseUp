"""Profile, stats, recommendations and history commands (F11-F14)."""

from __future__ import annotations

from riseup_cli.commands.base import IO, Command, Result, UserInputError


def _pct(value) -> str:
    return f"{value:.0%}" if isinstance(value, (int, float)) else "-"


class ShowProfileCommand(Command):
    name = "profile.show"

    def execute(self, args, api, io: IO) -> Result:
        p = api.get("/api/profile")
        lines = [f"Data status: {p['data_status']} (updated {p['updated_at']})"]
        opt = p["optimal_settings"]
        lines.append(
            f"Best escalation: {opt.get('best_escalation_method') or '-'} | "
            f"Fastest task: {opt.get('fastest_task_type') or '-'} | "
            f"Avg wake: {opt.get('avg_wake_time') or '-'}"
        )
        for method, s in p["escalation_stats"].items():
            lines.append(
                f"  {method:<18} success {_pct(s.get('success_rate'))} "
                f"({s.get('successes', 0)}/{s.get('attempts', 0)})"
            )
        for category, s in p["task_completion_rates"].items():
            lines.append(
                f"  tasks:{category:<12} completed {_pct(s.get('rate'))} "
                f"({s.get('completed', 0)}/{s.get('total', 0)})"
            )
        return Result(p, "\n".join(lines))


class RecomputeProfileCommand(ShowProfileCommand):
    name = "profile.recompute"

    def execute(self, args, api, io: IO) -> Result:
        api.post("/api/profile/recompute")
        result = super().execute(args, api, io)
        result.message = "Profile recomputed.\n" + result.message
        return result


class StatsCommand(Command):
    name = "stats"

    def execute(self, args, api, io: IO) -> Result:
        s = api.get("/api/stats", range=args.get("range") or "30d")
        if s["empty"]:
            return Result(s, f"No data in the last {s['range_days']} days yet.")
        v = s["verification"]
        rows = [
            ("Range", f"last {s['range_days']} days"),
            ("Average wake time", s["avg_wake_time"] or "-"),
            (
                "Alarms rang / verified / missed",
                f"{v['triggered']} / {v['verified']} / {v['missed']}",
            ),
            ("Verification success", _pct(v["success_rate"])),
            ("Fastest task type", v["fastest_task_type"] or "-"),
        ]
        for method, e in s["escalation"].items():
            rows.append((f"Escalation: {method}", f"{_pct(e['success_rate'])} of {e['attempts']}"))
        for category, t in s["tasks"].items():
            rows.append(
                (f"Tasks: {category}", f"{_pct(t['rate'])} ({t['completed']}/{t['total']})")
            )
        width = max(len(k) for k, _ in rows)
        return Result(s, "\n".join(f"  {k.ljust(width)} : {val}" for k, val in rows))


class RecommendCommand(Command):
    name = "recommend"

    COLUMNS = [("id", "#"), ("title", "Suggestion"), ("rationale", "Why"), ("source", "Source")]

    def execute(self, args, api, io: IO) -> Result:
        if args.get("apply") is not None:
            rec = api.post(f"/api/recommendations/{args['apply']}/accept")
            return Result(rec, f"Applied: {rec['title']}")
        if args.get("dismiss") is not None:
            rec = api.post(f"/api/recommendations/{args['dismiss']}/dismiss")
            return Result(rec, f"Dismissed: {rec['title']}")
        result = api.post("/api/recommendations/generate")
        message = result.get("message") or ""
        if result["items"]:
            message = (message + "\nApply one with: riseup recommend --apply <#>").strip()
        return Result(
            result["items"],
            message,
            columns=self.COLUMNS,
            empty_message="No recommendations right now.",
        )


class HistoryCommand(Command):
    name = "history"

    def execute(self, args, api, io: IO) -> Result:
        if args.get("type") and args["type"] not in (
            "alarm",
            "escalation",
            "reminder",
            "checkin",
            "prioritization",
        ):
            raise UserInputError(
                "--type must be alarm, escalation, reminder, checkin, or prioritization"
            )
        page = api.get(
            "/api/history",
            type=args.get("type"),
            since=args.get("since"),
            page=args.get("page") or 1,
        )
        hint = (
            f"Page {page['page']}/{page['pages']} ({page['total']} events). "
            "Explain an AI decision: riseup escalation explain <ref>"
        )
        return Result(
            page["items"],
            hint,
            columns=[
                ("at", "When"),
                ("kind", "Type"),
                ("ref_id", "Ref"),
                ("title", "Event"),
                ("summary", "Details"),
            ],
            empty_message="No history for this filter.",
        )


COMMANDS = [
    ShowProfileCommand,
    RecomputeProfileCommand,
    StatsCommand,
    RecommendCommand,
    HistoryCommand,
]
