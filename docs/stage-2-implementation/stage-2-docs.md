# RiseUp — Stage 2 Implementation Docs

**Course:** EECS3311 · **Period:** 20 Oct – 10 Nov 2026  
**Repository:** [https://github.com/Ghosts6/RiseUp](https://github.com/Ghosts6/RiseUp)  
**Branches:** `main` (Python/FastAPI + React baseline) · `migrate/java` (Java delivery stack)

This document is the working Stage 2 write-up for the requirements in `Project-Stage2-Instruction.pdf`. It feeds the final `stage2-report` PDF and sits beside [java-migration-notes.md](./java-migration-notes.md).

---

## 1. Project overview and implementation status

### Application

**RiseUp** is an intelligent alarm and reminder system. Users manage alarms with verification challenges, post-wake check-ins, multi-channel escalation when they miss wake-up, natural-language reminders, prioritization, behavioral learning, analytics, and recommendations.

**Users:** individuals who need reliable wake-up and task follow-through (personal productivity / accountability).

### AI / LLM integration (in-product)

| Role | Providers | Behavior |
|------|-----------|----------|
| Escalation decision (F04) | Claude / OpenAI (+ mock fallback) | Structured JSON decision; rule ladder if API fails |
| NL reminder parse (F07) | Claude / OpenAI (+ mock) | Extract title/time/priority; confirm before save |
| Reminder prioritization (F09) | Claude / OpenAI (+ mock) | Ordered priorities with reasoning |
| Recommendations (F13) | Claude / OpenAI (+ mock) | Personalized suggestions from profile/history |
| Agent chat / briefing (polish) | Same providers + tool loop | Multi-step tool use over domain APIs |

Keys live in local `backend/.env` (never committed). Placeholders → mock mode. See README for setup.

### Current stack (submitted direction)

| Layer | Implementation |
|-------|------------------|
| API | Spring Boot 3, JPA, Flyway, WebSocket `/ws` |
| CLI | Picocli `riseup` over HTTP (Command pattern) |
| GUI | JavaFX desktop (alarms, ringing/verify, tasks, insights, settings, agent) |
| Runtime | Docker Compose: Postgres + Redis + backend · or `./gradlew :backend:bootRun` (H2 default) |

Legacy Python FastAPI + React sources remain under `legacy/` on `migrate/java` for traceability.

### Working functionality (honest status)

**Working / demoable:** F01–F14 core flows via API + CLI + JavaFX; Docker health; LLM live or mock; verification strategies including math/trivia/custom/photo path; escalation with tool adapters; prefs/contacts; NL remind; stats/history/recommendations; Observer event bus; JUnit coverage on backend.

**Polish / gap-closing (Category 3, largely uncommitted at doc time):** JavaFX UI/UX upgrades, richer agent chat + tools, schedule rehydrate, series verification, Flyway Postgres type fixes, `gradlew` JDK auto-select for Homebrew OpenJDK 25+, history/dashboard/settings refinements. These make the product reliable under real run/test — not a second feature set.

**Limitations:** External Twilio/Alexa/Firebase/email stay mock until real keys are configured; photo verification needs a camera-capable host; GUI is desktop JavaFX (React web UI is legacy only); demo videos and final PDF report are separate deliverables indexed in §7.

---

## 2. Feature → implementation mapping

Stage 1 proposed **F01–F14**. All are implemented on the Java stack (paths below). Legacy Python counterparts live under `legacy/` with the same IDs.

| Feature | Stage 1 | Status | Primary classes / files | Patterns | Type |
|---------|---------|--------|-------------------------|----------|------|
| **F01** Alarm Management | Original | Implemented (GUI/CLI/API) | `AlarmControllerService`, `AlarmEntity`, `AlarmRepository`, GUI `DashboardPage` / ringing, CLI `alarm *` | Factory (schedule), Observer | Deterministic |
| **F02** Verification Challenge | Original | Implemented | `VerificationTask` + `MathTask` / `TriviaTask` / `CustomTask` / object-detection, `VerificationTaskFactory`, `AlarmRingingPage` | Strategy, Factory Method | Deterministic (vision library / rules, not LLM) |
| **F03** Post-Wake Check-in | Original | Implemented | `CheckInService`, check-in API, GUI `CheckInPage`, CLI `checkin *` | Observer | Deterministic |
| **F04** AI Escalation Decision | Original | Implemented | `AgentController` / escalation engine, `PromptBuilder`, `LLMClient`, `ResponseParser`, `LLMProvider` | Strategy (provider), Adapter | AI / Hybrid |
| **F05** Multi-Channel Escalation | Original | Implemented | `ToolManager`, `TwilioClient`, `AlexaClient`, `FirebaseClient` (+ mocks) | Adapter | Deterministic execution |
| **F06** Contacts & Prefs | Original | Implemented | prefs/contact services + entities, GUI `SettingsPage`, CLI `prefs` / `contact` | — | Deterministic |
| **F07** NL Reminder Creation | Original | Implemented | `NlParser`, LLM parse path, GUI tasks/agent, CLI `remind` | Strategy, Factory | Hybrid |
| **F08** Reminder Management | Original | Implemented | reminder services/repos, GUI `TasksPage`, CLI `task *` | Factory Method | Deterministic |
| **F09** AI Prioritization | Original | Implemented | `ReminderPrioritizer`, LLM path | Strategy | AI / Hybrid |
| **F10** Reminder Escalation Levels | Original | Implemented | reminder escalation fields + delivery/retry paths | Adapter (channels) | Deterministic |
| **F11** Behavioral Learning | Original | Implemented | `LearningService`, profile entities, nightly job (polish) | Observer (events → learning) | Deterministic |
| **F12** Analytics Dashboard | Original | Implemented | insights/stats services, GUI `InsightsPage`, CLI `stats` | Observer | Deterministic |
| **F13** AI Recommendations | Original | Implemented | `RecommendationService`, GUI `RecommendationsPage`, CLI `recommend` | Strategy | AI |
| **F14** History & Explain | Original | Implemented | history/escalation explain APIs, GUI `HistoryPage`, CLI `history` / `escalation explain` | — | Hybrid |

**Collaboration sketch (typical AI feature):** GUI/CLI → REST → domain service → `PromptBuilder` → `LLMClient` → `LLMProvider` (Claude/OpenAI/Mock) → `ResponseParser` → validate → persist / execute tools → `EventBus` notifies UI via WebSocket.

Video timestamps: fill in §7 when recordings are published.

---

## 3. Implemented design patterns (≥5)

Unchanged from Stage 1 intent; Java names below. Detail and rationale: Stage 1 [design-patterns.md](../stage-1-design/design-patterns.md) + [java-migration-notes.md](./java-migration-notes.md).

| # | Pattern | Design problem | Key participants (Java) |
|---|---------|----------------|-------------------------|
| 1 | **Strategy** | Swap verification types / LLM vendors / CLI format without branching callers | `VerificationTask` + concretes; `LLMProvider` + Claude/OpenAI/Mock; CLI formatters |
| 2 | **Adapter** | Isolate Twilio/Alexa/Firebase/LLM HTTP shapes from domain | `Tool` + clients; `LLMClient` wrapping providers |
| 3 | **Command** | Uniform CLI verbs, args, exit codes, testability | `Command` / `CommandRegistry` / `ConcreteCommands` + Picocli `RiseUpCli` |
| 4 | **Observer** | Decouple alarm/check-in/escalation events from UI & logging | `EventBus` → dashboard / logger / escalation / stats listeners; GUI `WsClient` |
| 5 | **Factory Method** | Create verification tasks & reminders without leaking constructors | `VerificationTaskFactory`, `ReminderFactory` |

UML: reuse/update Stage 1 class diagrams for the Java package layout (`com.riseup.*`). Sequence diagrams for F04/F07 should show `LLMClient` + fallback.

---

## 4. Changes and rationale

### Architecture (major)

| Original (Stage 1 / early Stage 2) | Implemented | Reason | Impact |
|------------------------------------|-------------|--------|--------|
| FastAPI + SQLAlchemy + Alembic | Spring Boot + JPA + Flyway | Single language for API/CLI/GUI; course-friendly pattern visibility | Same `/api/*` + `/ws` contracts; legacy kept under `legacy/` |
| Typer CLI | Picocli + Command registry | Preserve Command pattern explicitly | Same verb surface (`riseup alarm|task|…`) |
| React + Vite GUI | JavaFX desktop GUI | Full-Java deliverable; camera verify on desktop | Web UI archived; launch via `./gradlew :gui:run` |
| pytest | JUnit 5 + MockMvc | Match Java toolchain | CI runs Gradle tests |

Documented formally in [java-migration-notes.md](./java-migration-notes.md).

### Feature-level adjustments (examples)

| Original → change | Reason | Impact |
|-------------------|--------|--------|
| Web-only ringing audio → JavaFX sound + legacy Web Audio | Platform change | Same UX goal on desktop |
| Compose env → `env_file` + placeholder-as-unset | Real keys in Docker without baking secrets | Safer local/prod-like runs |
| Postgres Flyway `CLOB`/`DOUBLE` → `TEXT` / `DOUBLE PRECISION` | Dialect correctness | Backend stays healthy in Compose |
| Agent chat / briefing / series tasks (Category 3) | Stronger “meaningful agent” demo + verification depth | Additive; does not replace F01–F14 |

No Stage 1 feature ID was silently dropped; stack replacement is the main explained change.

---

## 5. AI tools and collaboration log

Using AI development tools was **mandatory**. Humans remained responsible for review, integration, secrets hygiene, and verification.

### 5.1 Development agents (authoring code)

| Tool / platform | Model(s) | Used for | Cost notes |
|-----------------|----------|----------|------------|
| **Cursor** (primary IDE agent) | **Auto** (default — let Cursor choose the model) for most turns | Scaffolding, multi-file refactors, Docker/Flyway fixes, GUI polish, CLI wiring, debugging build/runtime | Covered mainly by **Cursor Pro monthly plan**; **on-demand usage &lt; ~$1** |
| **Cursor** (task-typed routing) | **GPT Sol** (GPT Sol family in Cursor) | Broader implementation / planning-heavy coding passes | Included in Cursor routing above |
| **Cursor** | **Claude Sonnet** | Careful refactors, design-sensitive edits, longer reasoning on architecture | Included in Cursor routing above |
| **Cursor** | **Kimi K3** | Alternate coding passes / complementary tasks when routing by task type | Included in Cursor routing above |

**Practice:** Prefer Auto for day-to-day work; switch among GPT Sol / Claude Sonnet / Kimi K3 when a task benefits from a different strength (throughput vs careful edit vs alternate solution style). All AI output was reviewed before commit; secrets stayed in `.env` / `.gitignore`.

### 5.2 In-product LLM providers (runtime)

| Provider | Account / spend so far | Measured development & test usage | Role in app |
|----------|------------------------|-----------------------------------|-------------|
| **OpenAI** | ~**$5** loaded on the account | ~**$0.68** used for test runs / development calls | Parse, and configured feature routes |
| **Anthropic Claude** | ~**$5** loaded on the account | ~**$2.10** used for development / feature calls | Escalation, prioritize, recommend (`claude-sonnet-5`, configurable) |

Mock providers keep CI and demos working without keys.

### 5.3 Cost summary (approx.)

| Category | Amount (USD) |
|----------|--------------|
| OpenAI account top-up (capacity) | ~5.00 |
| Claude account top-up (capacity) | ~5.00 |
| OpenAI API burn (dev/test) | ~0.68 |
| Claude API burn (dev) | ~2.10 |
| Cursor Pro | Monthly subscription (primary) |
| Cursor on-demand beyond plan | &lt; ~1.00 |
| **Notable variable burn (API + Cursor on-demand)** | **≈ $3.78** (0.68 + 2.10 + &lt;1) |

Token totals vary by request; dollar figures above are the practical accounting used for this report.

### 5.4 Representative collaboration pattern

1. Human states feature or failure (e.g. Compose backend crash, `:gui:run` “27”).  
2. Cursor agent inspects logs/code, proposes patch (Flyway types, JDK pin in `gradlew`, Picocli `health`).  
3. Human runs Docker / Gradle / CLI smoke tests.  
4. Iterate until healthy; commit when ready.

---

## 6. Development and verification summary

### 6.1 Commit categories

Work is grouped into **three categories**. Categories 1–2 are pushed. Category 3 is the post-migration polish/gap-fix set (UI/UX, runtime fixes) described as uncommitted/extra work after the Java recipe commits.

#### Category 1 — Pre-Java Stage 2 (Python FastAPI + React)

**Range:** `6f74ee7` → `7fd90b3` (≈10 commits on the implementation path before the Java migration branch tip of this era)

| Commit | Summary |
|--------|---------|
| [`6f74ee7`](https://github.com/Ghosts6/RiseUp/commit/6f74ee7) | **Major.** Stage 2 backend core for F01–F14 (FastAPI): alarms, verification, check-in, escalation agent, reminders, learning, analytics, tool mocks. |
| [`53f05c8`](https://github.com/Ghosts6/RiseUp/commit/53f05c8) | **Major.** `riseup` CLI (Command pattern) + pytest suite covering unit/API/agent/CLI. |
| [`f852886`](https://github.com/Ghosts6/RiseUp/commit/f852886) | **Major.** React frontend: dashboard, alarms, tasks, insights, history, settings, ringing; dark mode + responsive layout. |
| [`238eb0b`](https://github.com/Ghosts6/RiseUp/commit/238eb0b) | **Major.** Docker Compose (Postgres/Redis/backend/frontend) + GitHub Actions CI. |
| [`00f80aa`](https://github.com/Ghosts6/RiseUp/commit/00f80aa) | README updates for Stage 2 run paths. |
| [`1fe144b`](https://github.com/Ghosts6/RiseUp/commit/1fe144b) | Branding: favicon, logo, footer. |
| [`a287bfd`](https://github.com/Ghosts6/RiseUp/commit/a287bfd) | Theme toggle UX (sun/moon). |
| [`8db2d9f`](https://github.com/Ghosts6/RiseUp/commit/8db2d9f) | Docker `env_file` + System status UX for live integrations. |
| [`3795f76`](https://github.com/Ghosts6/RiseUp/commit/3795f76) | Alarm sound + vibration on ringing page. |
| [`7fd90b3`](https://github.com/Ghosts6/RiseUp/commit/7fd90b3) | **Major.** Close Stage 2 gaps: object-detection hardening, escalation timing, SMTP reminder mail, Alexa NL + Tasks form fallback. |

#### Category 2 — Java migration (`migrate/java`)

**Range:** `71c628a` → `e7dde77` (10 planned migration commits)

| Commit | Summary |
|--------|---------|
| [`71c628a`](https://github.com/Ghosts6/RiseUp/commit/71c628a) | **Major.** Gradle multi-module scaffold; archive Python/React under `legacy/`. |
| [`7c1fff4`](https://github.com/Ghosts6/RiseUp/commit/7c1fff4) | **Major.** Spring domain model, repositories, Flyway V1 schema. |
| [`5f63a2c`](https://github.com/Ghosts6/RiseUp/commit/5f63a2c) | Verification strategies + tool adapters with mocks. |
| [`9294f71`](https://github.com/Ghosts6/RiseUp/commit/9294f71) | Alarm lifecycle, check-in, Observer `EventBus`. |
| [`f18f708`](https://github.com/Ghosts6/RiseUp/commit/f18f708) | **Major.** LLM providers, agent controller, escalation engine. |
| [`99ed64b`](https://github.com/Ghosts6/RiseUp/commit/99ed64b) | Reminders, prefs, insights, REST + WebSocket API surface. |
| [`46823cf`](https://github.com/Ghosts6/RiseUp/commit/46823cf) | **Major.** Picocli CLI + Command pattern over HTTP. |
| [`4077f54`](https://github.com/Ghosts6/RiseUp/commit/4077f54) | **Major.** JavaFX GUI (alarms, tasks, insights, settings, …). |
| [`e7dde77`](https://github.com/Ghosts6/RiseUp/commit/e7dde77) | **Major.** JUnit coverage, Java Docker/CI, migration docs. |

*(Intermediate commits in this range follow the same recipe order; all are meaningful code progress on the Java cutover.)*

#### Category 3 — Polishing, UI/UX, gap fixes (post-migration)

**Nature:** Uncommitted / follow-on work after Category 2 pushes — make the Java system **actually healthy** under Docker + local Gradle, and improve GUI/CLI UX after hands-on testing.

Themes (illustrative; land as commits before final submit):

- **Runtime / Docker:** Flyway Postgres types (`TEXT`, `DOUBLE PRECISION`); image healthchecks; `gradlew` auto-pick JDK 23/21 when system Java is 25+.
- **CLI:** Wire missing Picocli verbs (e.g. `health`); smoke against live `/api`.
- **GUI/UX:** Theme, icons, layout CSS, richer History/Dashboard/Settings/Tasks/Agent pages; WebSocket client robustness.
- **Agent depth:** `AgentChatService` / tools / briefing; series verification; schedule rehydrate; learning nightly job.
- **Config:** `.env.example` / `application.yml` alignment for providers and mocks.

### 6.2 Weekly commit summary (Stage 2 instruction periods)

Dates in **America/Toronto**. Each period needs ≥5 meaningful code commits.

| Period | Dates | Example commits (links) | Count (code-focused) |
|--------|-------|-------------------------|----------------------|
| **1** | 20–26 Oct 2026 | [`53f05c8`](https://github.com/Ghosts6/RiseUp/commit/53f05c8) CLI+tests · [`f852886`](https://github.com/Ghosts6/RiseUp/commit/f852886) React UI · [`238eb0b`](https://github.com/Ghosts6/RiseUp/commit/238eb0b) Compose/CI · [`1fe144b`](https://github.com/Ghosts6/RiseUp/commit/1fe144b) branding · [`a287bfd`](https://github.com/Ghosts6/RiseUp/commit/a287bfd) theme | ≥5 |
| **2** | 27 Oct – 2 Nov 2026 | [`8db2d9f`](https://github.com/Ghosts6/RiseUp/commit/8db2d9f) · [`3795f76`](https://github.com/Ghosts6/RiseUp/commit/3795f76) · [`7fd90b3`](https://github.com/Ghosts6/RiseUp/commit/7fd90b3) · [`71c628a`](https://github.com/Ghosts6/RiseUp/commit/71c628a) · [`7c1fff4`](https://github.com/Ghosts6/RiseUp/commit/7c1fff4) · [`5f63a2c`](https://github.com/Ghosts6/RiseUp/commit/5f63a2c) | ≥5 |
| **3** | 3–9 Nov 2026 | [`9294f71`](https://github.com/Ghosts6/RiseUp/commit/9294f71) · [`f18f708`](https://github.com/Ghosts6/RiseUp/commit/f18f708) · [`99ed64b`](https://github.com/Ghosts6/RiseUp/commit/99ed64b) · [`46823cf`](https://github.com/Ghosts6/RiseUp/commit/46823cf) · [`4077f54`](https://github.com/Ghosts6/RiseUp/commit/4077f54) · [`e7dde77`](https://github.com/Ghosts6/RiseUp/commit/e7dde77) (+ Category 3 polish commits as pushed) | ≥5 |

Note: [`6f74ee7`](https://github.com/Ghosts6/RiseUp/commit/6f74ee7) (18 Oct) is foundational Category 1 work **before** Period 1; it still documents implementation progress but is outside the first counted window.

### 6.3 Verification performed

- `docker compose up --build` → postgres / redis / backend **healthy**; `GET /api/health` → `status: ok`.
- `./gradlew :cli:run --args='health'` (and alarm/task smokes) against Docker API.
- `./gradlew :gui:run` launches JavaFX (Gradle stays on `:gui:run` while the window is open — expected).
- `./gradlew :backend:test` (and compile of cli/gui) during migration.
- Defects found and fixed examples: invalid Flyway types on Postgres; Gradle failure on JDK 27 (“27”); CLI `health` registered but not exposed to Picocli; Compose without `env_file` hiding real keys.

### 6.4 Remaining before hard submit

- Commit/push Category 3 polish in Period 3.  
- Export this material into `stage2-report` PDF/Word.  
- Record GUI + CLI demo videos; fill §7 timestamps.  
- Re-check `.gitignore` / `.env.example` — no real keys in history.

---

## 7. Demonstration index

| Feature | GUI segment | CLI segment | Notes |
|---------|-------------|-------------|-------|
| F01 | _TBD_ | `riseup alarm add\|list\|…` | |
| F02 | _TBD_ ringing + verify | `alarm challenge\|verify` | Photo GUI-only |
| F03 | _TBD_ | `checkin confirm\|list` | |
| F04–F05 | _TBD_ escalation UI | `escalation explain\|status\|list` | Show mock vs live tools |
| F06 | Settings | `prefs` / `contact` | |
| F07–F08 | Tasks / Agent | `remind` / `task *` | |
| F09–F10 | Tasks / Insights | `task priority-preview` | |
| F11–F13 | Insights / Recommendations | `profile` / `stats` / `recommend` | |
| F14 | History | `history` / `escalation explain` | |
| Cross-cut | Agent page | `health` | Meaningful agent + ops |

Collectively show **both GUI and CLI**. Do not display API keys on camera.

---

## Submission checklist (from instructions)

- [x] Source: GUI + CLI + AI integration (Java modules; legacy archived)  
- [x] README with prerequisites, `.env` copy, launch commands  
- [x] Build files + Compose  
- [ ] Final `stage2-report` PDF (derive from this doc)  
- [ ] Feature videos + links in §7  
- [x] `.gitignore` + `.env.example` (verify no secrets)  
- [ ] Public repo URL submitted in course system  

---

## Related docs

- [java-migration-notes.md](./java-migration-notes.md) — Stage 1 → Java design change  
- [../stage-1-design/feature-specification.md](../stage-1-design/feature-specification.md) — F01–F14 specs  
- [../stage-1-design/design-patterns.md](../stage-1-design/design-patterns.md) — pattern write-ups  
- Root [README.md](../../README.md) — how to run  
