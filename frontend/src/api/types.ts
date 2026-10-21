export type AlarmStatus =
  | "idle" | "scheduled" | "ringing" | "snoozed" | "escalating"
  | "awaiting_checkin" | "completed" | "missed" | "disabled";

export type TaskType = "math" | "trivia" | "custom" | "object_detection";

export interface Alarm {
  id: number;
  time: string;
  label: string | null;
  sound: string;
  vibration: string;
  repeat_pattern: string;
  snooze_enabled: boolean;
  verification_task_type: TaskType;
  custom_task_text: string | null;
  priority: number;
  is_active: boolean;
  status: AlarmStatus;
  next_trigger_at: string | null;
  next_trigger_local: string | null;
  triggered_at: string | null;
  snooze_count: number;
  escalation_episode: string | null;
}

export type AlarmInput = Partial<Pick<Alarm,
  "time" | "label" | "sound" | "vibration" | "repeat_pattern" | "snooze_enabled"
  | "verification_task_type" | "custom_task_text" | "priority" | "is_active">>;

export interface Challenge {
  type: TaskType;
  question: string;
  choices?: string[];
  input: "number" | "choice" | "confirm" | "image" | string;
  [key: string]: unknown;
}

export interface ChallengeState {
  alarm_id: number;
  status: AlarmStatus;
  label: string | null;
  triggered_at: string | null;
  snooze_available: boolean;
  challenge: Challenge | null;
}

export interface VerifyResult {
  success: boolean;
  message: string;
  challenge?: Challenge | null;
  attempts?: number;
  checkin_at?: string | null;
  seconds_to_verify?: number | null;
}

export interface EscalationAttempt {
  id: number;
  alarm_id: number | null;
  episode: string;
  attempt: number;
  trigger: string;
  method: string | null;
  decided_by: string;
  reasoning: string | null;
  confidence: number | null;
  fallback_method: string | null;
  fallback_used: boolean;
  status: string;
  error: string | null;
  created_at: string;
  sent_at: string | null;
  responded_at: string | null;
  response_time_seconds: number | null;
}

export interface EscalationStatus {
  alarm_id: number;
  status: AlarmStatus;
  episode: string | null;
  attempts: EscalationAttempt[];
}

export type Priority = "low" | "medium" | "high" | "critical";

export interface Reminder {
  id: number;
  title: string;
  notes: string | null;
  due_at: string;
  due_local: string;
  priority: Priority;
  category: string;
  repeat_pattern: string;
  series_id: string | null;
  notification_type: string;
  extra_times: string[];
  extra_times_local: string[];
  status: string;
  escalation_level: number;
  completed_at: string | null;
  snooze_count: number;
  source: string;
  created_at: string;
}

export interface ReminderInput {
  title?: string;
  notes?: string;
  due?: string;
  priority?: Priority;
  category?: string;
  repeat_pattern?: string;
  notification_type?: string;
  extra_times?: string[];
  allow_past?: boolean;
}

export interface ParseResult {
  status: "ok" | "needs_clarification" | "past_time" | "failed";
  draft: {
    title: string | null;
    due: string | null;
    priority: Priority;
    category: string;
    repeat_pattern: string;
  } | null;
  question: string | null;
  message: string;
  decided_by: string;
}

export interface PriorityPreview {
  order: Reminder[];
  reasoning: string;
  decided_by: string;
  fallback_used: boolean;
}

export interface Preferences {
  enabled_channels: string[];
  escalation_order: string[];
  notification_methods: string[];
  quiet_hours_start: string | null;
  quiet_hours_end: string | null;
  checkin_enabled: boolean;
  checkin_delay_minutes: number;
  default_task_type: TaskType;
  category_preferred_times: Record<string, string>;
  suppressed_recommendation_types: string[];
  push_token: string | null;
}

export interface User {
  id: number;
  name: string;
  email: string | null;
  phone: string | null;
  timezone: string;
}

export interface Contact {
  id: number;
  name: string;
  phone: string;
  consent_status: "pending" | "granted" | "denied" | string;
  consent_link?: string;
}

export interface PrefsPayload {
  user: User;
  preferences: Preferences;
  contacts: Contact[];
  warnings: string[];
  integrations: Record<string, string>;
  llm: Record<string, string>;
}

export interface MethodStats {
  attempts: number;
  successes: number;
  success_rate: number | null;
  avg_response_time: number | null;
}

export interface Stats {
  range_days: number;
  empty: boolean;
  wake_times: { date: string; time: string; minutes: number; task_type: string | null }[];
  avg_wake_time: string | null;
  verification: {
    triggered: number;
    verified: number;
    missed: number;
    success_rate: number | null;
    by_type: Record<string, { count: number; avg_seconds: number }>;
    fastest_task_type: string | null;
  };
  escalation: Record<string, MethodStats>;
  tasks: Record<string, { completed: number; missed: number; total: number; rate: number | null }>;
  tasks_daily: { date: string; completed: number; missed: number }[];
  checkins: Record<string, number>;
  cached: boolean;
}

export interface Profile {
  escalation_stats: Record<string, MethodStats>;
  alarm_patterns: Record<string, unknown>;
  task_completion_rates: Stats["tasks"];
  verification_stats: Record<string, unknown>;
  data_status: string;
  skipped_rows: number;
  optimal_settings: Record<string, string | null>;
  updated_at: string | null;
}

export interface Recommendation {
  id: number;
  type: string;
  title: string;
  rationale: string;
  change: Record<string, unknown>;
  source: string;
  status: string;
  created_at: string;
}

export interface HistoryItem {
  kind: "alarm" | "escalation" | "reminder" | "prioritization";
  ref_id: number;
  at: string;
  title: string;
  summary: string;
  status: string;
  explainable: boolean;
}

export interface HistoryPage {
  items: HistoryItem[];
  total: number;
  page: number;
  page_size: number;
  pages: number;
}

export interface Explanation {
  kind: "escalation" | "prioritization";
  id: number;
  at: string;
  label: string;
  reasoning: string | null;
  decided_by: string | null;
  fallback_used: boolean;
  inputs: Record<string, unknown>;
  method?: string | null;
  confidence?: number | null;
  trigger?: string;
  attempt?: number;
  outcome?: string;
  error?: string | null;
  alarm?: { id: number; label: string | null; time: string } | null;
  titles?: string[];
  order?: number[];
}

export interface AppNotification {
  id: number;
  kind: string;
  title: string;
  body: string | null;
  data: Record<string, unknown> | null;
  read: boolean;
  created_at: string;
}

export interface LiveEvent {
  type: "event";
  subject_type: "alarm" | "reminder" | "checkin" | "escalation" | string;
  subject_id: number;
  status: string;
  event: string | null;
  title: string | null;
  body: string | null;
  notification_id: number | null;
  at: string;
}

export interface Health {
  status: string;
  version: string;
  environment: string;
  integrations: Record<string, string>;
  llm: Record<string, string>;
}
