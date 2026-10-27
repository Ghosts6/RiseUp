"""Build human-readable integration / LLM / runtime status for /api/health."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from riseup.cache import InMemoryCache, RedisCache
from riseup.config import Settings
from riseup.scheduling import APSJobScheduler, ManualJobScheduler

if TYPE_CHECKING:
    from riseup.container import Container


def _row(
    status: str, label: str, detail: str, **extra: Any
) -> dict[str, Any]:
    return {"status": status, "label": label, "detail": detail, **extra}


def tool_status(container: "Container") -> dict[str, dict[str, Any]]:
    s = container.settings
    mode = container.tool_manager.mode

    twilio_missing = [
        name
        for name, value in (
            ("TWILIO_ACCOUNT_SID", s.twilio_account_sid),
            ("TWILIO_AUTH_TOKEN", s.twilio_auth_token),
            ("TWILIO_PHONE_NUMBER", s.twilio_phone_number),
        )
        if not Settings.configured(value)
    ]
    alexa_ok = bool(Settings.configured(s.alexa_api_key))
    firebase_ok = bool(Settings.configured(s.firebase_api_key))
    smtp_ok = bool(Settings.configured(s.smtp_host))

    def from_mode(name: str, configured: bool, missing: list[str] | None = None) -> dict[str, Any]:
        live = mode.get(name) == "live"
        if live:
            return _row("live", "Live", "Configured and active", mode="live", configured=True)
        if missing:
            return _row(
                "not_configured",
                "Not configured",
                f"Missing {', '.join(missing)} using mock",
                mode="mock",
                configured=False,
                missing=missing,
            )
        if not configured:
            return _row(
                "not_configured",
                "Not configured",
                "No API key, using mock",
                mode="mock",
                configured=False,
            )
        return _row("mock", "Mock", "Using offline mock", mode="mock", configured=False)

    return {
        "twilio": from_mode("twilio", not twilio_missing, twilio_missing or None),
        "alexa": from_mode("alexa", alexa_ok),
        "firebase": from_mode("firebase", firebase_ok),
        "email": from_mode("email", smtp_ok),
    }


def llm_status(container: "Container") -> dict[str, dict[str, Any]]:
    client = container.llm_client
    rows: dict[str, dict[str, Any]] = {}
    for task, requested in client.routing.items():
        provider = client.provider_for(task)
        active = provider.name
        label = provider.label
        if active == "mock" and requested != "mock":
            rows[task] = _row(
                "fallback_mock",
                "Mock (fallback)",
                f"Requested {requested} but its key is missing",
                provider=active,
                model=provider.model,
                requested=requested,
                configured=False,
            )
        elif active == "mock":
            rows[task] = _row(
                "mock",
                "Mock",
                "Offline mock provider",
                provider=active,
                model=provider.model,
                requested=requested,
                configured=False,
            )
        else:
            rows[task] = _row(
                "live",
                "Live",
                f"Using {label}",
                provider=active,
                model=provider.model,
                requested=requested,
                configured=True,
            )
    return rows


def runtime_status(container: "Container", version: str = "") -> dict[str, dict[str, Any]]:
    cache = container.cache
    if isinstance(cache, RedisCache):
        cache_row = _row("live", "Redis", "Connected", backend="redis")
    elif isinstance(cache, InMemoryCache):
        cache_row = _row("local", "In-memory", "Process-local cache", backend="memory")
    else:
        cache_row = _row("unknown", "Unknown", type(cache).__name__)

    sched = container.scheduler
    if isinstance(sched, ManualJobScheduler):
        sched_row = _row("test", "Manual", "Test/manual scheduler")
    elif isinstance(sched, APSJobScheduler):
        running = bool(getattr(sched, "_scheduler", None) and sched._scheduler.running)
        if container.settings.enable_scheduler and running:
            sched_row = _row("running", "Running", "APScheduler active")
        elif container.settings.enable_scheduler:
            sched_row = _row("stopped", "Stopped", "Scheduler enabled but not started")
        else:
            sched_row = _row("disabled", "Disabled", "ENABLE_SCHEDULER=false")
    else:
        sched_row = _row("unknown", "Unknown", type(sched).__name__)

    db_url = container.settings.database_url
    if "postgres" in db_url:
        db_row = _row("live", "PostgreSQL", "Connected")
    elif db_url.startswith("sqlite"):
        db_row = _row("local", "SQLite", "Local database file")
    else:
        db_row = _row("live", "Database", "Connected")

    return {
        "api": _row("running", "Running", f"{container.settings.environment} · v{version}"),
        "database": db_row,
        "cache": cache_row,
        "scheduler": sched_row,
    }


def health_payload(container: "Container", version: str) -> dict[str, Any]:
    tools = tool_status(container)
    llm = llm_status(container)
    # Compact maps kept for CLI / older clients
    integrations = {name: row["mode"] for name, row in tools.items()}
    llm_labels = {
        task: f"{row['provider']}:{row['model']}" if row.get("provider") else row["label"]
        for task, row in llm.items()
    }
    services = runtime_status(container, version)
    return {
        "status": "ok",
        "version": version,
        "environment": container.settings.environment,
        "integrations": integrations,
        "llm": llm_labels,
        "services": services,
        "integrations_detail": tools,
        "llm_detail": llm,
    }
