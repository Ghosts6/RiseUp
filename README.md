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

### API keys (local)

Edit `backend/.env` (never commit it) and replace the placeholders:

```bash
CLAUDE_API_KEY=sk-ant-...      # escalation, prioritization, recommendations, photo verification
OPENAI_API_KEY=sk-...          # reminder parsing, agent chat
```

`./gradlew :backend:bootRun` and Docker Compose both read `backend/.env`. Missing or placeholder keys fall back
to the mock provider, and `GET /api/health` shows which provider each feature uses. Per-feature routing:
`LLM_ESCALATION_PROVIDER`, `LLM_PARSE_PROVIDER`, `LLM_PRIORITIZE_PROVIDER`, `LLM_RECOMMEND_PROVIDER`,
`LLM_CHAT_PROVIDER`, `LLM_VISION_PROVIDER` (`claude` | `openai` | `mock`; vision needs `claude`).

### Standalone CLI (`java -jar`)

The CLI is a single runnable jar (all dependencies included). It talks to the backend over HTTP, so start the backend first (`./gradlew :backend:bootRun` or Docker Compose).

```bash
./gradlew :cli:jar                                   # builds cli/build/libs/cli-0.2.0.jar
java -jar cli/build/libs/cli-0.2.0.jar health        # check backend + which LLM each feature uses
alias riseup='java -jar cli/build/libs/cli-0.2.0.jar' # optional shortcut used below
```

**Run the agent on a task written in a file** (the LLM provider is chosen by the backend, see *API keys* above):

```bash
echo "Set an alarm labeled Study at 6:45 on weekdays with a trivia challenge, no snooze" > task.txt
java -jar cli/build/libs/cli-0.2.0.jar -y agent chat "$(cat task.txt)"
# agent> I can set that for 6:45 AM on weekdays with a trivia challenge and snooze disabled. ...   [llm:openai]
# Proposed action: create_alarm {"time":"06:45","label":"Study","repeat_pattern":"weekdays","verification_task_type":"trivia","snooze_enabled":false}
# Alarm set for 06:45.
```

Without `-y` the CLI asks before the agent creates anything. With no message (`riseup agent chat`) it starts an interactive session.

#### Global options (before the command)

| Option | Meaning | Default |
|---|---|---|
| `--server=<url>` | Backend URL | `$RISEUP_SERVER`, else `http://localhost:8000` |
| `--token=<token>` | API token | `$RISEUP_TOKEN` / `$DEMO_TOKEN`, else `dev-token` |
| `--json` | Machine-readable JSON output instead of tables | off |
| `-y`, `--yes` | Answer yes to confirmations (agent actions, parsed reminders, deletes) | off |
| `-h`, `--help` / `-V`, `--version` | Help (works on every command) / version | |

Exit codes: `0` success, `1` error (backend or network), `2` usage or validation error.

#### Commands

| Command | Options / arguments | Feature |
|---|---|---|
| `alarm add <HH:MM>` | `--label`, `--task math\|trivia\|object_detection\|custom`, `--custom-text`, `--repeat once\|daily\|weekdays\|weekends\|custom:mon,wed,fri`, `--[no-]snooze`, `--sound`, `--vibration` | F01 |
| `alarm list` · `alarm edit <id>` · `alarm delete <id>` · `alarm toggle <id>` | edit takes the same options as add plus `--time`; toggle takes `--on` / `--off` | F01 |
| `alarm trigger <id>` · `alarm challenge <id>` · `alarm snooze <id>` | | F02 |
| `alarm verify <id>` | `--answer <value>` (math/trivia) · `--done` (custom) · `--photo <file.jpg\|png>` (AI vision) | F02 |
| `checkin list` · `checkin confirm` | | F03 |
| `escalation list` · `escalation status <alarm-id>` · `escalation explain <id>` · `escalation ack` | | F04, F05, F14 |
| `prefs show` · `prefs set` · `prefs test <channel>` | set: `--name`, `--phone`, `--email`, `--timezone`, `--channels`, `--order`, `--notify`, `--quiet HH:MM-HH:MM`, `--[no-]checkin`, `--checkin-delay`, `--default-task` | F06 |
| `contact add <name> <phone>` · `contact list` · `contact remove <id>` · `contact test <id>` | | F06 |
| `remind "<sentence>"` | | F07 |
| `task add` · `task edit <id>` | `--title`, `--due` (`YYYY-MM-DD HH:MM`, `today 18:30`, `tomorrow 9am`, `in 30 minutes`), `--priority low\|medium\|high\|critical`, `--category`, `--repeat none\|daily\|weekly\|weekdays`, `--notify`, `--notes`; edit also `--scope this\|all` | F08 |
| `task list` | `--status`, `--category`, `--priority` | F08 |
| `task done <id>` · `task snooze <id> [minutes]` · `task delete <id>` | done: `--already`; snooze: `--minutes` | F08, F10 |
| `task priority-preview` | | F09 |
| `profile show` · `profile recompute` | | F11 |
| `stats` | `--range 7d\|30d\|90d` (default `30d`) | F12 |
| `recommend` | `--apply <n>` · `--dismiss <n>` | F13 |
| `history` | `--type alarm\|reminder\|escalation`, `--since 7d`, `--page <n>` | F14 |
| `agent chat ["message"]` · `agent today` | | F15, F16 |
| `health` | | |

#### More examples

```bash
riseup alarm add 07:30 --label Morning --task math --repeat weekdays
riseup alarm verify 3 --answer 42                 # math / trivia
riseup alarm verify 3 --photo ~/Desktop/mug.jpg   # photo challenge (AI vision)
riseup task add --title "Buy milk" --due "tomorrow 9am" --priority low
riseup remind "call mom tomorrow at 9am"          # natural language (AI parse, you confirm)
riseup task priority-preview                      # AI ranking of due tasks
riseup agent today                                # morning briefing
riseup --json stats --range 7d                    # JSON output for scripts
riseup history --type escalation --since 7d
```

Screenshots of every command in action are in [docs/stage-2-implementation/stage-2-docs.md](docs/stage-2-implementation/stage-2-docs.md) (§2.2).

You can also run the CLI without building the jar: `./gradlew :cli:run --args='health'`, or `./gradlew :cli:installDist` and then `cli/build/install/cli/bin/cli`.

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
