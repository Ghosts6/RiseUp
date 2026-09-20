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
- **Multiple Input Methods:** Create reminders via the app or voice agent
  - Natural language: "Remind me to call mom tomorrow at 9am"
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
    subgraph Client["🖥️ Client Layer"]
        WEB["React + TypeScript<br/>Vite"]
        MOBILE["Responsive UI<br/>Tailwind CSS"]
    end

    subgraph Backend["⚙️ Backend Layer"]
        API["FastAPI<br/>Python 3.10+"]
        ALARM["Alarm System<br/>APScheduler"]
        ESCALATION["Escalation Engine<br/>AI Logic"]
        TASK["Task Manager<br/>Prioritization"]
        USER["User Profile<br/>Learning Engine"]
    end

    subgraph AI["🤖 AI & LLM Layer"]
        CLAUDE["Claude API<br/>Reasoning & Planning"]
        PARSER["NLP Parser<br/>Voice Commands"]
    end

    subgraph External["🔗 External Integrations"]
        TWILIO["📞 Twilio<br/>Calls & SMS"]
        ALEXA["🔊 Amazon Alexa<br/>Smart Alerts"]
        CAMERA["📷 Camera API<br/>Object Detection"]
    end

    subgraph Data["💾 Data Layer"]
        DB["PostgreSQL<br/>User Data & Logs"]
        CACHE["Redis<br/>Caching & Tasks"]
    end

    Client -->|HTTP/WebSocket| API
    API --> ALARM
    API --> ESCALATION
    API --> TASK
    API --> USER
    
    ESCALATION --> CLAUDE
    TASK --> CLAUDE
    USER --> CLAUDE
    API --> PARSER
    PARSER --> CLAUDE
    
    ALARM --> TWILIO
    ESCALATION --> TWILIO
    ESCALATION --> ALEXA
    ALARM --> CAMERA
    
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
- `zustand` or `redux` — State Management
- `react-hook-form` — Form Handling
- `zod` — Type Validation
- `jest` & `@testing-library/react` — Testing

### ⚙️ Backend

| Technology | Purpose | Version |
|-----------|---------|---------|
| ![Python](https://img.shields.io/badge/-Python-3776AB?style=flat&logo=python&logoColor=white) | Language | 3.10+ |
| ![FastAPI](https://img.shields.io/badge/-FastAPI-009688?style=flat&logo=fastapi&logoColor=white) | Web Framework | 0.100+ |
| ![PostgreSQL](https://img.shields.io/badge/-PostgreSQL-336791?style=flat&logo=postgresql&logoColor=white) | Database | 12+ |
| ![SQLAlchemy](https://img.shields.io/badge/-SQLAlchemy-BA2131?style=flat&logo=sqlalchemy&logoColor=white) | ORM | 2.0+ |
| ![Redis](https://img.shields.io/badge/-Redis-DC382D?style=flat&logo=redis&logoColor=white) | Caching & Queue | 7.0+ |

**Key Libraries:**
- `uvicorn` — ASGI Server
- `pydantic` — Data Validation
- `alembic` — Database Migrations
- `celery` — Task Queue
- `apscheduler` — Task Scheduling
- `httpx` — Async HTTP Client
- `python-dotenv` — Environment Variables
- `pytest` — Testing

### 🤖 AI & LLM

**To do**

### 🔗 External Integrations

| Service | Purpose | Integration |
|---------|---------|-------------|
| ![Twilio](https://img.shields.io/badge/-Twilio-F22F46?style=flat&logo=twilio&logoColor=white) | SMS & Phone Calls | REST API |
| ![Amazon Alexa](https://img.shields.io/badge/-Amazon%20Alexa-FF9900?style=flat&logo=amazon-alexa&logoColor=white) | Voice Alerts & Commands | Alexa SDK |
| ![Firebase](https://img.shields.io/badge/-Firebase-FFCA28?style=flat&logo=firebase&logoColor=white) | Push Notifications | Cloud Messaging |

### 🧪 Testing & Quality

| Tool | Purpose |
|------|---------|
| ![Jest](https://img.shields.io/badge/-Jest-C21325?style=flat&logo=jest&logoColor=white) | Frontend Unit Tests |
| ![Testing Library](https://img.shields.io/badge/-Testing%20Library-E33332?style=flat&logo=testing-library&logoColor=white) | React Component Tests |
| ![pytest](https://img.shields.io/badge/-pytest-0A9EDC?style=flat&logo=pytest&logoColor=white) | Backend Unit Tests |
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
    CHECKOUT --> LINT["🔍 Lint Code"]
    
    LINT --> BACKEND["Backend Pipeline"]
    LINT --> FRONTEND["Frontend Pipeline"]
    
    subgraph Backend["⚙️ Backend Checks"]
        PY_DEPS["📦 Install Dependencies"]
        PY_LINT["🔍 Black/Flake8 Lint"]
        PY_TYPE["📝 mypy Type Check"]
        PY_TEST["🧪 pytest Unit Tests"]
        PY_COV["📊 Coverage Report"]
    end
    
    subgraph Frontend["🖥️ Frontend Checks"]
        JS_DEPS["📦 Install Dependencies"]
        JS_LINT["🔍 ESLint/Prettier"]
        JS_TEST["🧪 Jest Unit Tests"]
        JS_BUILD["🔨 npm build"]
    end
    
    PY_DEPS --> PY_LINT
    PY_LINT --> PY_TYPE
    PY_TYPE --> PY_TEST
    PY_TEST --> PY_COV
    
    JS_DEPS --> JS_LINT
    JS_LINT --> JS_TEST
    JS_TEST --> JS_BUILD
    
    PY_COV --> SUCCESS["✅ All Tests Pass"]
    JS_BUILD --> SUCCESS
    
    SUCCESS --> DOCKER["🐳 Build Docker Image"]
    DOCKER --> DEPLOY["🚀 Deploy to Staging"]
    DEPLOY --> NOTIFY["📢 Notify Team"]

    style PUSH fill:#e3f2fd
    style Backend fill:#f3e5f5
    style Frontend fill:#e1f5fe
    style SUCCESS fill:#c8e6c9
    style DOCKER fill:#fff9c4
    style DEPLOY fill:#ffe0b2
```

---

## Project Setup

### Prerequisites
- Python 3.10+
- Node.js 16+ and npm
- PostgreSQL 12+
- Redis 7+
- Git

### Installation

1. **Clone Repository**
   ```bash
   git clone https://github.com/yourusername/riseup.git
   cd riseup
   ```

2. **Setup Backend**
   ```bash
   # Create virtual environment
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   
   # Install dependencies
   pip install -r requirements.txt
   
   # Setup environment
   cp .env.example .env
   # Edit .env with your API keys
   
   # Initialize database
   alembic upgrade head
   ```

3. **Setup Frontend**
   ```bash
   cd frontend
   npm install
   ```

4. **Run Application**
   
   **Terminal 1 (Backend):**
   ```bash
   python app.py
   # Backend at http://localhost:5000
   ```
   
   **Terminal 2 (Frontend):**
   ```bash
   cd frontend
   npm run dev
   # Frontend at http://localhost:5173
   ```

### Using Docker

```bash
docker-compose up -d
# Backend: http://localhost:5000
# Frontend: http://localhost:3000
# PostgreSQL: localhost:5432
# Redis: localhost:6379
```

---

## Project Structure

```
riseup/
├── backend/
│   ├── app.py                      # Main application
│   ├── requirements.txt            # Python dependencies
│   ├── .env.example                # Environment template
│   ├── config.py                   # Configuration
│   ├── alarm_system/               # Alarm logic
│   ├── escalation_engine/          # AI escalation
│   ├── task_manager/               # Task handling
│   ├── user_profile/               # User data & learning
│   └── tests/                      # Backend tests
│
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── src/
│   │   ├── components/             # React components
│   │   ├── pages/                  # Pages
│   │   ├── services/               # API calls
│   │   ├── styles/                 # Tailwind styles
│   │   └── App.tsx
│   └── tests/                      # Frontend tests
│
├── .github/
│   └── workflows/
│       └── ci.yml                  # CI/CD pipeline
│
├── docs/
│   └── stage-1-design/             # Stage 1 documentation
│       ├── 01-project-scope.md
│       ├── 02-use-cases.md
│       └── 03-uml-diagrams/
│
├── docker-compose.yml              # Docker setup
├── .gitignore
└── README.md                        # This file
```

---

## Project Stages

### 📋 Stage 1: Design & Architecture
- [x] Project scope and problem statement
- [x] Major features definition
- [ ] Use cases documentation
- [ ] UML diagrams (class, sequence, use-case)
- [ ] Design mapping

### 🔨 Stage 2: AI-Human Collaborative Implementation
- [ ] Backend development
- [ ] Frontend development
- [ ] API integrations
- [ ] Database schema
- [ ] AI collaboration logs

### 🧪 Stage 3: Testing & Validation
- [ ] Unit tests (deterministic)
- [ ] Integration tests
- [ ] Agent behavioral tests (KUMA)
- [ ] Performance testing

---

## Configuration

### Environment Variables (.env)

```bash
# Database
DATABASE_URL=postgresql://user:password@localhost/riseup_db

# API Keys
CLAUDE_API_KEY=your_key_here
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

For questions or issues, please open a [GitHub Issue](https://github.com/yourusername/riseup/issues).

---

**Last Updated:** September 2026  
**Project Status:** Stage 1 - Design Phase  
**Python:** 3.10+ | **Node:** 16+ | **PostgreSQL:** 12+