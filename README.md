# RiseUp

## Overview

RiseUp is an **intelligent alarm and task reminder system** powered by AI that helps users who struggle with waking up and managing tasks. Instead of easy-to-ignore alarms, RiseUp enforces accountability through verification challenges and adaptive escalation strategies that learn what works best for each user.

## Problem Statement

**The Challenge:**
- Millions of people struggle to wake up and dismiss alarms without actually getting out of bed
- Users often forget or ignore task reminders
- One-size-fits-all alarm strategies don't work for everyone
- No system adapts to individual preferences or learns what's most effective

**Why RiseUp?**
- An AI agent learns your patterns and decides the best way to wake you up
- Combines proven escalation tactics (tasks, calls, messages, smart home integration)
- Intelligent task prioritization so you focus on what matters
- Continuously improves through behavioral learning

## Key Features

### 🖥️ Interfaces: GUI + CLI
- **Web GUI:** React dashboard for alarms, tasks, insights, history, and preferences
- **CLI:** `riseup` commands (e.g. `riseup alarm add`, `riseup remind "…"`, `riseup stats`) over the same FastAPI backend

### 🚨 Intelligent Alarm System
- **Customizable Alarms:** Personalized sounds, vibration patterns, and intensity levels
- **Verification Tasks:** Users must complete a challenge to cancel the alarm
  - Object detection (take a photo to prove you're out of bed)
  - Puzzles and math problems
  - Trivia questions
  - Custom user tasks
- **Smart Check-in:** After completing a task, the system checks in after 5-10 minutes to verify you're actually awake

### 🤖 AI-Powered Escalation
- **Intelligent Decision Making:** If alarm isn't addressed, AI analyzes user history and preferences to decide the next action
- **Escalation Options:**
  - Phone call
  - SMS notification
  - Alexa alert
  - Contact emergency contact
- **Learning:** System remembers which escalation methods work best for each user

### ✅ Smart Task Reminders
- **Multiple Input Methods:** Create reminders via the app, CLI, or voice agent
  - Natural language: "Remind me to call mom tomorrow at 9am" (`riseup remind "…"`)
  - App interface: Set time, priority, category
- **AI Prioritization:** Smart reminder scheduling based on urgency and user behavior
- **Adaptive Timing:** Learn when users are most likely to act on reminders

### 📊 Behavioral Learning & Insights
- **Sleep Pattern Tracking:** Understand your sleep habits over time
- **Task Completion Analytics:** Track which reminders you complete and when
- **Escalation Effectiveness:** See which wake-up methods work best for you
- **Personalized Recommendations:** AI suggests optimal alarm and escalation settings

### 💻 Cross-Platform Web App
- **Responsive Design:** Works on desktop, tablet, and mobile
- **Cross-OS:** Runs on Windows, macOS, and Linux
- **Alarm Management:** Set, edit, and track alarms from anywhere
- **Task Dashboard:** View, create, and complete tasks
- **Settings & Preferences:** Configure notification methods and user preferences

---

## System Architecture

```mermaid
graph TB
    subgraph Client["Client Layer"]
        WEB["React + TypeScript GUI<br/>Vite"]
        CLI["CLI riseup<br/>Typer / Command pattern"]
    end

    subgraph Backend["Backend Layer"]
        API["FastAPI<br/>Python 3.10+"]
        AGENT["AgentController<br/>observe decide act"]
        ALARM["Alarm System<br/>APScheduler"]
        ESCALATION["Escalation Engine"]
        TASK["Task Manager<br/>Prioritization"]
        USER["User Profile<br/>Learning Engine"]
    end

    subgraph AI["AI and LLM Layer"]
        LLM["LLMClient<br/>LLMProvider"]
        CLAUDE["Claude<br/>claude-sonnet-4-5"]
        OPENAI["OpenAI<br/>gpt-4o"]
        PARSER["NLCommandParser"]
    end

    subgraph External["External Integrations"]
        TWILIO["Twilio<br/>Calls and SMS"]
        ALEXA["Amazon Alexa"]
        CAMERA["Camera API"]
        FIREBASE["Firebase Push"]
    end

    subgraph Data["Data Layer"]
        DB["PostgreSQL"]
        CACHE["Redis"]
    end

    WEB -->|HTTP/WebSocket| API
    CLI -->|HTTP| API
    API --> AGENT
    API --> ALARM
    API --> ESCALATION
    API --> TASK
    API --> USER
    API --> PARSER

    AGENT --> ESCALATION
    AGENT --> LLM
    ESCALATION --> LLM
    TASK --> LLM
    PARSER --> LLM
    LLM --> CLAUDE
    LLM --> OPENAI

    ESCALATION --> TWILIO
    ESCALATION --> ALEXA
    ESCALATION --> FIREBASE
    ALARM --> CAMERA
    ALARM --> TWILIO

    API --> DB
    API --> CACHE
    USER --> DB
    ALARM --> DB

    style Client fill:#e1f5ff
    style Backend fill:#f3e5f5
    style AI fill:#fff3e0
    style External fill:#e8f5e9
    style Data fill:#fce4ec
```

---

## Technology Stack

### 🖥️ Frontend

| Technology | Purpose | Version |
|-----------|---------|---------|
| ![React](https://img.shields.io/badge/-React-61DAFB?style=flat&logo=react&logoColor=white) | UI Framework | 18.2+ |
| ![TypeScript](https://img.shields.io/badge/-TypeScript-3178C6?style=flat&logo=typescript&logoColor=white) | Type Safety | 5.0+ |
| ![Vite](https://img.shields.io/badge/-Vite-646CFF?style=flat&logo=vite&logoColor=white) | Build Tool | 4.0+ |
| ![Tailwind CSS](https://img.shields.io/badge/-Tailwind%20CSS-06B6D4?style=flat&logo=tailwindcss&logoColor=white) | Styling | 3.0+ |
| ![Axios](https://img.shields.io/badge/-Axios-5A29E4?style=flat&logo=axios&logoColor=white) | HTTP Client | 1.4+ |

**Key Libraries:**
- `react-router-dom` — Routing
- `zustand` — State Management
- `react-hook-form` — Form Handling
- `zod` — Type Validation
- `recharts` — Insights charts

### ⚙️ Backend

| Technology | Purpose | Version |
|-----------|---------|---------|
| ![Python](https://img.shields.io/badge/-Python-3776AB?style=flat&logo=python&logoColor=white) | Language | 3.10+ |
| ![FastAPI](https://img.shields.io/badge/-FastAPI-009688?style=flat&logo=fastapi&logoColor=white) | Web Framework | 0.100+ |
| ![PostgreSQL](https://img.shields.io/badge/-PostgreSQL-336791?style=flat&logo=postgresql&logoColor=white) | Database | 12+ |
| ![SQLAlchemy](https://img.shields.io/badge/-SQLAlchemy-BA2131?style=flat&logo=sqlalchemy&logoColor=white) | ORM | 2.0+ |
| ![Redis](https://img.shields.io/badge/-Redis-DC382D?style=flat&logo=redis&logoColor=white) | Optional cache | 7.0+ |

**Key Libraries:**
- `uvicorn` — ASGI Server
- `pydantic` — Data Validation
- `alembic` — Database Migrations
- `apscheduler` — Alarm, check-in, escalation and reminder jobs (in-process; no Celery needed)
- `httpx` — HTTP client for Twilio / Alexa / Firebase / LLM REST APIs
- `typer` — CLI (`riseup` commands)
- `python-dotenv` — Environment Variables
- `pytest` — Testing

### 🤖 AI & LLM

RiseUp uses AI as an **agent** (`AgentController`: observe → decide → tool act → re-observe),
not a one-shot chatbot. Long-term memory is `UserProfile` + `EscalationLog`.

Both providers implement `LLMProvider` (Strategy); `LLMClient` selects the configured one.

| Provider | Model | Default role |
|----------|-------|----------------|
| **Anthropic Claude** | `claude-sonnet-4-5` | Escalation, prioritization, recommendations |
| **OpenAI** | `gpt-4o` | Natural-language reminder parsing |

#### AI Agent Behavior

- Interpret natural-language user requests
- Analyze preferences and history (memory)
- Prioritize tasks; decide escalation channels
- Execute tools (Twilio / Alexa / Firebase) and retry on failure
- Fall back to a deterministic escalation ladder if the LLM fails
- Generate personalized recommendations
- Store decision reasoning for explainability (F14)

### 🔗 External Integrations

| Service | Purpose | Integration |
|---------|---------|-------------|
| ![Twilio](https://img.shields.io/badge/-Twilio-F22F46?style=flat&logo=twilio&logoColor=white) | SMS & Phone Calls | REST API |
| ![Amazon Alexa](https://img.shields.io/badge/-Amazon%20Alexa-FF9900?style=flat&logo=amazon-alexa&logoColor=white) | Voice / Notify-Me alerts | REST API |
| ![Firebase](https://img.shields.io/badge/-Firebase-FFCA28?style=flat&logo=firebase&logoColor=white) | Push Notifications | Cloud Messaging |

### 🧪 Testing & Quality

| Tool | Purpose |
|------|---------|
| ![pytest](https://img.shields.io/badge/-pytest-0A9EDC?style=flat&logo=pytest&logoColor=white) | Backend unit, API, agent and CLI tests |
| ![TypeScript](https://img.shields.io/badge/-tsc-3178C6?style=flat&logo=typescript&logoColor=white) | Frontend type checking |
| ![Jest](https://img.shields.io/badge/-Jest-C21325?style=flat&logo=jest&logoColor=white) | Frontend unit tests (planned, stage 3) |
| ![Coverage.py](https://img.shields.io/badge/-Coverage.py-85C1E2?style=flat&logo=python&logoColor=white) | Code Coverage Analysis |

### 🚀 CI/CD & Deployment

| Tool | Purpose |
|------|---------|
| ![GitHub Actions](https://img.shields.io/badge/-GitHub%20Actions-2088FF?style=flat&logo=github-actions&logoColor=white) | CI/CD Pipeline |
| ![Docker](https://img.shields.io/badge/-Docker-2496ED?style=flat&logo=docker&logoColor=white) | Containerization |
| ![Docker Compose](https://img.shields.io/badge/-Docker%20Compose-2496ED?style=flat&logo=docker&logoColor=white) | Local Development |

---

## CI/CD Pipeline

```mermaid
graph LR
    PUSH["📤 Push to GitHub"] --> TRIGGER["⚡ Trigger Workflow"]
    
    TRIGGER --> CHECKOUT["🔄 Checkout Code"]

    CHECKOUT --> PY_DEPS
    CHECKOUT --> PG_DEPS
    CHECKOUT --> JS_DEPS

    subgraph Backend["⚙️ Backend (Python 3.11 + 3.12)"]
        PY_DEPS["📦 Install Dependencies"]
        PY_LINT["🔍 Flake8 + Black"]
        PY_TYPE["📝 mypy Type Check"]
        PY_MIG["🗄️ Alembic upgrade + check"]
        PY_TEST["🧪 pytest + Coverage"]
    end

    subgraph Postgres["🐘 Backend on PostgreSQL"]
        PG_DEPS["📦 Install Dependencies"]
        PG_MIG["🗄️ Migrations on Postgres 16"]
    end

    subgraph Frontend["🖥️ Frontend"]
        JS_DEPS["📦 npm install"]
        JS_BUILD["🔨 tsc + vite build"]
    end

    PY_DEPS --> PY_LINT --> PY_TYPE --> PY_MIG --> PY_TEST
    PG_DEPS --> PG_MIG
    JS_DEPS --> JS_BUILD

    PY_TEST --> SUCCESS["✅ All Checks Pass"]
    PG_MIG --> SUCCESS
    JS_BUILD --> SUCCESS

    style PUSH fill:#e3f2fd
    style Backend fill:#f3e5f5
    style Postgres fill:#e8eaf6
    style Frontend fill:#e1f5fe
    style SUCCESS fill:#c8e6c9
```

---

## Project Setup

### Prerequisites
- Python 3.10+
- Node.js 18+ and npm
- Git
- Optional: Docker (for PostgreSQL + Redis), API keys for Claude/OpenAI/Twilio/Alexa/Firebase

**No API keys are required.** Without keys, every external service runs as a deterministic
mock adapter (calls/SMS/Alexa/push are logged; a rule-based `MockProvider` answers LLM prompts),
so the full app works offline. Add a key to `.env` and that integration switches to the real API.
SQLite is the default database; set `DATABASE_URL` to use PostgreSQL.

### Installation

1. **Clone Repository**
   ```bash
   git clone https://github.com/Ghosts6/RiseUp.git
   cd RiseUp
   ```

2. **Setup Backend**
   ```bash
   cd backend
   python -m venv .venv
   source .venv/bin/activate        # Windows: .venv\Scripts\activate
   pip install -e ".[dev]"          # add ,postgres,redis for those backends
   cp .env.example .env             # optional: add API keys
   ```

3. **Setup Frontend**
   ```bash
   cd frontend
   npm install
   cp .env.example .env             # optional: VITE_API_URL / VITE_API_TOKEN
   ```

4. **Run Application**

   **Terminal 1 (Backend):**
   ```bash
   cd backend && riseup-server      # or: python app.py
   # API at http://localhost:8000, docs at http://localhost:8000/docs
   ```

   **Terminal 2 (Frontend):**
   ```bash
   cd frontend && npm run dev
   # GUI at http://localhost:5173
   ```

   **Terminal 3 (CLI):**
   ```bash
   riseup alarm add 07:00 --task math --repeat weekdays --label Work
   riseup alarm trigger 1           # ring now (demo) and show the challenge
   riseup alarm verify 1 --answer 42
   riseup remind "call mom tomorrow at 9am"
   riseup task list
   riseup stats --range 30d
   riseup history --since 7d
   riseup escalation explain 1      # why the agent chose a channel (F14)
   riseup --json alarm list         # machine-readable output
   ```
   CLI exit codes: `0` success, `1` invalid input / not found, `2` server unreachable or error.

   > Port 8000 is used instead of 5000 because macOS reserves 5000 for AirPlay.

### Running Tests

```bash
cd backend
pytest                               # 83 tests: unit + API + agent + CLI (~85% coverage)
flake8 riseup riseup_cli tests && black --check riseup riseup_cli tests && mypy riseup riseup_cli
cd ../frontend && npm run build      # type-check + production build
```

### Using Docker

```bash
docker compose up -d --build
# Frontend:   http://localhost:5173
# Backend:    http://localhost:8000  (runs `alembic upgrade head` on start)
# PostgreSQL and Redis run as internal services
```
API keys can be passed through the environment, e.g. `CLAUDE_API_KEY=... docker compose up -d`.

---

## Project Structure

```
riseup/
├── backend/
│   ├── app.py                      # Entry point (python app.py)
│   ├── pyproject.toml              # Package, deps, `riseup` + `riseup-server` scripts
│   ├── .env.example                # Environment template
│   ├── Dockerfile
│   ├── migrations/                 # Alembic migrations
│   ├── riseup/                     # FastAPI backend
│   │   ├── main.py, container.py   # App factory + composition root / unit of work
│   │   ├── models/, repositories/  # SQLAlchemy models and data access
│   │   ├── alarm_system/           # Alarms, verification strategies, check-in (F01-F03)
│   │   ├── agent/                  # AgentController + EscalationEngine (F04-F05)
│   │   ├── ai/                     # LLMClient, providers, prompts, response parser
│   │   ├── tools/                  # Twilio / Alexa / Firebase adapters + mocks
│   │   ├── task_manager/           # Reminders, NL parser, prioritizer (F07-F10)
│   │   ├── user_profile/           # Preferences, learning, analytics, recommendations
│   │   ├── history/                # History + decision explanations (F14)
│   │   ├── events/                 # Observer: event bus and observers
│   │   └── api/                    # Routers, schemas, serializers, WebSocket
│   ├── riseup_cli/                 # `riseup` CLI (Command pattern)
│   └── tests/                      # pytest suite
│
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── Dockerfile, nginx.conf
│   └── src/
│       ├── api/                    # Typed API client + types
│       ├── components/             # Layout + UI primitives
│       ├── hooks/                  # Data loading + live WebSocket events
│       ├── store/                  # zustand app store
│       ├── pages/                  # Dashboard, alarms, tasks, insights, history, settings
│       └── App.tsx
│
├── .github/
│   └── workflows/
│       └── ci.yml                  # CI/CD pipeline
│
├── docs/
│   ├── stage-1-design/             # Stage 1 documentation
│   │   ├── project-scope.md
│   │   ├── feature-specification.md
│   │   ├── use-cases.md
│   │   ├── design-decisions.md
│   │   ├── design-patterns.md
│   │   ├── feature-to-design-mapping.md
│   │   └── uml-diagrams/           # PNG of diagrams
│   ├── stage-2-implementation/     # Implementation notes + AI collaboration log
│   └── stage-3-testing/
├── docker-compose.yml              # Docker setup
├── .gitignore
└── README.md
```

---

### Stage 1 — Design & Architecture

The complete Stage 1 design documentation is available in:

**[`docs/stage-1-design/`](docs/stage-1-design/)**

It contains the project scope, use cases, design decisions, feature-to-design
traceability, and UML diagrams required for the Stage 1 design.

#### Stage 1 Report

All Stage 1 documents and UML diagrams combined into a single report:

- 📄 **[Stage 1 Report (PDF)](docs/stage-1-design/Stage-1-Report.pdf)**

#### Stage 1 Documentation

| Document | Description |
|----------|-------------|
| [`project-scope.md`](docs/stage-1-design/project-scope.md) | Problem, users, agent, models, architecture, F01–F14 summary |
| [`feature-specification.md`](docs/stage-1-design/feature-specification.md) | **Canonical** 14 features (8 fields each) + CLI design |
| [`use-cases.md`](docs/stage-1-design/use-cases.md) | UC-01–UC-09 with Related Feature(s) |
| [`design-decisions.md`](docs/stage-1-design/design-decisions.md) | Architecture / AI / UX decisions (incl. AgentController loop) |
| [`design-patterns.md`](docs/stage-1-design/design-patterns.md) | Five patterns with problem, classes, roles, rationale |
| [`feature-to-design-mapping.md`](docs/stage-1-design/feature-to-design-mapping.md) | Traceability table + Task 4 for all 14 features |

#### Stage 1 UML Diagrams

All diagrams (PNG): **[`docs/stage-1-design/uml-diagrams/`](docs/stage-1-design/uml-diagrams/)**

| Diagram | Description |
|---------|-------------|
| [`ArchitectureDiagram.png`](docs/stage-1-design/uml-diagrams/ArchitectureDiagram.png) | Overall system architecture |
| [`ClassDiagram.png`](docs/stage-1-design/uml-diagrams/ClassDiagram.png) | Classes incl. CLI, agent, LLM providers, tools |
| [`UseCaseDiagram.png`](docs/stage-1-design/uml-diagrams/UseCaseDiagram.png) | UC-01–UC-09 + Twilio/Alexa/Camera/LLM actors |
| [`SequenceDiagramAlarm.png`](docs/stage-1-design/uml-diagrams/SequenceDiagramAlarm.png) | **SD-01** Alarm / verify / check-in |
| [`SequenceDiagramAi.png`](docs/stage-1-design/uml-diagrams/SequenceDiagramAi.png) | **SD-02** Agent escalation loop |
| [`SequenceDiagramTaskReminder.png`](docs/stage-1-design/uml-diagrams/SequenceDiagramTaskReminder.png) | **SD-03** Reminders + prioritization |
| [`SequenceDiagramLearningEngine.png`](docs/stage-1-design/uml-diagrams/SequenceDiagramLearningEngine.png) | **SD-04** Learning |
| [`SequenceDiagramCliRemind.png`](docs/stage-1-design/uml-diagrams/SequenceDiagramCliRemind.png) | **SD-05** CLI / NL remind |
| [`SequenceDiagramRecommendations.png`](docs/stage-1-design/uml-diagrams/SequenceDiagramRecommendations.png) | **SD-06** Recommendations |
| [`SequenceDiagramHistoryExplain.png`](docs/stage-1-design/uml-diagrams/SequenceDiagramHistoryExplain.png) | **SD-07** History / explain |

---

## Project Stages

### 📋 Stage 1: Design & Architecture
- [x] Project scope and problem statement
- [x] Major features definition
- [x] Use cases documentation
- [x] UML diagrams (class, sequence, use-case)
- [x] Design mapping

### 🔨 Stage 2: AI-Human Collaborative Implementation
- [x] Backend development (FastAPI, F01–F14)
- [x] CLI (`riseup`, all commands from the feature specification)
- [x] Frontend development (React + TypeScript + Tailwind, live WebSocket updates)
- [x] API integrations (Twilio, Alexa, Firebase, Claude, OpenAI; offline mocks by default)
- [x] Database schema (SQLAlchemy + Alembic; SQLite or PostgreSQL)
- [x] Docker Compose + CI pipeline
- [x] AI collaboration logs

See **[`docs/stage-2-implementation/`](docs/stage-2-implementation/)** for the feature-to-code map
and the AI collaboration log.

### 🧪 Stage 3: Testing & Validation
- [ ] Unit tests (deterministic) (initial suite added in stage 2)
- [ ] Integration tests
- [ ] Agent behavioral tests (KUMA)
- [ ] Performance testing

---

## Configuration

### Environment Variables (.env)

The full list with defaults is in [`backend/.env.example`](backend/.env.example). The most
important ones:

```bash
# Database (default: sqlite:///./riseup.db)
DATABASE_URL=postgresql://user:password@localhost/riseup_db
REDIS_URL=redis://localhost:6379/0          # optional cache

# AI / LLM APIs (unset = offline MockProvider)
OPENAI_API_KEY=your_openai_key_here
CLAUDE_API_KEY=your_anthropic_key_here
MOCK_LLM_MODE=normal                        # normal | invalid_json | error | disabled_method

# Api keys
TWILIO_ACCOUNT_SID=your_sid
TWILIO_AUTH_TOKEN=your_token
TWILIO_PHONE_NUMBER=+1234567890
ALEXA_API_KEY=your_key
FIREBASE_API_KEY=your_key

# Application
DEBUG=False
SECRET_KEY=your_secret_key
ENVIRONMENT=development
```

---

## Contributing

This is a course project for EECS3311 (Software Design).

## License

MIT License - See [LICENSE](LICENSE) file for details

---

## Support

For questions or issues, please open a [GitHub Issue](https://github.com/Ghosts6/RiseUp/issues).

---

**Last Updated:** October 18th 2026  
**Project Status:** Stage 2 - Implementation (done)  
**Python:** 3.10+ | **Node:** 18+ | **PostgreSQL:** 12+ (optional)