# RiseUp - Project Scope & Description

Canonical feature IDs are **F01–F14** in [`feature-specification.md`](./feature-specification.md).
This document answers Stage 1 Task 1.1 (problem, users, agent, models, architecture).

---

## 1. Problem Statement

### What problem does RiseUp solve?
People dismiss alarms without getting up, ignore check-ins, and miss task reminders.
One-size-fits-all alarms and static reminders do not adapt to what actually works for each person.

### Who are the target users?
- People who struggle to wake (ADHD, depression, sleep issues)
- Students with irregular schedules
- Professionals with critical morning routines
- Anyone who needs accountable task reminders

### Why an AI agent is appropriate
Waking and reminding require **multi-step decisions under uncertainty**: choose a channel,
try it, observe the outcome, try another if it failed, and learn over time. That is agent
behavior (observe → decide → tool use → re-observe), not a single chat completion.

---

## 2. Solution: RiseUp Agent

RiseUp is an intelligent alarm and task-reminder system with:

- **GUI:** React web app for alarms, tasks, insights, history, preferences
- **CLI:** `riseup` commands over the same FastAPI backend (major functionality)
- **Agent:** `AgentController` loop with LLM decisions and tool execution
- **Memory:** `UserProfile` + `EscalationLog` (and reminder logs)
- **Tools:** Twilio (call/SMS), Alexa, Firebase push, camera/object-detection for verification

### What the agent can do
- Interpret natural-language reminder requests
- Decide escalation method when an alarm/check-in is ignored
- Rank simultaneous reminders
- Propose personalized setting changes from learned stats
- Recover when a tool fails by choosing a fallback (or the deterministic ladder)

### AI / LLM models
| Provider | Model | Default use |
|----------|-------|-------------|
| Anthropic | `claude-sonnet-4-5` | Escalation, recommendations, prioritization |
| OpenAI | `gpt-4o` | Natural-language reminder parsing |

Both implement `LLMProvider` (Strategy). `LLMClient` selects the configured provider.

### How the AI interacts with the rest of the system
```
GUI / CLI → Controllers → AgentController / domain services
                              │
                              ├─ PromptBuilder → LLMClient → LLMProvider
                              ├─ ResponseParser (validate JSON)
                              ├─ ToolManager → Tool adapters (Twilio, Alexa, Firebase)
                              └─ UserProfile / EscalationLog (memory)
```
Deterministic parts (scheduling, math/trivia validation, DB, tool HTTP calls) never depend
on the LLM succeeding; AI failure triggers documented fallbacks.

---

## 3. Assumptions & Constraints

### Assumptions
- Users have internet connectivity
- Users want to wake up (not coercive)
- At least one escalation channel is enabled
- Platform: web + CLI (Windows, macOS, Linux)

### Constraints
- Minimal personal data; explainable AI decisions (F14)
- Escalation SMS/calls must respect consent / quiet hours
- Emergency contact only after explicit consent (F06)

---

## 4. Main Features (F01–F14)

Detail (inputs, outputs, workflows, errors) lives in `feature-specification.md`. Summary:

| ID | Feature | Type |
|----|---------|------|
| F01 | Alarm Management | Deterministic |
| F02 | Verification Challenge | Deterministic |
| F03 | Post-Wake Check-in | Deterministic |
| F04 | AI Escalation Decision | AI / Hybrid |
| F05 | Multi-Channel Escalation Execution | Deterministic |
| F06 | Emergency Contact & Escalation Preferences | Deterministic |
| F07 | Natural-Language Reminder Creation | Hybrid |
| F08 | Reminder Management | Deterministic |
| F09 | AI Reminder Prioritization | AI / Hybrid |
| F10 | Reminder Escalation Levels | Deterministic |
| F11 | Behavioral Learning & Profile Update | Deterministic |
| F12 | Sleep & Completion Analytics Dashboard | Deterministic |
| F13 | AI Personalized Recommendations | AI |
| F14 | History & Decision Explanation | Hybrid |

Not counted as features: login/logout, generic settings shell, “having a web app,” bare scheduler infra.

---

## 5. Overall Architecture

```
React GUI ─┐
           ├─► FastAPI (controllers/services) ─► PostgreSQL / Redis
CLI ───────┘              │
                          ├─ AgentController / EscalationEngine / TaskPrioritizer
                          ├─ LLMClient (Claude / OpenAI)
                          └─ ToolManager (Twilio, Alexa, Firebase)
```

UML: [`uml-diagrams/`](./uml-diagrams/) — class, use-case, sequence (SD-01…SD-07).
Patterns: [`design-patterns.md`](./design-patterns.md).

---

## 6. Out of Scope (Stage 1–2)

- Wearables / biometrics
- Google/Outlook calendar sync
- Multi-user family accounts
- Training a custom ML model (use hosted LLMs)

---

## 7. Success Criteria

- Alarm cannot be dismissed without verification; check-in verifies wakefulness
- Escalation is agent-driven with deterministic fallback
- CLI and GUI both reach major features
- Learning updates profiles used by later decisions
- Decisions are explainable in history (F14)
- Deterministic components unit-testable; agent behavior testable later with KUMA
