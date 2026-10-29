"""Application settings loaded from environment variables / `.env`."""

from __future__ import annotations

from functools import lru_cache

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"), env_file_encoding="utf-8", extra="ignore"
    )

    # Core
    environment: str = "development"
    debug: bool = False
    secret_key: str = "change-me"
    database_url: str = "sqlite:///./riseup.db"
    redis_url: str | None = None
    auto_create_tables: bool = True
    enable_scheduler: bool = True
    host: str = "0.0.0.0"
    port: int = 8000
    public_base_url: str = "http://localhost:8000"
    cors_origins: str = "http://localhost:5173,http://localhost:3000"
    # Any port on localhost / 127.0.0.1 / [::1]; set to "" to allow only `cors_origins`.
    cors_origin_regex: str = r"^https?://(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$"

    # Auth (single demo user; login is not a graded feature)
    demo_token: str = "dev-token"
    require_auth: bool = False

    # LLM
    openai_api_key: str | None = None
    claude_api_key: str | None = Field(
        default=None, validation_alias=AliasChoices("CLAUDE_API_KEY", "ANTHROPIC_API_KEY")
    )
    claude_model: str = "claude-sonnet-4-5"
    openai_model: str = "gpt-4o"
    llm_escalation_provider: str = "claude"
    llm_parse_provider: str = "openai"
    llm_prioritize_provider: str = "claude"
    llm_recommend_provider: str = "claude"
    llm_timeout_seconds: float = 20.0
    llm_max_retries: int = 1
    mock_llm_mode: str = "normal"

    # External tools
    twilio_account_sid: str | None = None
    twilio_auth_token: str | None = None
    twilio_phone_number: str | None = None
    alexa_api_key: str | None = None
    alexa_notify_url: str = "https://api.notifymyecho.com/v1/NotifyMe"
    firebase_api_key: str | None = None
    smtp_host: str | None = None
    smtp_port: int = 587
    smtp_username: str | None = None
    smtp_password: str | None = None
    smtp_from: str | None = None
    smtp_use_tls: bool = True
    mock_tool_failures: str = ""

    # Timing
    verification_timeout_seconds: int = 120
    snooze_minutes: int = 10
    max_snoozes: int = 3
    checkin_response_minutes: int = 5
    # Spec F05: wait 30s after a failed call before trying the next channel.
    escalation_response_seconds: int = 30
    emergency_contact_attempts: int = 3
    reminder_escalation_minutes: int = 5
    reminder_final_minutes: int = 15
    reminder_missed_minutes: int = 25
    reminder_snooze_minutes: int = 15
    reminder_retry_minutes: int = 2
    learning_cron_hour: int = 3
    camera_available: bool = True
    max_alarms_per_user: int = 20

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @property
    def mock_tool_failure_set(self) -> set[str]:
        return {c.strip() for c in self.mock_tool_failures.split(",") if c.strip()}

    @staticmethod
    def configured(value: str | None) -> str | None:
        """Treat blank / YOUR_*_HERE placeholders as unset so mocks stay active."""
        if value is None:
            return None
        text = value.strip()
        if not text:
            return None
        upper = text.upper()
        if upper.startswith("YOUR_") or upper.endswith("_HERE") or upper == "CHANGE-ME":
            return None
        return text


@lru_cache
def get_settings() -> Settings:
    return Settings()
