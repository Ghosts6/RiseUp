"""CLI connection settings: flags > env vars > ~/.config/riseup/config.json profile."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path

DEFAULT_SERVER = "http://localhost:8000"
CONFIG_PATH = Path(
    os.environ.get("RISEUP_CONFIG", Path.home() / ".config" / "riseup" / "config.json")
)


@dataclass
class Connection:
    server: str
    token: str | None


def resolve_connection(server: str | None, token: str | None, profile: str | None) -> Connection:
    stored: dict = {}
    if CONFIG_PATH.exists():
        try:
            data = json.loads(CONFIG_PATH.read_text())
            stored = data.get("profiles", {}).get(profile or data.get("default", "default"), {})
        except (OSError, ValueError):
            stored = {}
    return Connection(
        server=server or os.environ.get("RISEUP_SERVER") or stored.get("server") or DEFAULT_SERVER,
        token=token or os.environ.get("RISEUP_TOKEN") or stored.get("token") or "dev-token",
    )
