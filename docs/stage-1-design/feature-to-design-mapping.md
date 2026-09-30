# RiseUp - Feature-to-Design Traceability

Canonical features: **F01–F14** in [`feature-specification.md`](./feature-specification.md).
Patterns: [`design-patterns.md`](./design-patterns.md). Sequence IDs: see table below.

## Sequence diagram ID map

| ID | File | Covers |
|----|------|--------|
| SD-01 | `SequenceDiagramAlarm.png` | F01–F03 alarm, verify, check-in |
| SD-02 | `SequenceDiagramAi.png` | F04–F05 agent escalation loop |
| SD-03 | `SequenceDiagramTaskReminder.png` | F08–F10 reminders + prioritization |
| SD-04 | `SequenceDiagramLearningEngine.png` | F11 learning |
| SD-05 | `SequenceDiagramCliRemind.png` | F07 CLI/NL remind (GUI same backend path) |
| SD-06 | `SequenceDiagramRecommendations.png` | F13 recommendations |
| SD-07 | `SequenceDiagramHistoryExplain.png` | F14 history / explain |
| — | Use F06 with UC-08 prefs UI (deterministic; no dedicated SD beyond prefs save) | F06 |
| — | F12 uses analytics query path (shown conceptually with F11/F13 data) | F12 → treat SD-04 + dashboard |

F06 and F12 are deterministic CRUD/query flows; their collaboration is described in Task 4 below and uses the same controller → repository pattern as F01/F08. Graders can treat SD-01/SD-04 as the structural template.

---

## Feature-to-Design Traceability Table

| Feature | Description | Type | Related UC | Classes | Key Methods | Sequence | Pattern(s) |
|---------|-------------|------|------------|---------|-------------|----------|------------|
| F01 | Alarm Management | Deterministic | UC-01 | `AlarmController`, `Alarm`, `AlarmRepository`, `AlarmScheduler`, `UserPreferences` | `create_alarm()`, `validate()`, `save()`, `schedule_alarm()` | SD-01 | State (lifecycle starts `Scheduled`) |
| F02 | Verification Challenge | Deterministic | UC-02 | `VerificationTaskFactory`, `VerificationTask`, `ObjectDetectionTask`, `MathTask`, `TriviaTask`, `CustomTask`, `Alarm` | `create()`, `validate()`, `get_task_prompt()`, `on_verify()` | SD-01 | Strategy, Factory Method, State |
| F03 | Post-Wake Check-in | Deterministic | UC-02 | `CheckIn`, `ToolManager`, `FirebaseClient`, `Alarm` | `schedule()`, `send()`, `record_response()`, `timeout_handler()` | SD-01 | Observer, Adapter |
| F04 | AI Escalation Decision | AI/Hybrid | UC-03 | `AgentController`, `EscalationEngine`, `PromptBuilder`, `LLMClient`, `LLMProvider`, `ResponseParser`, `UserProfile`, `EscalationLog` | `run()`, `decide_escalation()`, `build_escalation_prompt()`, `send_request()`, `parse_decision()` | SD-02 | Strategy (provider), Adapter |
| F05 | Multi-Channel Escalation Execution | Deterministic | UC-03 | `ToolManager`, `Tool`, `TwilioClient`, `AlexaClient`, `EscalationLog`, `UserProfile` | `execute_escalation()`, `make_call()`, `send_sms()`, `send_alert()`, `record_escalation()` | SD-02 | Adapter, Observer |
| F06 | Emergency Contact & Prefs | Deterministic | UC-08 | `PreferencesController`, `UserPreferences`, `EmergencyContact`, `ToolManager` | `update()`, `validate()`, `request_consent()`, `send` test | SD-01 style | — |
| F07 | NL Reminder Creation | Hybrid | UC-04 | `CLIApp`/`RemindCommand` or GUI, `NLCommandParser`, `PromptBuilder`, `LLMClient`, `ResponseParser`, `ReminderFactory`, `ReminderScheduler` | `parse()`, `send_request()`, `parse_reminder()`, `create()`, `schedule_reminder()` | SD-05 | Command (CLI), Factory, Strategy |
| F08 | Reminder Management | Deterministic | UC-04, UC-06 | `ReminderController`, `Reminder`, `ReminderRepository`, `ReminderFactory`, `ReminderScheduler` | `validate()`, `save()`, `schedule_reminder()`, `mark_complete()` | SD-03 | Factory Method |
| F09 | AI Reminder Prioritization | AI/Hybrid | UC-05 | `TaskPrioritizer`, `LLMClient`, `ResponseParser`, `UserProfile`, `ReminderScheduler` | `prioritize_reminders()`, `send_request()`, `parse_ranking()` | SD-03 | Strategy (provider) |
| F10 | Reminder Escalation Levels | Deterministic | UC-05 | `ReminderScheduler`, `Reminder`, `ToolManager`, `ReminderObserver` | `send_primary_reminder()`, `send_escalation_reminder()`, `send_final_reminder()`, `on_status_changed()` | SD-03 | Observer |
| F11 | Behavioral Learning | Deterministic | UC-03, UC-05, UC-07 | `LearningEngine`, `AnalyticsEngine`, `UserProfile`, `EscalationLog` | `analyze_behavior()`, `compute_metrics()`, `update_learning_data()` | SD-04 | — |
| F12 | Analytics Dashboard | Deterministic | UC-07 | `AnalyticsController`, `AnalyticsEngine`, `UserProfile`, `OutputFormatter` | `get_stats()`, `query()`, `format()` | SD-04 / CLI | Strategy (formatter) |
| F13 | AI Recommendations | AI | UC-07 | `RecommendationService`, `AgentController`, `PromptBuilder`, `LLMClient`, `ResponseParser`, `UserPreferences` | `generate()`, `run()`, `parse_recommendations()`, `update()` | SD-06 | Strategy, Adapter |
| F14 | History & Explain | Hybrid | UC-09 | `HistoryController`, `EscalationLog`, `ReminderLog` | `list()`, `explain()` | SD-07 | — |

Dropped from Stage 1 credit: “Web Application Interface,” bare “Alarm Scheduling,” Delegation, Repository-as-pattern, EscalationEngine-as-Facade.

---

## Task 4 — How each feature is realized

### F01 — Alarm Management
**UC:** UC-01 · **SD:** SD-01  
`AlarmController` accepts GUI/CLI input → `Alarm.validate()` → `AlarmRepository.save()` →
`AlarmScheduler.schedule_alarm()` places the job; alarm enters `ScheduledState`.

### F02 — Verification Challenge
**UC:** UC-02 · **SD:** SD-01  
On trigger, `Alarm` moves to `RingingState`. `VerificationTaskFactory.create()` returns a
Strategy task. GUI (or CLI for non-photo) collects a response; `validate()` succeeds →
`VerifiedState` and stop sound; failures retry; camera failure → factory emits `MathTask`.

### F03 — Post-Wake Check-in
**UC:** UC-02 · **SD:** SD-01  
After verify, `CheckIn.schedule()`; `ToolManager` + `FirebaseClient` notify. Response →
`record_response()`; timeout notifies observers and starts F04.

### F04 — AI Escalation Decision
**UC:** UC-03 · **SD:** SD-02  
`AgentController.run("escalate", ctx)` observes `UserProfile` + `EscalationLog`,
`EscalationEngine.decide_escalation()` builds a prompt, `LLMClient`/`LLMProvider` answers,
`ResponseParser` validates method ∈ enabled list. On LLM failure → UX-01 fallback ladder.

### F05 — Escalation Execution
**UC:** UC-03 · **SD:** SD-02  
`ToolManager.execute_escalation(method)` uses `Tool` adapters. Outcome logged;
`UserProfile.update_learning_data()`. If tool fails, agent **re-decides** or walks fallback.

### F06 — Prefs & Emergency Contact
**UC:** UC-08 · **SD:** prefs save (same shape as F01)  
`PreferencesController` updates `UserPreferences`; `EmergencyContact.request_consent()` must
succeed before that contact is eligible for F05.

### F07 — NL Reminder Creation
**UC:** UC-04 · **SD:** SD-05  
CLI `RemindCommand` or GUI quick-add → `NLCommandParser` → LLM JSON → user confirms →
`ReminderFactory.create()` → schedule. Ambiguous/failed parse → clarifying question or manual form.

### F08 — Reminder Management
**UC:** UC-04, UC-06 · **SD:** SD-03  
CRUD via `ReminderController` / repository / scheduler; complete/snooze/delete update state
and observers.

### F09 — AI Prioritization
**UC:** UC-05 · **SD:** SD-03  
When multiple due, `TaskPrioritizer.prioritize_reminders()` uses LLM ranking validated to be
a permutation of IDs; else deterministic sort by priority → category → due time.

### F10 — Reminder Escalation Levels
**UC:** UC-05 · **SD:** SD-03  
`ReminderScheduler` sends primary / +5m / +15m; `ReminderObserver` updates UI/logs; complete
cancels the chain.

### F11 — Behavioral Learning
**UC:** related to UC-03/05/07 · **SD:** SD-04  
Nightly `LearningEngine.analyze_behavior()` → `AnalyticsEngine` → `UserProfile` metrics used
as agent memory on next F04/F09/F13.

### F12 — Analytics Dashboard
**UC:** UC-07 · **SD:** SD-04 data path  
`AnalyticsController.get_stats(range)` reads aggregates; GUI charts or CLI
`OutputFormatter` (`--json`).

### F13 — AI Recommendations
**UC:** UC-07 · **SD:** SD-06  
`RecommendationService.generate()` via agent/LLM; each suggestion validated against settings
schema; Accept applies `UserPreferences.update()`.

### F14 — History & Explain
**UC:** UC-09 · **SD:** SD-07  
`HistoryController.list/explain` reads stored reasoning/confidence from F04/F09 decisions
(including “fallback used” flag).

---

## Agent loop (applies to F04, F05, F07 confirm side-effects, F13)

```
observe(memory: UserProfile, EscalationLog, last ToolResult)
  → decide(LLM structured action)
  → act(ToolManager)
  → observe(outcome)
  → if failed and retries left: decide again
  → else: deterministic fallback / stop
```

`EscalationEngine` **orchestrates** escalation decisions; it is not claimed as a Facade pattern.
