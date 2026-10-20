"""OutputFormatter strategies selected by `--json`."""

from __future__ import annotations

import json
import re
from abc import ABC, abstractmethod
from datetime import datetime
from typing import Any

from riseup_cli.commands.base import Result


class OutputFormatter(ABC):
    @abstractmethod
    def format(self, result: Result) -> str: ...


ISO_UTC = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$")


def _cell(value: Any) -> str:
    if value is None:
        return "-"
    if isinstance(value, str) and ISO_UTC.match(value):
        moment = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return moment.astimezone().strftime("%Y-%m-%d %H:%M")
    if isinstance(value, bool):
        return "yes" if value else "no"
    if isinstance(value, float):
        return f"{value:.2f}"
    if isinstance(value, (list, tuple)):
        return ", ".join(_cell(v) for v in value) or "-"
    if isinstance(value, dict):
        return json.dumps(value, default=str)
    return str(value)


class TableFormatter(OutputFormatter):
    def format(self, result: Result) -> str:
        lines: list[str] = []
        if result.title:
            lines.append(result.title)
        data = result.data
        if isinstance(data, list) and result.columns:
            if not data:
                lines.append(result.empty_message)
            else:
                headers = [h for _, h in result.columns]
                rows = [[_cell(row.get(k)) for k, _ in result.columns] for row in data]
                widths = [max(len(h), *(len(r[i]) for r in rows)) for i, h in enumerate(headers)]
                widths = [min(w, 60) for w in widths]
                lines.append("  ".join(h.ljust(w) for h, w in zip(headers, widths)))
                lines.append("  ".join("-" * w for w in widths))
                for row in rows:
                    lines.append("  ".join(c[:w].ljust(w) for c, w in zip(row, widths)))
        elif isinstance(data, dict) and result.fields:
            width = max(len(label) for _, label in result.fields)
            for key, label in result.fields:
                lines.append(f"  {label.ljust(width)} : {_cell(data.get(key))}")
        if result.message:
            lines.append(result.message)
        return "\n".join(lines).rstrip()


class JsonFormatter(OutputFormatter):
    def format(self, result: Result) -> str:
        payload = result.data if result.data is not None else {"message": result.message}
        return json.dumps(payload, indent=2, default=str)
