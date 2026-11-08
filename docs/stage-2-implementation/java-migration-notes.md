# Stage 2 design change: Java migration

## Summary

RiseUp Stage 2 implementation language/stack was changed from **Python (FastAPI) + React** to **Java 21 (Spring Boot) + Picocli CLI + JavaFX GUI**.

Course requirements (GUI + CLI, ≥10 features, ≥5 design patterns, LLM/agent behavior) are unchanged. This document records the Stage 1 → Stage 2 design change for the Stage 2 report.

## Why

- Unified language across server, CLI, and GUI for course deliverables and pattern visibility in one ecosystem.
- Spring Boot / JPA / Flyway map cleanly to the existing layered architecture (AD-01).
- JavaFX provides a full-Java desktop GUI including photo verification capture.

## Mapping

| Stage 1 choice | Stage 2 choice |
|----------------|----------------|
| FastAPI + Uvicorn | Spring Boot 3 Web + WebSocket |
| SQLAlchemy + Alembic | JPA/Hibernate + Flyway |
| APScheduler | Spring `@Scheduled` + job registry |
| Typer CLI | Picocli + Command pattern |
| React + Vite GUI | JavaFX desktop GUI |
| pytest | JUnit 5 + MockMvc |

## Patterns retained

1. **Strategy** — verification tasks, `LLMProvider`, CLI formatters  
2. **Adapter** — Twilio / Alexa / Firebase / LLM as `Tool`  
3. **Command** — CLI commands + registry  
4. **Observer** — `EventBus` → dashboard / logger / escalation / stats  
5. **Factory Method** — `VerificationTaskFactory`, `ReminderFactory`

## Compatibility

- REST paths remain under `/api/*`; live events on `/ws`.
- Env var names for API keys match the previous `.env.example` (placeholder → mock).
- Legacy Python/React sources are kept under `legacy/` on this branch for reference until cutover is accepted.
