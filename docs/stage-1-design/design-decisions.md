# RiseUp - Design Decisions & Justifications

This document explains the important architectural and design decisions made during Stage 1, why they were chosen, and what alternatives were considered.

---

## Table of Contents

1. [Architectural Decisions](#architectural-decisions)
2. [AI Integration Decisions](#ai-integration-decisions)
3. [Data & Persistence Decisions](#data--persistence-decisions)
4. [Testing & Reliability Decisions](#testing--reliability-decisions)
5. [User Experience Decisions](#user-experience-decisions)

---

## Architectural Decisions

### AD-01: Layered Architecture (Client-Server)

**Decision:**
Separate frontend (React) and backend (FastAPI) with REST API + WebSocket communication.

**Architecture:**
```
┌─────────────────────────────────────┐
│   Frontend Layer (React + Vite)     │
│   - UI Components                   │
│   - State Management                │
│   - API Client                      │
└────────────┬────────────────────────┘
             │ HTTP/WebSocket
┌────────────▼────────────────────────┐
│   Backend Layer (FastAPI + Python)  │
│   - API Routes                      │
│   - Business Logic Services         │
│   - Data Models                     │
└────────────┬────────────────────────┘
             │ SQL
┌────────────▼────────────────────────┐
│   Data Layer (PostgreSQL + Redis)   │
│   - Persistent storage              │
│   - Cache layer                     │
└─────────────────────────────────────┘
```

**Why This Design:**
- **Separation of Concerns:** UI logic separate from business logic
- **Scalability:** Backend can be scaled independently from frontend
- **Cross-Platform:** Same backend serves web, mobile (future), and CLI
- **Testability:** Each layer can be tested independently
- **Maintainability:** Clear responsibilities for each layer

**Alternatives Considered:**
1. **Monolithic (Full-stack in FastAPI):**
   - ❌ Harder to maintain (mixing templates with business logic)
   - ❌ Difficult to scale frontend separately
   - ❌ Cannot easily add mobile app later

2. **Microservices (separate services for alarms, escalation, reminders):**
   - ❌ Too complex for current scope
   - ❌ Would require message queue infrastructure
   - ❌ Operational overhead (multiple deployments)

**Chosen:** Layered (best balance of simplicity, maintainability, scalability)

---

### AD-02: Strategy Pattern for Verification Tasks

**Decision:**
Abstract `VerificationTask` class with concrete implementations: `ObjectDetectionTask`, `MathTask`, `TriviaTask`, `CustomTask`.

**Class Hierarchy:**
```
VerificationTask (abstract)
├── ObjectDetectionTask
├── MathTask
├── TriviaTask
└── CustomTask
```

**Why This Design:**
- **Open/Closed Principle:** Easy to add new task types without modifying existing code
- **Polymorphism:** Different tasks have different validation logic, but same interface
- **Flexibility:** Users can choose task types, system can add types later
- **Testing:** Each task type can be tested independently

**Alternatives Considered:**
1. **String-based task type with if/else:**
   ```python
   if task_type == "photo":
       # photo logic
   elif task_type == "math":
       # math logic
   ```
   - ❌ Violates DRY (logic scattered)
   - ❌ Hard to add new types (must modify existing code)
   - ❌ Cannot use polymorphism
   - ❌ Testing is more complex

2. **Single `VerificationTask` class with mode parameter:**
   - ❌ God Object (too many responsibilities)
   - ❌ Hard to maintain (all logic in one class)

**Chosen:** Strategy Pattern (clean, extensible, testable)

---

### AD-03: Separate Escalation Engine Service

**Decision:**
Create dedicated `EscalationEngine` class that orchestrates escalation decisions.

**EscalationEngine Responsibilities:**
- Gather user context from `UserProfile` and `EscalationLog`
- Call LLM for decision-making
- Coordinate with `ToolManager` to execute decision
- Record outcome in `EscalationLog`
- Update `UserProfile` with learning data

**Why This Design:**
- **Single Responsibility:** Escalation logic is isolated
- **Reusability:** Can be called from multiple places (timeout, check-in failure, etc.)
- **Testing:** Can test escalation logic independently
- **Evolution:** Can improve escalation algorithm without affecting alarm system
- **Learning:** Clear pipeline for data collection → AI → action

**Alternatives Considered:**
1. **Inline escalation in Alarm class:**
   - ❌ Alarm class becomes too large
   - ❌ Escalation tied to alarm timing
   - ❌ Cannot reuse for check-in failures
   - ❌ Hard to test

2. **Escalation logic inside LLMClient:**
   - ❌ Mixes AI integration with orchestration
   - ❌ LLM shouldn't know about Twilio/Alexa
   - ❌ Cannot test without calling LLM

**Chosen:** Separate Service (clean separation, testable, reusable)

---

### AD-04: LLMClient as Abstraction

**Decision:**
Wrap Claude API in `LLMClient` class rather than calling API directly from services.

**LLMClient Responsibilities:**
- Manage API credentials and keys
- Build prompts from context
- Handle API errors and retries
- Parse LLM responses
- Implement fallback logic if API fails

**Why This Design:**
- **Abstraction:** Services don't need to know Claude-specific details
- **Testability:** Can mock `LLMClient` in tests
- **Flexibility:** Can swap LLM providers later (switch to GPT, Llama, etc.)
- **Error Handling:** Centralized error handling for LLM failures
- **Rate Limiting:** Can implement rate limiting in one place

**Example Usage:**
```python
# Without abstraction (BAD):
response = anthropic.messages.create(
    model="claude-3",
    messages=[...],
    api_key=os.environ["CLAUDE_API_KEY"]
)

# With abstraction (GOOD):
llm_client = LLMClient()
decision = llm_client.decide_escalation(context)
```

**Alternatives Considered:**
1. **Direct API calls everywhere:**
   - ❌ API key scattered across code
   - ❌ Hard to test (must mock everywhere)
   - ❌ Tight coupling to Claude
   - ❌ Duplicate error handling

**Chosen:** LLMClient abstraction (clean, testable, flexible)

---

## AI Integration Decisions

### AI-01: Use Claude for Decision-Making

**Decision:**
Use Claude (Anthropic) LLM for:
- Escalation method selection (UC-03)
- Task reminder prioritization (UC-05)
- User behavior analysis
- Natural language command parsing

**Why Claude:**
- **Reasoning:** Excels at multi-step reasoning (analyze history → decide best approach)
- **Reliability:** Strong instruction-following (can follow escalation rules)
- **Context:** Can process complex user history and make contextual decisions
- **Cost:** Reasonable API pricing for this use case
- **Academic:** Available for educational projects
- **Interpretability:** Decisions can be explained to users

**Capabilities Used:**
```
Escalation Decision:
  Context: {
    user: "John",
    last_escalation: "call",
    call_success_rate: 0.95,
    sms_success_rate: 0.4,
    available_methods: ["call", "sms", "alexa"],
    current_time: "7:30 AM",
    user_timezone: "EST"
  }
  Prompt: "Given this user context, which escalation method is best?"
  Response: "CALL (95% success rate, morning time suggests user responsive to calls)"
```

**Alternatives Considered:**
1. **Rule-based heuristics (if/else):**
   ```python
   if call_success_rate > 0.8:
       use_call()
   elif sms_success_rate > 0.6:
       use_sms()
   ```
   - ❌ Brittle (doesn't handle edge cases)
   - ❌ Hard to maintain (many rules to manage)
   - ❌ Cannot adapt to new situations
   - ❌ No learning capability

2. **Machine Learning model (train on historical data):**
   - ❌ Requires large historical dataset (cold-start problem)
   - ❌ Infrastructure complexity (model training, deployment)
   - ❌ Maintenance burden (model monitoring, retraining)
   - ⚠️ Possible future enhancement after sufficient data collected

3. **Simple random selection:**
   - ❌ No intelligence
   - ❌ Poor user experience
   - ❌ Cannot learn

**Chosen:** Claude LLM (intelligent, adaptive, interpretable)

---

### AI-02: AI for Prioritization (Not Execution)

**Decision:**
Use AI ONLY for decision-making, not for executing deterministic tasks.

**AI Used For:**
- ✅ Deciding escalation method
- ✅ Prioritizing reminders
- ✅ Analyzing user patterns
- ✅ Generating insights

**AI NOT Used For:**
- ❌ Verifying photo (use computer vision library)
- ❌ Validating math answer (deterministic calculation)
- ❌ Scheduling alarms (use APScheduler)
- ❌ Storing data (use PostgreSQL)

**Why This Design:**
- **Reliability:** Deterministic tasks must work 100% reliably (don't trust LLM for this)
- **Cost:** Deterministic tasks are cheaper with libraries
- **Speed:** Deterministic tasks are faster (no API calls)
- **Testing:** Deterministic tasks are easier to test

**Example:**
```python
# CORRECT: AI for decision, deterministic for execution
escalation_method = llm_client.decide_escalation(context)  # AI
if escalation_method == "call":
    success = twilio_client.make_call(user_phone)  # Deterministic

# WRONG: AI for everything
response = llm_client.verify_photo(photo_data)  # Don't do this!
```

**Alternatives Considered:**
1. **Use LLM for all decisions (including verification):**
   - ❌ Expensive (API calls for every verification)
   - ❌ Slow (latency)
   - ❌ Unreliable (hallucinations possible)

**Chosen:** AI for decisions, deterministic for execution

---

### AI-03: Prompt Engineering for Consistency

**Decision:**
Carefully design prompts to ensure consistent, reliable LLM responses.

**Escalation Prompt Structure:**
```
CONTEXT:
- User: [name]
- Escalation history: [past methods and outcomes]
- Success rates: [method → success rate]
- Current conditions: [time, device status, etc.]
- Available methods: [call, SMS, Alexa, emergency contact]

TASK:
Analyze the user history and recommend ONE escalation method.

OUTPUT FORMAT:
{
  "method": "call" | "sms" | "alexa" | "emergency_contact",
  "reasoning": "[explain why this method]",
  "confidence": 0.0-1.0,
  "fallback": "[alternate method if first fails]"
}

CONSTRAINTS:
- ONLY use methods in the available list
- MUST choose the method with highest historical success rate
- DO NOT make up methods
```

**Why This Design:**
- **Consistency:** Structured format ensures predictable output
- **Fallback:** Always has a backup plan
- **Reasoning:** Explains decision (for debugging/transparency)
- **Confidence:** Indicates how certain the LLM is
- **Constraints:** Prevents LLM from making invalid recommendations

**Alternatives Considered:**
1. **Unstructured prompts:**
   - ❌ Unpredictable output format
   - ❌ Hard to parse programmatically
   - ❌ Easy for LLM to make invalid suggestions

**Chosen:** Structured prompts with JSON output

---

## Data & Persistence Decisions

### DP-01: PostgreSQL for Persistent Storage

**Decision:**
Use PostgreSQL for all persistent data (alarms, reminders, users, logs).

**Data Stored:**
- User accounts and preferences
- Alarms and reminders
- Verification task history
- Escalation logs
- Check-in records
- Behavioral analytics data

**Why PostgreSQL:**
- **ACID:** Guarantees data consistency (important for alarms!)
- **Reliability:** Won't lose data if app crashes
- **Querying:** Can analyze historical data (behavior patterns)
- **Relationships:** Can model complex relationships (user → alarms → check-ins)
- **Scalability:** Handles large datasets well

**Schema Example:**
```sql
-- Users
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR UNIQUE,
    timezone VARCHAR,
    created_at TIMESTAMP
);

-- Alarms
CREATE TABLE alarms (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users,
    time TIME,
    sound VARCHAR,
    verification_task_type VARCHAR,
    repeat_pattern VARCHAR,
    is_active BOOLEAN,
    created_at TIMESTAMP
);

-- Escalation Logs (for learning)
CREATE TABLE escalation_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users,
    alarm_id UUID REFERENCES alarms,
    method VARCHAR,
    timestamp TIMESTAMP,
    response_status VARCHAR,
    response_time_seconds INT
);
```

**Alternatives Considered:**
1. **SQLite (file-based):**
   - ❌ Not suitable for multi-user app
   - ❌ Limited concurrency

2. **NoSQL (MongoDB):**
   - ❌ Overkill for structured data
   - ❌ Harder to analyze historical data
   - ❌ No ACID guarantees by default

**Chosen:** PostgreSQL (reliable, queryable, ACID)

---

### DP-02: Redis for Caching & Real-Time

**Decision:**
Use Redis for:
- Caching frequently accessed data (user profiles, escalation success rates)
- Session management
- Real-time WebSocket message queue
- Celery task queue (for background jobs)

**Why Redis:**
- **Speed:** In-memory → nanosecond latency
- **Real-time:** Perfect for WebSocket-based notifications
- **Pub/Sub:** Built-in message broadcasting
- **Queue:** Celery integration for background tasks
- **Cache:** Reduces database load

**Usage Patterns:**
```python
# Cache user profile (update less frequently)
redis.set(f"user_profile:{user_id}", user_profile_json, expire=1800)

# Cache escalation success rates (update hourly)
redis.set(f"escalation_stats:{user_id}", stats_json, expire=3600)

# Queue background escalation task
celery_task.delay(alarm_id=123, user_id=456)

# WebSocket broadcast (send to all connected users)
redis.publish("alarm_triggered", json.dumps(alarm_event))
```

**Alternatives Considered:**
1. **Database cache (no Redis):**
   - ❌ Slower (must query database)
   - ❌ Cannot do real-time notifications easily

**Chosen:** Redis + PostgreSQL (fast caching, reliable persistence)

---

### DP-03: UserProfile for Aggregated Metrics

**Decision:**
Store aggregated behavioral metrics in `UserProfile` rather than computing from raw logs every time.

**UserProfile Stores:**
```python
class UserProfile:
    user_id: UUID
    
    # Escalation effectiveness
    escalation_stats: {
        "call": {"success_rate": 0.95, "avg_response_time": 120},
        "sms": {"success_rate": 0.40, "avg_response_time": 300},
        "alexa": {"success_rate": 0.60, "avg_response_time": 180}
    }
    
    # Alarm patterns
    alarm_patterns: {
        "avg_wake_time": "07:30",
        "success_rate_by_hour": {
            "06": 0.8, "07": 0.95, "08": 0.92, ...
        }
    }
    
    # Task completion
    task_completion_rates: {
        "work": 0.85,
        "personal": 0.60,
        "health": 0.95
    }
    
    # Recent updates
    updated_at: DateTime
```

**Why This Design:**
- **Performance:** Quick lookups (no computation needed)
- **AI Context:** LLM gets precomputed stats, not raw logs
- **Reduced LLM Cost:** Smaller context → smaller API requests
- **Real-time:** Stats always available
- **Versioning:** Can track how stats change over time

**Update Strategy:**
```
Events happen throughout the day
    ↓
Logged in EscalationLog, ReminderLog, etc.
    ↓
Nightly: LearningEngine computes new stats
    ↓
Update UserProfile with new metrics
    ↓
Next escalation/prioritization uses updated data
```

**Alternatives Considered:**
1. **Compute stats on-the-fly:**
   - ❌ Slow (must aggregate logs every time)
   - ❌ Expensive (complex queries)
   - ❌ LLM gets delayed insights

**Chosen:** Pre-aggregated metrics in UserProfile

---

## Testing & Reliability Decisions

### TR-01: Separate Test Strategies for Deterministic vs. AI Components

**Decision:**
- **Deterministic Components:** Traditional unit tests (pytest)
- **AI/LLM Components:** Behavioral tests (KUMA framework)

**Deterministic Components (Unit Tests):**
```python
# Alarm scheduling
def test_alarm_schedule():
    alarm = Alarm(time="07:00", user_id=123)
    job = alarm_scheduler.schedule(alarm)
    assert job.trigger_time == "07:00"

# Math verification
def test_math_task_validation():
    task = MathTask(equation="2+2")
    assert task.validate_answer(4) == True
    assert task.validate_answer(5) == False

# Escalation logging
def test_escalation_logged():
    escalate(user_id=123, method="call")
    log = database.get_escalation_log(user_id=123)
    assert log.method == "call"
```

**AI/LLM Components (Behavioral Tests):**
```
Test: BR-01 Correct Tool Selection
- Input: "Find recent papers about sleep disorders"
- Expected: Agent uses search tool before answering
- Pass Criteria: Search tool invoked in response

Test: BR-02 Escalation Adapts to User
- Input: User has 95% success rate with calls
- Expected: Agent recommends CALL as escalation
- Evaluation: Check if decision matches user history
```

**Why This Design:**
- **Reliability:** Deterministic logic must work 100%
- **Pragmatism:** Cannot demand 100% from LLM
- **Different Metrics:** Success = different things
- **Cost:** Cheaper to unit test deterministic code
- **Speed:** Unit tests run fast (no API calls)

**Alternatives Considered:**
1. **Only unit tests (no AI testing):**
   - ❌ Cannot verify LLM behaves correctly
   - ❌ Cannot catch AI failures in production

2. **All behavioral tests (expensive, slow):**
   - ❌ Expensive (API calls for every test)
   - ❌ Slow feedback loop
   - ❌ Cannot test deterministic logic easily

**Chosen:** Dual strategy (unit tests + behavioral tests)

---

### TR-02: Error Handling & Fallbacks

**Decision:**
Every external API call has error handling and fallback logic.

**Escalation Fallback Chain:**
```
Try: Call user via Twilio
  ↓ (Fails: network error)
Try: Send SMS via Twilio
  ↓ (Fails: user has no phone)
Try: Alexa alert
  ↓ (Fails: no Alexa device)
Try: Contact emergency contact
  ↓ (Fails: unreachable)
Result: Log failed escalation, notify user dashboard
```

**LLM Fallback:**
```python
try:
    decision = llm_client.decide_escalation(context)
except APIError:
    # Fallback to rule-based decision
    decision = fallback_escalation(context)
    
    # Log for debugging
    log.warning(f"LLM failed, using fallback: {decision}")
```

**Why This Design:**
- **Reliability:** System works even if external services fail
- **User Trust:** Won't fail silently
- **Debugging:** Know when/why fallbacks happen
- **Graceful Degradation:** Best-effort service

**Alternatives Considered:**
1. **Fail hard (no fallbacks):**
   - ❌ User misses alarm if service unavailable
   - ❌ Bad user experience

**Chosen:** Comprehensive error handling + fallbacks

---

## User Experience Decisions

### UX-01: Progressive Escalation

**Decision:**
Do NOT immediately call emergency contact. Escalate gradually:
1. Alarm sound + verification task (2 min)
2. Call user (if no response)
3. SMS (if call fails)
4. Alexa alert (if SMS not working)
5. Emergency contact (last resort)

**Why This Design:**
- **Respectful:** Don't bother emergency contact unless necessary
- **Effective:** Most users respond to earlier escalation levels
- **User Control:** Users can disable escalation if desired
- **Learning:** System learns which level works best per user

**Alternatives Considered:**
1. **Immediate emergency contact:**
   - ❌ Overuses emergency contact
   - ❌ Contact gets annoyed
   - ❌ May stop answering

2. **Only call, no other methods:**
   - ❌ Doesn't work if phone dead/off
   - ❌ SMS might work when call doesn't

**Chosen:** Progressive escalation

---

### UX-02: Intelligent Check-in (Not Immediate Dismissal)

**Decision:**
After verifying task, send check-in 5-10 min later instead of immediate dismissal.

**Why:**
- **Prevents Sleeping Again:** Checks user is actually awake (not just fumbled phone)
- **Learning:** More data points about user's wakefulness
- **Better UX:** User can "prove" wakefulness if needed

**Flow:**
```
7:00 AM: Alarm triggers
7:02 AM: User completes photo verification
         System: "Great! Rest well."
7:10 AM: Check-in notification: "Are you still awake?"
7:10 AM: User confirms "Yes"
         Success logged!
```

**Alternative (Rejected):**
```
7:00 AM: Alarm triggers
7:02 AM: User completes photo verification
7:02 AM: Alarm dismissed immediately
         (User could go back to bed)
```

**Chosen:** Delayed check-in

---

### UX-03: Reminders with Escalation Levels

**Decision:**
Send multiple reminder notifications (primary, escalation, final) rather than single reminder.

**Reminder Levels:**
```
9:00 AM: Primary reminder: "Call Mom"
9:05 AM: Escalation: "Hey! Still need to call Mom."
9:20 AM: Final: "Last reminder: Call Mom (High Priority)"
```

**Why:**
- **Adaptive:** Can skip escalation reminders if user completes immediately
- **Respects Time:** Don't spam if user completes early
- **Effective:** Multiple nudges increase completion rate
- **Learning:** Track how many reminders needed per task type

**Alternatives Considered:**
1. **Single reminder:**
   - ❌ User might miss it (forget phone, distracted)
   - ❌ No escalation for important tasks

2. **Fixed multiple reminders:**
   - ❌ Spammy (even if user already completed)

**Chosen:** Adaptive escalation reminders

---

## Summary: Design Principles Applied

| Principle | Application |
|-----------|------------|
| **Single Responsibility** | Each class has one reason to change |
| **Open/Closed** | New verification tasks, escalation methods via inheritance |
| **Liskov Substitution** | Any VerificationTask can replace another |
| **Interface Segregation** | LLMClient, ToolManager expose only needed methods |
| **Dependency Inversion** | Services depend on abstractions (LLMClient), not concrete APIs |
| **DRY** | Shared logic in base classes/services |
| **YAGNI** | Only implement what's needed (no unused features) |
| **Composition over Inheritance** | Services composed together, not deep hierarchies |

---

## Design Evolution & Future Improvements

### Possible Enhancements (Not in Stage 1)

1. **Machine Learning Model (Once Data Collected)**
   - After 6 months of data, train ML model on escalation success
   - Replace rule-based fallback with ML-based decisions

2. **Wearable Integration**
   - Add support for smartwatch (detect if user actually got out of bed)
   - Integrate with fitness trackers for sleep quality data

3. **Calendar Integration**
   - Check Google Calendar before scheduling alarm (don't alarm on days off)
   - Prioritize reminders based on calendar events

4. **Natural Language Commands**
   - Voice assistant: "Alexa, ask RiseUp to remind me to..."
   - Improve NLP parsing for complex commands

5. **Microservices Architecture**
   - If traffic grows, split into separate services
   - Independent scaling for alarms vs. reminders vs. escalation

6. **Distributed Scheduling**
   - If single server can't handle volume, use distributed scheduler
   - Ensure alarms trigger reliably at scale

---

## Conclusion

The RiseUp architecture balances:
- **Simplicity:** Easy to understand and implement (Stage 1-2)
- **Extensibility:** Easy to add new features (verification tasks, escalation methods)
- **Maintainability:** Clear separation of concerns, testable components
- **Scalability:** Can grow from single server to multiple services if needed
- **Reliability:** Fallbacks, error handling, ACID database
- **Intelligence:** AI integration without over-relying on LLM