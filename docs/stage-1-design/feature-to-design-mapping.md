# RiseUp - Feature-to-Design Mapping

This document maps each major feature to the classes responsible for implementing it, explaining how these classes collaborate.

---

## Feature 1: Intelligent Alarm System

### Feature Description
Users create customizable alarms with multiple cancellation options (verification tasks). When alarm triggers, users must complete a verification task to dismiss it. System sends check-in reminder 5-10 minutes later.

### Designed Classes
- `Alarm` — Core alarm entity
- `VerificationTask` (abstract) — Base class for verification strategies
- `ObjectDetectionTask` — Photo-based verification
- `MathTask` — Math puzzle verification
- `TriviaTask` — Trivia question verification
- `CustomTask` — User-defined verification
- `CheckIn` — Check-in reminder management
- `AlarmScheduler` — Schedules alarm triggers
- `UserPreferences` — User's alarm settings

### Class Responsibilities

| Class | Responsibility |
|-------|-----------------|
| `Alarm` | Stores alarm configuration (time, sound, task, repeat pattern, active status) |
| `VerificationTask` | Abstract interface for all verification strategies |
| `ObjectDetectionTask` | Generates photo prompt, validates photo using vision API |
| `MathTask` | Generates equations, validates numerical answers |
| `TriviaTask` | Generates trivia questions, validates answer selection |
| `CustomTask` | Stores custom task description, marks complete when user confirms |
| `CheckIn` | Schedules check-in reminder, tracks response status |
| `AlarmScheduler` | Uses APScheduler to schedule alarm triggers at correct time |
| `UserPreferences` | Stores enabled verification methods and escalation preferences |

### How Classes Collaborate

```
1. User creates alarm (UC-01)
   └─> Alarm stores configuration
   └─> AlarmScheduler schedules trigger
   └─> UserPreferences records settings

2. Alarm triggers (UC-02)
   └─> AlarmScheduler detects time
   └─> Alarm.trigger() called
   └─> Alarm gets VerificationTask (ObjectDetectionTask, MathTask, etc.)
   └─> Frontend displays task

3. User completes verification
   └─> VerificationTask.validate() checks if correct
   └─> If valid: Alarm stops, CheckIn scheduled
   └─> If invalid: Task resets, user retries

4. Check-in 5-10 min later
   └─> CheckIn.send() notifies user
   └─> If response: CheckIn.record_response()
   └─> If timeout: EscalationEngine triggered
```

### Traceability Example
**Requirement:** "User must complete verification task to dismiss alarm"
- **Use Case:** UC-02
- **Classes:** `Alarm`, `VerificationTask`, `AlarmScheduler`
- **Methods:** `Alarm.trigger()`, `VerificationTask.validate()`, `CheckIn.send()`
- **Database:** Logs stored in `EscalationLog` for learning

---

## Feature 2: AI-Powered Escalation Engine

### Feature Description
When users don't respond to alarms or check-ins, the system uses AI to intelligently decide the best escalation method based on user history, preferences, and success rates.

### Designed Classes
- `EscalationEngine` — Main escalation orchestrator
- `LLMClient` — Claude API wrapper
- `ToolManager` — Manages external tool execution
- `TwilioClient` — Handles calls and SMS
- `AlexaClient` — Handles Alexa alerts
- `EscalationLog` — Records escalation attempts
- `UserProfile` — Stores user history and preferences

### Class Responsibilities

| Class | Responsibility |
|-------|-----------------|
| `EscalationEngine` | Orchestrates escalation: gathers context, calls LLM, executes decision |
| `LLMClient` | Sends context to Claude, parses AI decision response |
| `ToolManager` | Selects and executes appropriate escalation tool |
| `TwilioClient` | Makes phone calls, sends SMS, handles user responses |
| `AlexaClient` | Sends alerts through Alexa API |
| `EscalationLog` | Records all escalation events for learning |
| `UserProfile` | Provides historical data for AI analysis |

### How Classes Collaborate

```
1. Alarm not responded to (2+ min timeout)
   └─> EscalationEngine.decide_escalation()
   
2. Gather context
   └─> Get UserProfile (past escalations, success rates)
   └─> Get EscalationLog (history of attempts)
   └─> Prepare context: "User X didn't respond. Call worked 90% before."
   
3. AI decision
   └─> LLMClient.send_request() with context
   └─> Claude analyzes and recommends method
   └─> LLMClient.parse_decision() extracts choice
   
4. Execute escalation
   └─> ToolManager.execute_escalation(selected_method)
   └─> If CALL: TwilioClient.make_call()
   └─> If SMS: TwilioClient.send_sms()
   └─> If ALEXA: AlexaClient.send_alert()
   
5. Record & Learn
   └─> EscalationLog.record_escalation()
   └─> UserProfile.update_learning_data()
   └─> Next escalation uses improved data
```

### Traceability Example
**Requirement:** "AI decides best escalation method based on user history"
- **Use Case:** UC-03
- **Classes:** `EscalationEngine`, `LLMClient`, `UserProfile`, `EscalationLog`, `ToolManager`
- **Methods:** `EscalationEngine.decide_escalation()`, `LLMClient.send_request()`, `ToolManager.execute_escalation()`
- **Database:** Stores decisions in `EscalationLog`

---

## Feature 3: Smart Task Reminder System

### Feature Description
Users can create task reminders via app or voice. System intelligently prioritizes reminders when multiple are due simultaneously. Reminders have escalation (primary → escalation → final).

### Designed Classes
- `Reminder` — Core reminder entity
- `ReminderScheduler` — Schedules reminder triggers
- `TaskPrioritizer` — Uses AI to prioritize multiple reminders
- `LLMClient` — Analyzes context for prioritization
- `UserProfile` — Provides user behavior data
- `ToolManager` — Sends notifications (push, SMS, email)

### Class Responsibilities

| Class | Responsibility |
|-------|-----------------|
| `Reminder` | Stores task data (title, due time, priority, category, status) |
| `ReminderScheduler` | Schedules reminders at correct time, manages escalation levels |
| `TaskPrioritizer` | Uses AI to rank reminders if multiple are due |
| `LLMClient` | Analyzes user behavior to determine priority order |
| `UserProfile` | Provides completion rates per category and time patterns |
| `ToolManager` | Sends notifications via push, SMS, or email |

### How Classes Collaborate

```
1. User creates reminder (UC-04)
   └─> Reminder stores task details
   └─> ReminderScheduler schedules at due_time
   └─> User confirmed

2. Reminder time reached
   └─> ReminderScheduler detects trigger_time
   └─> Check: Are multiple reminders due?
   
3. If multiple reminders due (prioritization)
   └─> TaskPrioritizer.prioritize_reminders()
   └─> LLMClient analyzes:
       ├─ Priority levels (high/medium/low)
       ├─ Category completion rates
       ├─ Time of day patterns
       ├─ User behavior
   └─> Returns priority order
   
4. Send primary reminder
   └─> ToolManager.send_notification() for highest priority
   └─> User can complete or snooze
   
5. Escalation reminders (if not completed)
   └─> ReminderScheduler.send_escalation_reminder() at +5 min
   └─> ReminderScheduler.send_final_reminder() at +15 min
   └─> If still not completed: Log as missed
   
6. Log & Learn
   └─> Record completion time, response time, method used
   └─> Update UserProfile with category completion patterns
```

### Traceability Example
**Requirement:** "AI prioritizes multiple reminders at same time"
- **Use Case:** UC-05
- **Classes:** `Reminder`, `ReminderScheduler`, `TaskPrioritizer`, `LLMClient`, `UserProfile`
- **Methods:** `TaskPrioritizer.prioritize_reminders()`, `LLMClient.analyze_context()`
- **Database:** Stores completion data for future prioritization

---

## Feature 4: Behavioral Learning & User Profiling

### Feature Description
System continuously learns from user behavior (alarm responses, escalation effectiveness, task completion patterns) and uses this data to improve future decisions and provide personalized recommendations.

### Designed Classes
- `UserProfile` — Stores aggregated user behavior data
- `LearningEngine` — Analyzes behavior patterns
- `EscalationLog` — Records escalation events
- `Database` — Persists all behavioral data
- `AnalyticsEngine` — Computes metrics and patterns

### Class Responsibilities

| Class | Responsibility |
|-------|-----------------|
| `UserProfile` | Stores: sleep patterns, completion rates, escalation effectiveness, preferences |
| `LearningEngine` | Aggregates data, identifies patterns, updates recommendations |
| `EscalationLog` | Records every escalation event with outcome |
| `AnalyticsEngine` | Calculates metrics (success rates, patterns, anomalies) |
| `Database` | Persists all historical data for analysis |

### How Classes Collaborate

```
1. User completes alarm/reminder (event)
   └─> System records: time, duration, method, category
   └─> Data stored in Database
   
2. LearningEngine processes events (periodic)
   └─> Get all recent events from Database
   └─> Run AnalyticsEngine.analyze_patterns()
   
3. Analytics computation
   ├─ Sleep quality: Track wake-up times, response delays
   ├─ Escalation effectiveness: Which method works best?
   ├─ Task completion: By category, time of day, priority
   ├─ Behavioral trends: Improving? Declining?
   └─ Anomaly detection: Unusual patterns?
   
4. Update user insights
   └─> LearningEngine.update_profile()
   └─> Store results in UserProfile
   └─> Examples:
       ├─ "Calls work 95% for you, SMS only 40%"
       ├─ "You complete work tasks best at 9 AM"
       ├─ "Personal task completion: 60% → improved to 75%"
   
5. Use in future decisions
   └─> EscalationEngine uses updated data
   └─> TaskPrioritizer uses updated completion patterns
   └─> System improves over time
```

### Traceability Example
**Requirement:** "System learns what escalation methods work best for user"
- **Use Case:** UC-03 (escalation) with learning outcome
- **Classes:** `UserProfile`, `LearningEngine`, `EscalationLog`, `AnalyticsEngine`
- **Methods:** `LearningEngine.analyze_behavior()`, `UserProfile.update_learning_data()`
- **Data Flow:** Event → Database → Analytics → UserProfile → Future Decision

---

## Feature 5: Cross-Platform Web Application

### Feature Description
Responsive web UI that runs on Windows, macOS, Linux. Users manage alarms, reminders, view analytics, configure settings all from a single web interface.

### Designed Classes
- React Components (UI Layer)
- State Management (Zustand/Redux)
- API Client (HTTP communication)
- Service Layer (Frontend services)
- Database (Backend persistence)

### Class Responsibilities

| Layer | Responsibility |
|-------|-----------------|
| **UI Components** | Display alarms, reminders, analytics, settings forms |
| **State Management** | Manage local UI state (form inputs, pagination, filters) |
| **API Client** | HTTP communication with backend (REST/WebSocket) |
| **Frontend Services** | Business logic for alarm/reminder CRUD operations |
| **Backend API Routes** | Expose endpoints for all UI interactions |
| **Backend Services** | Implement core business logic |
| **Database** | Persist all user data |

### How Classes Collaborate

```
1. User opens web app
   └─> React components render
   └─> State Management initializes
   
2. User navigates (e.g., "Create Alarm")
   └─> AlarmFormComponent renders
   └─> User fills form
   
3. User clicks "Save Alarm"
   └─> Frontend Service.create_alarm()
   └─> API Client.POST /api/alarms
   └─> Backend receives request
   
4. Backend processes
   └─> Alarm Controller receives data
   └─> Alarm Service validates & creates
   └─> Database stores alarm
   
5. Backend response
   └─> Alarm Controller returns created alarm
   └─> API Client receives response
   └─> State Management updates
   └─> UI re-renders with new alarm
   
6. Real-time updates (WebSocket)
   └─> Alarm triggers (backend)
   └─> Backend notifies all connected clients
   └─> Frontend UI updates in real-time (no polling needed)
```

### Technology Mapping

| Feature Requirement | Frontend Technology | Backend Technology |
|-------------------|-------------------|-------------------|
| Responsive design | Tailwind CSS, Flexbox/Grid | N/A (CSS handles) |
| Cross-OS compatibility | React (browser-based) | FastAPI (runs on any OS) |
| Alarm management UI | React components, forms | Alarm Service, Database |
| Real-time notifications | WebSocket, State Management | FastAPI WebSocket support |
| Task analytics & charts | Recharts, Zustand | AnalyticsEngine, Database |
| Dark/Light theme | Tailwind CSS variables | User Preferences stored |

---

## Design Decision Summary

### 1. **LLM for AI Decisions**
- **Decision:** Use Claude API for escalation and prioritization
- **Why:** 
  - Can reason about complex user behavior
  - Can adapt to new situations
  - Can explain decisions (interpretability)
- **Alternative Rejected:** Rule-based heuristics (too rigid, hard to adapt)

### 2. **Escalation Strategy Pattern**
- **Decision:** Abstract VerificationTask with multiple concrete implementations
- **Why:**
  - Easy to add new task types without changing existing code
  - Follows Open/Closed Principle
  - Each task type encapsulates its own logic
- **Alternative Rejected:** Large if/else blocks (violates DRY, hard to maintain)

### 3. **Separate Learning Engine**
- **Decision:** Dedicated LearningEngine class for behavior analysis
- **Why:**
  - Separates concerns (learning ≠ escalation)
  - Scales independently
  - Can be updated/improved without affecting alarms
- **Alternative Rejected:** Inline learning in EscalationEngine (tight coupling)

### 4. **Check-in as Separate Entity**
- **Decision:** CheckIn class separate from Alarm
- **Why:**
  - Check-in is a distinct responsibility
  - Can have independent scheduling/timeouts
  - Cleaner separation of concerns
- **Alternative Rejected:** CheckIn logic inside Alarm class (God Object)

### 5. **ToolManager Delegation**
- **Decision:** ToolManager delegates to TwilioClient, AlexaClient, etc.
- **Why:**
  - Isolates external API dependencies
  - Easy to mock for testing
  - Easy to add new tool types
- **Alternative Rejected:** Direct calls from EscalationEngine (tight coupling to external services)

### 6. **Database Over In-Memory Storage**
- **Decision:** PostgreSQL for persistent storage
- **Why:**
  - Alarms/reminders must survive app restarts
  - Learning data needs long-term retention
  - ACID guarantees for consistency
- **Alternative Rejected:** Redis only (loses data on restart)

### 7. **UserProfile for Learning Data**
- **Decision:** Aggregate behavioral metrics in UserProfile
- **Why:**
  - Fast lookups for AI decisions (no computation needed)
  - Reduces LLM context size
  - Historical data always available
- **Alternative Rejected:** Recompute from raw logs every time (slow, expensive)

### 8. **APScheduler for Alarm Scheduling**
- **Decision:** Use APScheduler with Celery for reliable scheduling
- **Why:**
  - Handles complex scheduling (recurring alarms)
  - Survives app restarts
  - Reliable trigger execution
- **Alternative Rejected:** Manual time checks (unreliable, misses triggers)

---

## Summary Table: Feature → Classes

| Feature | UC | Primary Classes | Secondary Classes | Data Storage |
|---------|----|-----------------|--------------------|---------------|
| Intelligent Alarms | UC-01, UC-02 | `Alarm`, `VerificationTask`, `CheckIn`, `AlarmScheduler` | `UserPreferences`, `UserProfile` | `alarms`, `check_ins` tables |
| AI Escalation | UC-03 | `EscalationEngine`, `LLMClient`, `ToolManager` | `UserProfile`, `EscalationLog` | `escalation_logs` table |
| Task Reminders | UC-04, UC-05 | `Reminder`, `ReminderScheduler`, `TaskPrioritizer` | `LLMClient`, `UserProfile`, `ToolManager` | `reminders` table |
| Learning System | All UCs | `LearningEngine`, `UserProfile`, `AnalyticsEngine` | `EscalationLog`, `Database` | `user_profiles` table |
| Web App | All UCs | React Components, API Client | Backend Services, Database | PostgreSQL |

---

## Comprehensive Feature-to-Design Traceability Matrix

This table maps **every major feature** to its design components, providing complete traceability from requirements through implementation.

| # | Feature | Type | Related UC | Participating Classes | Key Methods | Sequence Diagram | Design Pattern(s) | Database Tables |
|---|---------|------|-----------|----------------------|--------------|------------------|------------------|-----------------|
| **F01** | **Intelligent Alarm System** | Hybrid | UC-01, UC-02 | `Alarm`, `AlarmScheduler`, `VerificationTask`, `ObjectDetectionTask`, `MathTask`, `TriviaTask`, `CustomTask`, `CheckIn`, `UserPreferences` | `Alarm.trigger()`, `Alarm.schedule()`, `VerificationTask.validate()`, `CheckIn.send()`, `CheckIn.record_response()` | SD-01 | Strategy (verification tasks), Observer (check-in notifications) | `alarms`, `check_ins`, `verification_tasks` |
| **F02** | **Verification Task Strategies** | Deterministic | UC-02 | `VerificationTask` (abstract), `ObjectDetectionTask`, `MathTask`, `TriviaTask`, `CustomTask` | `validate()`, `get_task_prompt()`, `create_random_task()` | SD-01 | Strategy Pattern | `verification_tasks`, `user_custom_tasks` |
| **F03** | **AI-Powered Escalation Engine** | AI | UC-03 | `EscalationEngine`, `LLMClient`, `UserProfile`, `EscalationLog`, `ToolManager` | `EscalationEngine.decide_escalation()`, `LLMClient.send_request()`, `ToolManager.execute_escalation()` | SD-02 | Facade (escalation complexity), Delegation (tool execution) | `escalation_logs`, `user_profiles` |
| **F04** | **External Tool Integration** | Deterministic | UC-03 | `ToolManager`, `TwilioClient`, `AlexaClient`, `FirebaseClient` | `ToolManager.call_user()`, `ToolManager.send_sms()`, `ToolManager.send_alexa_alert()`, `ToolManager.send_push_notification()` | SD-02 | Delegation Pattern, Adapter | `escalation_logs` |
| **F05** | **Task Reminder Creation** | Hybrid | UC-04 | `Reminder`, `ReminderScheduler`, `UserPreferences` | `Reminder.create()`, `ReminderScheduler.schedule_reminder()` | SD-03 | Factory (reminder creation) | `reminders`, `user_preferences` |
| **F06** | **Smart Reminder Prioritization** | AI | UC-05 | `TaskPrioritizer`, `LLMClient`, `UserProfile`, `ReminderScheduler` | `TaskPrioritizer.prioritize_reminders()`, `LLMClient.analyze_context()`, `ReminderScheduler.send_notification()` | SD-03 | Facade (prioritization logic) | `reminders`, `user_profiles` |
| **F07** | **Reminder Escalation Levels** | Deterministic | UC-05 | `ReminderScheduler`, `Reminder`, `ToolManager` | `ReminderScheduler.send_primary_reminder()`, `ReminderScheduler.send_escalation_reminder()`, `ReminderScheduler.send_final_reminder()` | SD-03 | Observer (escalation notifications) | `reminders`, `reminder_escalation_log` |
| **F08** | **Behavioral Learning System** | Deterministic | All UCs | `LearningEngine`, `UserProfile`, `AnalyticsEngine`, `EscalationLog` | `LearningEngine.analyze_behavior()`, `LearningEngine.identify_patterns()`, `UserProfile.update_learning_data()` | SD-04 | Repository Pattern | `user_profiles`, `escalation_logs`, `reminder_logs` |
| **F09** | **User Profile & Analytics** | Deterministic | All UCs | `UserProfile`, `LearningEngine`, `Database` | `UserProfile.update_learning_data()`, `UserProfile.get_optimal_settings()`, `UserProfile.predict_success_rate()` | SD-04 | Repository Pattern | `user_profiles`, `user_analytics` |
| **F10** | **User Preferences Management** | Deterministic | UC-01, UC-03 | `UserPreferences`, `User` | `UserPreferences.get_enabled_methods()`, `UserPreferences.update()` | — | — | `user_preferences` |
| **F11** | **Web Application Interface** | Deterministic | All UCs | React Components, API Client, State Management | API calls to backend, UI event handlers | — | MVC Pattern (Model in backend, View in React) | N/A (frontend) |
| **F12** | **Alarm Scheduling & Triggering** | Deterministic | UC-02 | `AlarmScheduler`, `APScheduler`, `Alarm` | `AlarmScheduler.schedule_alarm()`, `AlarmScheduler.trigger_alarm()`, `AlarmScheduler.cancel_alarm()` | SD-01 | Adapter (APScheduler wrapper) | `alarms`, `scheduled_jobs` |

---

## Traceability: Features → Use Cases → Classes → Implementation

### Example: F01 Intelligent Alarm System

| Aspect | Details |
|--------|---------|
| **Feature** | Users can set customizable alarms with verification tasks and check-in confirmation |
| **Use Cases** | UC-01 (Create alarm), UC-02 (Alarm triggers and verification) |
| **Participating Classes** | `Alarm`, `AlarmScheduler`, `VerificationTask` (+ subclasses), `CheckIn`, `UserPreferences` |
| **Key Methods** | `Alarm.trigger()`, `VerificationTask.validate()`, `CheckIn.send()`, `CheckIn.record_response()` |
| **Sequence Diagram** | SD-01: Alarm Triggers with Verification |
| **Design Patterns** | Strategy (multiple verification task types), Observer (check-in notifications) |
| **Database Tables** | `alarms`, `check_ins`, `verification_tasks` |
| **External Dependencies** | None (all deterministic) |
| **Fallback Strategy** | If check-in fails → trigger EscalationEngine (F03) |

### Example: F03 AI-Powered Escalation Engine

| Aspect | Details |
|--------|---------|
| **Feature** | AI analyzes user history and recommends best escalation method when user doesn't respond |
| **Use Cases** | UC-03 (AI escalation on timeout) |
| **Participating Classes** | `EscalationEngine`, `LLMClient`, `UserProfile`, `EscalationLog`, `ToolManager`, `TwilioClient`, `AlexaClient` |
| **Key Methods** | `EscalationEngine.decide_escalation()`, `LLMClient.send_request()`, `ToolManager.execute_escalation()` |
| **Sequence Diagram** | SD-02: AI Escalation |
| **Design Patterns** | Facade (EscalationEngine hides AI complexity), Delegation (ToolManager delegates to specific tools), Adapter (LLMClient wraps Claude API) |
| **Database Tables** | `escalation_logs`, `user_profiles` |
| **External Dependencies** | Claude API (LLM), Twilio API (calls/SMS), Alexa API, Firebase (notifications) |
| **Fallback Strategy** | If LLM fails → use rule-based escalation (highest success rate method) |

---