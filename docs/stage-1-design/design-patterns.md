# RiseUp - Design Patterns

Stage 1 requires at least **five** design patterns, each solving a real design problem.
RiseUp uses **five**. Patterns claimed elsewhere (Delegation, Repository as a “pattern credit,”
EscalationEngine-as-Facade, State) are **not** counted here — State is deferred until
`AlarmState` subclasses appear on the class diagram.

**Canonical patterns**

| # | Pattern | Where it appears |
|---|---------|------------------|
| 1 | Strategy | Verification tasks; LLM providers; CLI output formatters |
| 2 | Adapter | `LLMClient`, `TwilioClient`, `AlexaClient`, `FirebaseClient` |
| 3 | Command | CLI actions (`riseup …`) |
| 4 | Observer | Alarm / reminder / check-in status changes → UI & loggers |
| 5 | Factory Method | `VerificationTaskFactory`, `ReminderFactory` |

---

## 1. Strategy

### Design problem
Verification challenges differ (photo, math, trivia, custom) but the alarm flow must treat
them the same. LLM providers also differ (Claude vs OpenAI) while callers only need
“send prompt → get text.” CLI output can be a table or JSON.

### Participating classes and roles

| Class | Role |
|-------|------|
| `VerificationTask` | Strategy interface (`validate()`, `get_task_prompt()`) |
| `ObjectDetectionTask`, `MathTask`, `TriviaTask`, `CustomTask` | Concrete verification strategies |
| `LLMProvider` | Strategy interface (`complete(prompt) -> str`) |
| `ClaudeProvider`, `OpenAIProvider` | Concrete LLM strategies |
| `OutputFormatter` | Strategy interface for CLI formatting |
| `TableFormatter`, `JsonFormatter` | Concrete format strategies |
| `Alarm` / `CLIApp` / `LLMClient` | Context that selects and invokes a strategy |

### Why appropriate
Open/Closed: new task types, providers, or formats are added as new classes without editing
callers. Call sites stay polymorphic (`task.validate(response)`).

### Without the pattern
Large `if/elif` chains on task type / provider / `--json` flag, duplicated validation logic,
and every new variant requiring edits in multiple controllers.

---

## 2. Adapter

### Design problem
Twilio, Alexa, Firebase, and Anthropic/OpenAI each expose different SDKs and payload shapes.
Domain code must not depend on those vendor APIs.

### Participating classes and roles

| Class | Role |
|-------|------|
| `Tool` | Target interface used by the domain (`execute(payload) -> ToolResult`) |
| `TwilioClient` | Adapts Twilio REST API to `Tool` (call / SMS) |
| `AlexaClient` | Adapts Alexa notifications to `Tool` |
| `FirebaseClient` | Adapts FCM push to `Tool` |
| `LLMClient` | Adapts `LLMProvider` responses into domain DTOs via `ResponseParser` |
| `ToolManager` | Client that only talks to `Tool` / `LLMClient` |

### Why appropriate
Isolates vendor churn behind a stable interface; tests mock adapters instead of live APIs.

### Without the pattern
`EscalationEngine` and schedulers would call Twilio/Alexa/Anthropic SDKs directly, coupling
business logic to vendor libraries and making unit tests require network stubs everywhere.

---

## 3. Command

### Design problem
The CLI must expose many actions (`alarm add`, `remind`, `stats`, …) with uniform parsing,
exit codes, and testability, without growing a giant `if` in `main`.

### Participating classes and roles

| Class | Role |
|-------|------|
| `Command` | Command interface (`execute(args) -> Result`) |
| `AddAlarmCommand`, `RemindCommand`, `StatsCommand`, … | Concrete commands |
| `CommandRegistry` | Maps argv verb → `Command` |
| `CLIApp` | Invoker (parse argv, look up command, run, set exit code) |
| `APIClient` | Receiver helper (HTTP to FastAPI) |

### Why appropriate
Each CLI action is an object that can be unit-tested alone; new verbs register without
changing `CLIApp`.

### Without the pattern
One monolithic CLI switch statement, hard-to-test side effects, and tangled error handling.

---

## 4. Observer

### Design problem
When an alarm rings, a reminder is missed, or a check-in times out, several parts must react
(dashboard banner, history log, escalation trigger) without the domain objects knowing about UI.

### Participating classes and roles

| Class | Role |
|-------|------|
| `Alarm` / `Reminder` / `CheckIn` | Subjects that change status and notify |
| `ReminderObserver` (interface) | Observer contract (`on_status_changed(event)`) |
| `DashboardNotifier` | Updates GUI / push state |
| `ReminderLogger` / `EscalationLog` writer | Persists history |
| `EscalationEngine` (as listener on timeout) | Starts agent escalation when check-in fails |

### Why appropriate
Subjects emit events; observers subscribe. UI and logging can change without editing `Alarm`.

### Without the pattern
`Alarm.stop()` would hard-code UI updates, logging, and escalation calls (tight coupling,
harder to test, easy to miss a side effect when adding a new consumer).

---

## 5. Factory Method

### Design problem
Creating the right `VerificationTask` or `Reminder` depends on user prefs, AI-parsed fields,
or fallbacks. Callers should not `new` concrete classes with scattered construction rules.

### Participating classes and roles

| Class | Role |
|-------|------|
| `VerificationTaskFactory` | `create(task_type, prefs) -> VerificationTask` |
| `ReminderFactory` | `create(parsed_fields | form_dto) -> Reminder` |
| `Alarm` / `NLCommandParser` flow | Clients that ask factories for products |

### Why appropriate
Centralizes construction, defaults, and fallbacks (e.g. camera unavailable → `MathTask`).

### Without the pattern
Construction logic duplicated in GUI controllers, CLI commands, and schedulers; inconsistent
defaults and harder testing of creation rules.

---

## Patterns explicitly not claimed

| Name | Why not claimed |
|------|-----------------|
| Delegation | Useful technique inside `ToolManager`, but not used as a Stage 1 pattern credit |
| Repository | Repositories exist for persistence; not counted as one of the five course patterns |
| Facade on `EscalationEngine` | `EscalationEngine` is an **orchestrator** used by `AgentController`, not a thin Facade over a subsystem |
| State | Alarm lifecycle is real, but the class diagram does not yet show `AlarmState` subclasses; do not claim State until those classes are on the diagram |

`APIClient` may still *act* as a small Facade for HTTP/auth; it is not needed for the five-pattern minimum.
