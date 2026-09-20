# RiseUp - Use Cases

## Use Case Overview

RiseUp has five major use cases that cover the core functionality:

1. **UC-01:** User Configures and Sets an Alarm
2. **UC-02:** Alarm Triggers and User Completes Verification Task
3. **UC-03:** User Ignores Alarm or Verification → AI Escalates
4. **UC-04:** User Creates a Task Reminder
5. **UC-05:** System Sends Prioritized Task Reminders

---

## UC-01: User Configures and Sets an Alarm

### Use Case ID
UC-01

### Use Case Name
Configure and Set Alarm

### Primary Actor
User

### Secondary Actors
- System (Alarm Scheduler)

### Goal
User creates a new alarm with customized settings and activates it for a specific time.

### Preconditions
- User is logged into RiseUp
- User has access to the web application
- System has current time and timezone information

### Trigger
User clicks "New Alarm" or "Add Alarm" button on the dashboard.

### Main Success Scenario
1. System displays alarm creation form
2. User enters alarm time (HH:MM)
3. User selects alarm sound/tone from library
4. User sets vibration intensity (low/medium/high)
5. User selects verification task type:
   - Object Detection (take photo)
   - Math Puzzle (solve equation)
   - Trivia Question (answer question)
   - Custom Task (user-defined)
6. User optionally sets label/name (e.g., "Work Alarm", "Gym Alarm")
7. User configures repeat pattern (once, daily, weekdays, custom)
8. User optionally sets snooze availability (enable/disable)
9. User reviews settings
10. User clicks "Save Alarm"
11. System validates alarm configuration
12. System schedules alarm in the database
13. System confirms "Alarm set for [time]"
14. Alarm appears on dashboard as active

### Alternative Flows

#### A1: User Cancels Alarm Creation
- At any step, user clicks "Cancel"
- System discards all entries
- User returns to dashboard
- No alarm is created

#### A2: Invalid Time Entry
- User enters invalid time format
- System displays error message: "Please enter time in HH:MM format"
- User re-enters time
- Continue from step 2 in main flow

#### A3: User Selects Custom Verification Task
- At step 5, user selects "Custom Task"
- System shows text input for custom task description
- User enters task (e.g., "Do 10 pushups")
- User confirms custom task
- Continue with step 6 in main flow

#### A4: User Edits Existing Alarm
- User clicks "Edit" on an existing alarm
- System loads alarm settings into form
- User modifies settings
- User clicks "Save"
- System updates alarm in database
- Updated alarm replaces old one

### Exception Flows

#### E1: System Fails to Save Alarm
- User clicks "Save Alarm"
- System attempts to save but database connection fails
- System displays error: "Failed to save alarm. Please try again."
- User clicks "Retry"
- System attempts save again
- If successful, continue with step 13 in main flow
- If failed again, offer to save offline or contact support

#### E2: User Exceeds Maximum Alarms
- User has 20 alarms already set
- System displays: "Maximum alarms reached. Delete an alarm to add another."
- User is directed to alarm management screen
- User must delete an alarm first before creating new one

### Postconditions
- Alarm is stored in the database
- Alarm is scheduled to trigger at specified time
- Alarm appears on user's dashboard as active
- User receives confirmation notification

### Notes
- Alarm persists across sessions (if user logs out/closes app)
- User can edit or delete alarm at any time before it triggers
- Multiple alarms can be set for the same time

---

## UC-02: Alarm Triggers and User Completes Verification Task

### Use Case ID
UC-02

### Use Case Name
Alarm Triggers and Complete Verification to Dismiss

### Primary Actor
User

### Secondary Actors
- System (Alarm Engine)
- LLM (Claude - for AI features)

### Goal
When alarm triggers, user must complete a verification task to prove they are awake before alarm can be dismissed.

### Preconditions
- Alarm has been set and is enabled (from UC-01)
- Current time equals alarm time
- User's device is connected to internet
- User is nearby to receive alarm

### Trigger
System's clock reaches the scheduled alarm time.

### Main Success Scenario
1. System plays alarm sound at maximum volume
2. System activates vibration at configured intensity
3. System displays alarm screen with:
   - Alarm name/label
   - Current time
   - Assigned verification task
   - "Cannot dismiss - Complete task" message
   - Dismiss button is disabled/grayed out
4. User wakes up and responds to alarm
5. User reads the verification task
6. Based on task type:
   - **Object Detection:** User taps "Take Photo" → Camera opens → User takes photo of self/room → System analyzes photo → Verifies object present
   - **Math Puzzle:** System displays equation → User enters answer → System validates answer
   - **Trivia:** System displays question → User selects answer → System validates answer
   - **Custom Task:** System displays task description → User marks as "Completed" when done
7. System validates task completion
8. If valid: Task completion succeeds
9. System stops alarm sound/vibration
10. System displays "Task completed! Rest well." message
11. System records completion time
12. System schedules check-in reminder for 5-10 minutes later
13. Alarm screen closes, user returns to dashboard
14. System stores alarm completion log

### Alternative Flows

#### A1: User Completes Verification Task Incorrectly
- User attempts verification task (e.g., takes blurry photo, answers math wrong)
- System validation fails
- System displays: "Task not valid. Please try again."
- Task resets for another attempt
- User must try again
- Alarm sound/vibration continues
- Continue from step 5 in main flow (user retries task)

#### A2: User Snooze Is Available
- User has enabled snooze in alarm settings (UC-01)
- A "Snooze 10 min" button appears on alarm screen
- If user clicks snooze:
  - Alarm pauses for 10 minutes
  - Alarm sound/vibration stops temporarily
  - At end of 10 min, alarm triggers again
  - Return to step 1 in main flow
  - Note: Limited snooze attempts (e.g., max 3 snoozes)

#### A3: User Disables Alarm (Invalid Attempt)
- User tries to close/swipe away alarm screen
- System prevents dismissal: "Complete task to dismiss alarm"
- Alarm sound/vibration continues
- Alarm remains on screen
- Return to step 5 in main flow

### Exception Flows

#### E1: Verification Task Fails Unexpectedly
- User attempts photo object detection
- System camera fails or photo upload fails
- System displays: "Camera error. Please try again or contact support."
- User can retry with same task
- If repeated failures, offer fallback task (math puzzle instead)

#### E2: User Doesn't Respond to Alarm
- Alarm has been triggering for 2+ minutes with no user action
- System detects inactivity
- System transitions to UC-03 (Escalation)
- Check-in is skipped
- Escalation begins

#### E3: Check-in Reminder After Task Completion
- User completes verification task at 7:05 AM
- System schedules check-in for 7:10-7:15 AM
- Check-in is sent as:
  - Notification push: "Are you still awake? Tap to confirm"
  - Or SMS: "RiseUp check-in: Reply YES to confirm you're awake"
- If user responds within 5 minutes → Success (log recorded)
- If user doesn't respond → Transition to UC-03 (Escalation)

### Postconditions
- Alarm sound/vibration is stopped
- Verification task is marked as completed
- Completion time is recorded in database
- Check-in reminder is scheduled
- Alarm log entry is created for analytics
- User dashboard shows alarm as "completed"

### Notes
- Verification task prevents casual dismissal
- Check-in 5-10 min later verifies actual wakefulness
- Failed check-in triggers escalation (UC-03)
- System learns which task types user completes fastest
- Alarm logs help identify patterns (sleep quality, task effectiveness)

---

## UC-03: User Ignores Alarm or Verification → AI Escalates

### Use Case ID
UC-03

### Use Case Name
AI Escalation When User Doesn't Respond

### Primary Actor
System (AI Escalation Engine)

### Secondary Actors
- User (recipient of escalation)
- LLM (Claude - for AI reasoning and decision-making)
- External Tools (Twilio for calls/SMS, Alexa SDK, etc.)

### Goal
When user doesn't respond to alarm or verification task, system intelligently escalates using AI to determine the best next action.

### Preconditions
- Alarm has triggered (from UC-02)
- User has not completed verification task within timeout (e.g., 2 minutes)
- OR user completed task but didn't respond to check-in (5-10 min check-in)
- User's escalation preferences are configured
- System has access to at least one escalation tool (call, SMS, Alexa, etc.)
- User profile and past escalation history exist in database

### Trigger
Either:
- Alarm triggered 2+ minutes ago with no verification task completion
- OR Check-in reminder sent but no response within 5 minutes

### Main Success Scenario

#### Phase 1: Gather User Context
1. System retrieves user profile:
   - Past alarm behaviors (response times)
   - Which escalation methods worked previously
   - Current time and day of week
   - User's typical wake-up patterns
   - Sleep quality history
2. System accesses escalation history log:
   - Previous escalations and outcomes
   - Which method successfully woke user
   - Time of day patterns

#### Phase 2: AI Decision Making
3. System sends request to Claude LLM with context:
   - "User [name] did not respond to alarm at [time]"
   - "Previous escalations that worked: [list]"
   - "Failed escalations: [list]"
   - "User's availability: [phone/SMS/Alexa enabled]"
   - "Available escalation methods: [call/SMS/Alexa/emergency contact]"
4. Claude AI analyzes and decides:
   - Which escalation method has highest success rate for this user
   - Optimal timing (immediate or wait 30 sec)
   - Message content (personalized based on user preferences)
   - Fallback if primary method fails
5. System returns decision with confidence score

#### Phase 3: Execute Escalation
6. Based on AI decision, system selects escalation method:

**If Call Selected:**
7a. System initiates phone call via Twilio
7b. System plays pre-recorded or TTS message: "This is your RiseUp alarm. Please confirm you are awake by pressing 1."
7c. User presses 1 to confirm
7d. System records confirmation and stops escalation
7e. Proceed to step 10

**If SMS Selected:**
7b. System sends SMS via Twilio: "RiseUp: Your alarm has triggered. Reply AWAKE to confirm."
7c. User replies with "AWAKE" or similar
7d. System detects response and records confirmation
7e. Proceed to step 10

**If Alexa Selected:**
7c. System sends request to Alexa API
7d. Alexa announces: "Your RiseUp alarm has triggered. Say yes to confirm you're awake."
7e. User says "yes" or similar
7f. System detects voice response and records confirmation
7g. Proceed to step 10

**If Emergency Contact Selected:**
7h. System calls/texts emergency contact: "RiseUp alert: [User name] is not responding to alarm. Please check on them."
7i. Emergency contact acknowledges
7j. Proceed to step 10

#### Phase 4: Completion
8. System logs escalation event:
   - Method used
   - Time of escalation
   - User response time
   - Outcome (successful/failed)
9. System updates user profile with escalation effectiveness data
10. System notifies user (if they respond) of escalation taken
11. Escalation ends

### Alternative Flows

#### A1: Primary Escalation Method Fails
- System attempts call via Twilio
- Call connection fails (user phone off, unreachable)
- System waits 30 seconds
- System attempts fallback method (SMS instead)
- User responds to SMS
- Continue from step 8 in main flow

#### A2: Multiple Escalation Attempts Needed
- First escalation attempt (call) → No response
- System waits 1 minute
- Second escalation attempt (SMS) → No response
- System waits 2 minutes
- Third attempt (Alexa alert) → User responds
- System logs all attempts and outcomes
- Continue from step 8 in main flow

#### A3: User Disables Escalation
- User has disabled all escalation methods in settings
- Alarm triggers but times out without verification
- System displays on user's dashboard: "Alarm missed at 7:00 AM"
- No escalation occurs
- System logs missed alarm for user review

#### A4: Multiple Alarms Trigger Simultaneously
- User has two alarms set 5 minutes apart
- Both trigger within short window
- System prioritizes based on alarm type/importance
- Escalates the higher-priority alarm first
- Delays second escalation by 30-60 seconds

### Exception Flows

#### E1: LLM Decision Fails
- System sends request to Claude API
- API timeout or failure occurs
- System falls back to rule-based escalation:
  - Use method that worked last time
  - If no history, default to: call → SMS → Alexa
- Continue with Phase 3

#### E2: All Escalation Methods Unavailable
- User has disabled all escalation methods
- Call, SMS, Alexa all disabled
- User has no emergency contact
- System logs escalation failure
- System displays alert on user's dashboard: "Alarm at 7:00 AM went unanswered"
- User receives in-app notification on next login

#### E3: User Rejects Escalation
- System calls user
- User explicitly says "do not call again" or ignores multiple attempts
- System detects rejection pattern
- System updates user's escalation settings
- System respects user preferences for future alarms

#### E4: Emergency Contact Unreachable
- Emergency contact selected as escalation method
- Contact's phone is off or unavailable
- System attempts 3 times with 30-sec delays
- On final failure, system falls back to SMS or logs failure
- System notifies user of failed emergency contact

### Postconditions
- Escalation method has been attempted (at least once)
- Escalation outcome is recorded in database
- User profile updated with escalation effectiveness data
- AI learning engine updates escalation strategy for future use
- If successful: User has responded and confirmed wakefulness
- If failed: User dashboard shows missed alarm with escalation attempts

### Notes
- AI learns which escalation methods work best for each user
- Escalation decisions improve over time with historical data
- System respects user privacy (no escalation if all methods disabled)
- Emergency contact is last resort, used rarely
- Escalation logs help identify users who consistently miss alarms
- System can suggest alarm time/verification task changes to user

---

## UC-04: User Creates a Task Reminder

### Use Case ID
UC-04

### Use Case Name
Create Task Reminder

### Primary Actor
User

### Secondary Actors
- System (Task Scheduler)
- LLM (Claude - for natural language parsing)

### Goal
User creates a task reminder and the system schedules it to notify the user at the appropriate time.

### Preconditions
- User is logged into RiseUp
- User has access to web app or voice interface
- System has current time and user's timezone

### Trigger
User clicks "New Reminder" or "Add Task" or speaks voice command to system.

### Main Success Scenario

#### Via Web App:
1. User clicks "New Reminder" button on dashboard
2. System displays task creation form with fields:
   - Task title/description
   - Due date
   - Due time
   - Priority (low/medium/high/critical)
   - Category (work, personal, health, other)
   - Notification type (push, SMS, call, email)
3. User enters task description (e.g., "Call mom")
4. User sets due date (today, tomorrow, specific date)
5. User sets due time (HH:MM)
6. User selects priority level
7. User optionally selects category
8. User optionally adds notes or description
9. User clicks "Save Reminder"
10. System validates task data
11. System schedules reminder notification at due time
12. System displays confirmation: "Reminder set for [date] at [time]"
13. Reminder appears on dashboard in task list
14. Task is stored in database

#### Via Voice:
1. User says "Hey RiseUp, remind me to [task] at [time]"
2. System captures voice input
3. LLM parses natural language:
   - Extracts task: "call mom"
   - Extracts time: "tomorrow at 9am"
   - Infers priority: medium (default)
   - Infers category: personal
4. System confirms: "Reminder set to call mom tomorrow at 9 AM. Correct?"
5. User confirms "yes"
6. Continue from step 10 in main flow

### Alternative Flows

#### A1: User Creates Recurring Task
- At step 5, user sets repeat pattern: daily, weekly, weekdays, custom
- System schedules multiple reminders (one per occurrence)
- User can edit or delete recurring series
- Each occurrence is logged separately for completion tracking

#### A2: User Sets Multiple Reminders for Same Task
- User sets primary reminder at 9 AM
- User clicks "Add another reminder time"
- User sets secondary reminder at 9:30 AM (escalation reminder)
- System creates two separate notifications
- Both reminders appear on dashboard

#### A3: User Cancels Task Creation
- User fills form but clicks "Cancel"
- No reminder is saved
- User returns to dashboard
- No data loss

#### A4: User Edits Existing Task Reminder
- User clicks "Edit" on existing reminder
- System loads task details into form
- User modifies due time, priority, category
- User clicks "Save"
- System updates database
- Notification schedule is recalculated if time changed

#### A5: User Marks Task as Complete
- Task notification is sent
- User clicks "Complete" button or says "done"
- System marks task as completed
- Task moves to completed list
- Completion time is recorded for analytics

### Exception Flows

#### E1: Invalid Task Title
- User leaves task title blank
- User clicks "Save Reminder"
- System displays error: "Task title is required"
- User must enter task title
- Continue from step 9 in main flow

#### E2: Invalid Time Entry
- User enters invalid time format
- System displays error: "Please enter time in HH:MM format"
- User corrects entry
- Continue from step 9 in main flow

#### E3: Past Time/Date Entry
- User sets reminder for 8 AM today, but current time is 9 AM
- System detects past time
- System offers: "This time has passed. Set for tomorrow instead?"
- User confirms or sets new time
- Continue from step 10 in main flow

#### E4: Voice Parse Fails
- User says reminder with ambiguous wording
- LLM cannot extract clear task or time
- System asks for clarification: "Did you say remind you to [X]? At what time?"
- User provides missing information
- LLM re-parses
- If successful, continue from step 10 in main flow

### Postconditions
- Task reminder is stored in database
- Notification is scheduled at specified time
- Task appears on user's dashboard/task list
- User receives confirmation
- Completion tracking is enabled

### Notes
- Reminders can be set via app or voice
- AI parses natural language for convenience
- Users can set recurring reminders
- Completion data helps system learn user patterns
- Failed/missed reminders are logged for analytics

---

## UC-05: System Sends Prioritized Task Reminders

### Use Case ID
UC-05

### Use Case Name
Send Prioritized Task Reminders

### Primary Actor
System (Task Scheduler & AI Prioritizer)

### Secondary Actors
- User (recipient)
- LLM (Claude - for prioritization decision)

### Goal
At scheduled time, system intelligently prioritizes and sends task reminders to maximize user engagement.

### Preconditions
- Task reminders have been created (UC-04)
- Current time matches or approaches scheduled reminder time
- User is available to receive notification
- System has user's notification preferences configured

### Trigger
System's scheduler detects that reminder notification time has arrived.

### Main Success Scenario

#### Phase 1: Reminder Trigger Check
1. System checks if multiple reminders are due simultaneously
2. If only one reminder:
   - Go to step 5 (single reminder flow)
3. If multiple reminders at same time:
   - Continue to step 4 (prioritization flow)

#### Phase 2: AI Prioritization (if multiple)
4. System sends request to Claude LLM:
   - "User has 3 reminders due now: [list]"
   - "User priority levels: [task1]=high, [task2]=medium, [task3]=low"
   - "User's typical task completion rates: work=80%, personal=50%, health=90%"
   - "Time of day: [morning/afternoon/evening]"
   - "Recent task completion history: [data]"
   - Decision: "Which task should be prioritized?"
5. Claude analyzes and recommends priority order:
   - Health task (highest completion rate, high priority)
   - Work task (medium priority, work hours)
   - Personal task (low priority, low completion rate)
6. System orders reminders by AI recommendation

#### Phase 3: Send Primary Reminder
7. System determines notification method based on user preference:
   - Push notification (default)
   - SMS
   - Email
   - Combination
8. System sends primary reminder:
   - Content: "[Task name]: [Description]"
   - Example: "Call Mom: Don't forget to call your mom"
   - Includes completion button/action
9. System starts timer (track user response time)
10. User receives reminder notification
11. If user completes task immediately → Log completion, end flow
12. If user doesn't respond within 5 minutes → Go to step 13 (escalation reminder)

#### Phase 4: Escalation Reminder (if needed)
13. System sends secondary "escalation" reminder:
    - Slightly different tone/message
    - Example: "Hey! Still need to call Mom. Tap to complete."
    - Sent 5 minutes after primary reminder
14. If user completes task → Log completion, end flow
15. If user still doesn't respond within 10 more minutes → Go to step 16 (final reminder)

#### Phase 5: Final Reminder
16. System sends final reminder:
    - More urgent tone
    - Example: "Last reminder: Call Mom (high priority). Complete now?"
    - Sent 15 minutes after primary reminder
17. If user completes → Log completion, end flow
18. If user doesn't complete → Log as missed/incomplete, end flow

#### Phase 6: Logging & Learning
19. System records:
    - Task name
    - Scheduled time vs. actual completion time
    - Number of reminders needed before completion
    - Notification method used
    - User response time
    - Task category and priority
20. System updates user profile:
    - Task completion patterns (time of day, category, priority)
    - Optimal reminder frequency for this user
    - Which notification methods are most effective
21. Data feeds into future prioritization logic

### Alternative Flows

#### A1: User Completes Task Immediately
- User receives primary reminder
- User clicks "Complete" button or marks done
- System logs completion at reminder time (or close to it)
- No escalation reminders sent
- Data recorded for analytics
- Continue from step 19

#### A2: User Snoozes Reminder
- User receives reminder
- User clicks "Snooze 15 min"
- Reminder is rescheduled for 15 min later
- At new time, primary reminder is resent
- Continue from step 9 (restart timer for new time)

#### A3: User Marks Task as "Done Earlier"
- User receives reminder
- But task was already completed earlier in the day
- User clicks "Already done" option
- System removes reminder and logs completion
- System adjusts reminder scheduling for similar tasks

#### A4: Single Reminder with High Priority
- Only one high-priority reminder is due
- System sends immediately with urgent notification style
- No AI prioritization needed (only one task)
- Continue from step 7

#### A5: Reminder for Recurring Task
- Recurring task (e.g., "Drink water") set to send multiple times/day
- Each occurrence is sent at scheduled time
- User can mark individual occurrences as complete
- Series can be completed or skipped
- Data tracked per occurrence

### Exception Flows

#### E1: User Disabled Notifications
- Reminder is due
- User has notifications disabled in settings
- System checks last login time
- If recent: Schedule reminder for next login
- If old: Log missed reminder for user to review later

#### E2: User's Device Offline
- Reminder is due
- System attempts to send push notification
- Delivery fails (device offline)
- System queues notification
- When device comes online, queued notification is delivered
- Completion is still tracked if user completes within reasonable time

#### E3: LLM Prioritization Fails
- System sends request to Claude for prioritization
- API timeout or error occurs
- System falls back to simple prioritization:
  - Sort by: priority level → category → due time
  - Send highest priority first
- Continue with Phase 3

#### E4: User Dismisses All Reminders
- User receives 3 escalation reminders
- User dismisses each without completing task
- System logs as "missed/incomplete"
- On dashboard, task appears as "incomplete" with red flag
- AI learns that this task/category has low completion rate
- System may suggest: "You often miss [category] tasks. Change reminder time?"

### Postconditions
- Reminder notification has been sent to user
- Completion status is recorded (completed/incomplete/missed)
- Response time is logged
- User behavior data is stored for learning engine
- Task appears on user's dashboard with completion status
- AI prioritization logic is updated with new data

### Notes
- Multiple reminders prioritized by AI (not just sequential)
- Escalation reminders increase likelihood of completion
- System learns optimal reminder frequency per user
- Missed reminders help identify problematic tasks or times
- Completion data drives future task scheduling recommendations
- Privacy: All data is tied to user's own profile, not shared

---

## Use Case Diagram

```
                                    ┌─────────────────┐
                                    │      User       │
                                    └────────┬────────┘
                                             │
                     ┌───────────────────────┼───────────────────────┐
                     │                       │                       │
                     ▼                       ▼                       ▼
            ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
            │  UC-01: Config   │   │  UC-04: Create   │   │  UC-02: Complete │
            │  Alarm Settings  │   │  Task Reminder   │   │  Verification    │
            └──────────────────┘   └──────────────────┘   └──────────────────┘
                     │                       │                       │
                     └───────────────────────┼───────────────────────┘
                                             │
                                             ▼
                                    ┌──────────────────┐
                                    │  UC-03: AI       │
                                    │  Escalation      │
                                    └────────┬─────────┘
                                             │
                                             ▼
                                    ┌──────────────────┐
                                    │  UC-05: Send     │
                                    │  Prioritized     │
                                    │  Reminders       │
                                    └──────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Secondary Actors                                               │
├─────────────────────────────────────────────────────────────────┤
│  • System (Alarm Scheduler, Task Scheduler)                     │
│  • LLM (Claude - for AI reasoning, prioritization, escalation)  │
│  • External Tools (Twilio for SMS/calls, Alexa SDK, etc.)       │
│  • Database (Store alarms, tasks, user profiles, logs)          │
│  • Device Services (Camera for object detection, microphone)    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Use Case Dependencies

- **UC-01** is prerequisite for **UC-02** (alarm must exist before it triggers)
- **UC-02** triggers **UC-03** if user doesn't respond (alarm timeout → escalation)
- **UC-02** triggers check-in, which if missed, triggers **UC-03**
- **UC-04** is independent (users can create reminders anytime)
- **UC-05** requires **UC-04** (reminders must exist before they're sent)
- **UC-03** and **UC-05** both depend on LLM for AI-driven decisions

---

## Summary Table

| Use Case | Actor | Goal | Trigger | Key Decision Point |
|----------|-------|------|---------|-------------------|
| UC-01 | User | Set customized alarm | Click "New Alarm" | Verification task type, repeat pattern |
| UC-02 | User | Complete verification | Alarm triggers | Task type success, check-in response |
| UC-03 | System | Escalate intelligently | No task completion | AI decides escalation method |
| UC-04 | User | Create task reminder | Click "New Reminder" | Priority, category, time |
| UC-05 | System | Send prioritized reminders | Reminder time arrives | AI prioritizes if multiple reminders |