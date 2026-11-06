# RiseUp

Intelligent alarm and task reminder system — **Java 21+/23** stack with Spring Boot API, Picocli CLI, and JavaFX GUI.

## Architecture

- **Backend:** Spring Boot 3 (REST + WebSocket), JPA, Flyway, scheduled jobs
- **CLI:** `riseup` (Picocli + Command pattern) over HTTP
- **GUI:** JavaFX desktop client (alarms, verification/photo, tasks, insights, settings)
- **Data:** H2 (local default) or PostgreSQL; optional Redis
- **AI / tools:** Claude & OpenAI providers with mock fallbacks; Twilio / Alexa / Firebase adapters with mocks when keys are placeholders

## Quick start

```bash
# ./gradlew auto-selects JDK 23/21 if your default java is 25+ (Homebrew 27 breaks Gradle).
# Optional explicit pin:
#   export JAVA_HOME=$(/usr/libexec/java_home -v 23)

cp backend/.env.example backend/.env   # once
./gradlew :backend:bootRun            # API :8000
./gradlew :cli:run --args='health'    # CLI
./gradlew :gui:run                    # JavaFX GUI (needs display)
```

API default: `http://localhost:8000` · demo token: `dev-token`  
There is no separate React frontend on this branch — GUI is the JavaFX module.

### Docker

```bash
cp backend/.env.example backend/.env
# If Postgres was started with a failed Flyway migrate, reset volumes once:
docker compose down -v
docker compose up --build
```

Compose runs Postgres, Redis, and the Spring Boot backend on port 8000. Run CLI/GUI locally against that API.

## Design patterns

1. **Strategy** — verification tasks, LLM providers, CLI formatters  
2. **Adapter** — Twilio / Alexa / Firebase / LLM tools  
3. **Command** — CLI command registry  
4. **Observer** — EventBus → dashboard / logs / escalation / stats  
5. **Factory Method** — verification & reminder factories  

See [docs/stage-2-implementation/java-migration-notes.md](docs/stage-2-implementation/java-migration-notes.md).

## Tests

```bash
./gradlew :backend:test :cli:compileJava :gui:compileJava
```

## Legacy reference

Python/FastAPI + React sources (pre-migration) live under [`legacy/`](legacy/) on branch `migrate/java` for comparison only.
