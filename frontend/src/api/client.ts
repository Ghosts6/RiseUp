import axios, { AxiosError } from "axios";
import type {
  Alarm, AlarmInput, AppNotification, ChallengeState, Contact, EscalationAttempt,
  EscalationStatus, Explanation, Health, HistoryPage, ParseResult, PrefsPayload,
  PriorityPreview, Profile, Recommendation, Reminder, ReminderInput, Stats, VerifyResult,
} from "./types";

export const API_URL: string = import.meta.env.VITE_API_URL ?? "http://localhost:8000";
export const API_TOKEN: string = import.meta.env.VITE_API_TOKEN ?? "dev-token";

const http = axios.create({
  baseURL: `${API_URL}/api`,
  headers: { Authorization: `Bearer ${API_TOKEN}` },
  timeout: 15000,
});

export class ApiError extends Error {
  constructor(message: string, public status: number, public detail?: Record<string, unknown>) {
    super(message);
  }
}

export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return String(err);
}

http.interceptors.response.use(
  (res) => res,
  (err: AxiosError<Record<string, unknown>>) => {
    if (!err.response) {
      return Promise.reject(new ApiError(`Cannot reach RiseUp server at ${API_URL}`, 0));
    }
    const body = err.response.data ?? {};
    const raw = body.detail ?? body.message ?? err.message;
    const message = typeof raw === "string" ? raw
      : Array.isArray(raw) ? raw.map((d: { msg?: string }) => d.msg ?? JSON.stringify(d)).join("; ")
      : JSON.stringify(raw);
    return Promise.reject(new ApiError(message, err.response.status, body));
  },
);

const get = <T>(url: string, params?: object) => http.get<T>(url, { params }).then((r) => r.data);
const post = <T>(url: string, body?: unknown, params?: object) =>
  http.post<T>(url, body, { params }).then((r) => r.data);
const patch = <T>(url: string, body?: unknown, params?: object) =>
  http.patch<T>(url, body, { params }).then((r) => r.data);
const del = (url: string) => http.delete(url).then(() => undefined);

export const api = {
  health: () => get<Health>("/health"),
  notifications: () => get<AppNotification[]>("/notifications"),
  markNotificationsRead: () => post("/notifications/read"),

  alarms: {
    list: () => get<Alarm[]>("/alarms"),
    get: (id: number) => get<Alarm>(`/alarms/${id}`),
    create: (body: AlarmInput) => post<Alarm>("/alarms", body),
    update: (id: number, body: AlarmInput) => patch<Alarm>(`/alarms/${id}`, body),
    remove: (id: number) => del(`/alarms/${id}`),
    toggle: (id: number, active?: boolean) => post<Alarm>(`/alarms/${id}/toggle`, { active }),
    trigger: (id: number) => post<Alarm>(`/alarms/${id}/trigger`),
    challenge: (id: number) => get<ChallengeState>(`/alarms/${id}/challenge`),
    verify: (id: number, body: { answer?: string; completed?: boolean; image_base64?: string; camera_error?: boolean }) =>
      post<VerifyResult>(`/alarms/${id}/verify`, body),
    snooze: (id: number) => post<Alarm>(`/alarms/${id}/snooze`),
    escalation: (id: number) => get<EscalationStatus>(`/alarms/${id}/escalation`),
  },

  checkins: {
    confirm: () => post<{ status: string; message: string }>("/checkins/confirm"),
  },

  escalations: {
    list: () => get<EscalationAttempt[]>("/escalations"),
    acknowledge: () => post<{ message: string }>("/escalations/acknowledge", { via: "dashboard" }),
  },

  reminders: {
    list: (filters: { status?: string; category?: string; priority?: string } = {}) =>
      get<Reminder[]>("/reminders", filters),
    create: (body: ReminderInput, source: "form" | "nl" = "form") =>
      post<Reminder>("/reminders", body, { source }),
    parse: (text: string) => post<ParseResult>("/reminders/parse", { text }),
    update: (id: number, body: ReminderInput, scope: "this" | "all" = "this") =>
      patch<Reminder>(`/reminders/${id}`, body, { scope }),
    remove: (id: number) => del(`/reminders/${id}`),
    complete: (id: number, alreadyDone = false) =>
      post<Reminder>(`/reminders/${id}/complete`, { already_done: alreadyDone }),
    snooze: (id: number, minutes?: number) => post<Reminder>(`/reminders/${id}/snooze`, { minutes }),
    priorityPreview: () => get<PriorityPreview>("/reminders/priority-preview"),
  },

  prefs: {
    get: () => get<PrefsPayload>("/prefs"),
    update: (body: Record<string, unknown>) => patch<PrefsPayload>("/prefs", body),
    test: (channel: string) => post<{ success: boolean; channel: string; detail: string }>("/prefs/test", { channel }),
  },

  contacts: {
    add: (name: string, phone: string) => post<Contact>("/contacts", { name, phone }),
    remove: (id: number) => del(`/contacts/${id}`),
    test: (id: number) => post<{ success: boolean; detail: string }>(`/contacts/${id}/test`),
  },

  insights: {
    profile: () => get<Profile>("/profile"),
    recompute: () => post<Profile>("/profile/recompute"),
    stats: (range: string) => get<Stats>("/stats", { range }),
    recommendations: () => get<Recommendation[]>("/recommendations"),
    generate: () => post<{ items: Recommendation[]; source: string; message: string }>("/recommendations/generate"),
    accept: (id: number) => post<Recommendation>(`/recommendations/${id}/accept`),
    dismiss: (id: number) => post<Recommendation>(`/recommendations/${id}/dismiss`),
  },

  history: {
    list: (params: { type?: string; since?: string; page?: number; page_size?: number }) =>
      get<HistoryPage>("/history", params),
    explain: (kind: string, id: number) => get<Explanation>(`/history/${kind}/${id}`),
  },
};

export function wsUrl(): string {
  return `${API_URL.replace(/^http/, "ws")}/ws?token=${encodeURIComponent(API_TOKEN)}`;
}
