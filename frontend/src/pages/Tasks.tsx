import { zodResolver } from "@hookform/resolvers/zod";
import { FormEvent, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api, errorMessage } from "../api/client";
import type { ParseResult, Priority, Reminder } from "../api/types";
import { Badge, Button, Card, Empty, ErrorNote, Field, PageHeader, inputClass } from "../components/ui";
import { useAction, useApi } from "../hooks/useApi";
import { useAppStore } from "../store/useAppStore";

const PRIORITIES: Priority[] = ["low", "medium", "high", "critical"];
const CATEGORIES = ["work", "personal", "health", "other"];
const REPEATS = ["none", "daily", "weekly", "weekdays"];
const NOTIFY = ["push", "sms", "call", "email"] as const;

const draftSchema = z.object({
  title: z.string().trim().min(1, "Title is required").max(120),
  due: z.string().min(1, "Pick a date and time"),
  priority: z.enum(["low", "medium", "high", "critical"]),
  category: z.enum(["work", "personal", "health", "other"]),
  repeat_pattern: z.enum(["none", "daily", "weekly", "weekdays"]),
  notification_type: z.enum(["push", "sms", "call", "email"]),
  extra_times_text: z.string().optional(),
});
type Draft = z.infer<typeof draftSchema>;

const EMPTY_DRAFT: Draft = {
  title: "", due: "", priority: "medium", category: "other", repeat_pattern: "none",
  notification_type: "push", extra_times_text: "",
};

function toDatetimeLocal(value: string): string {
  // Accept "YYYY-MM-DD HH:MM", "YYYY-MM-DDTHH:MM", or full ISO.
  const normalized = value.includes("T") ? value : value.replace(" ", "T");
  return normalized.slice(0, 16);
}

function parseExtraTimes(text: string | undefined): string[] {
  if (!text?.trim()) return [];
  return text.split(",").map((t) => t.trim()).filter(Boolean).map((t) => toDatetimeLocal(t));
}

function reminderToDraft(r: Reminder): Draft {
  return {
    title: r.title,
    due: toDatetimeLocal(r.due_local || r.due_at),
    priority: r.priority,
    category: (CATEGORIES.includes(r.category) ? r.category : "other") as Draft["category"],
    repeat_pattern: (REPEATS.includes(r.repeat_pattern) ? r.repeat_pattern : "none") as Draft["repeat_pattern"],
    notification_type: (NOTIFY.includes(r.notification_type as Draft["notification_type"])
      ? r.notification_type : "push") as Draft["notification_type"],
    extra_times_text: (r.extra_times_local ?? []).join(", "),
  };
}

function toReminderInput(draft: Draft) {
  const extras = parseExtraTimes(draft.extra_times_text);
  return {
    title: draft.title,
    due: draft.due,
    priority: draft.priority,
    category: draft.category,
    repeat_pattern: draft.repeat_pattern,
    notification_type: draft.notification_type,
    ...(extras.length ? { extra_times: extras } : { extra_times: [] }),
  };
}

function draftFromParse(parsed: ParseResult, fallbackText: string): Draft {
  const d = parsed.draft;
  return {
    title: (d?.title ?? fallbackText).trim() || fallbackText,
    due: d?.due ? toDatetimeLocal(d.due) : "",
    priority: (d?.priority && PRIORITIES.includes(d.priority) ? d.priority : "medium"),
    category: (d?.category && CATEGORIES.includes(d.category) ? d.category : "other") as Draft["category"],
    repeat_pattern: (d?.repeat_pattern && REPEATS.includes(d.repeat_pattern)
      ? d.repeat_pattern : "none") as Draft["repeat_pattern"],
    notification_type: "push",
    extra_times_text: "",
  };
}

function DraftForm({ initial, submitLabel, onSubmit, onCancel, recurringScope }: {
  initial: Draft;
  submitLabel: string;
  onSubmit: (d: Draft, scope: "this" | "all") => Promise<void>;
  onCancel?: () => void;
  recurringScope?: boolean;
}) {
  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<Draft>({ resolver: zodResolver(draftSchema), defaultValues: initial });
  const [scope, setScope] = useState<"this" | "all">("this");
  return (
    <form onSubmit={handleSubmit((d) => onSubmit(d, scope))} className="grid gap-3 sm:grid-cols-2">
      <Field label="Title" error={errors.title?.message}>
        <input className={inputClass} {...register("title")} />
      </Field>
      <Field label="Due" error={errors.due?.message}>
        <input type="datetime-local" className={inputClass} {...register("due")} />
      </Field>
      <Field label="Priority">
        <select className={inputClass} {...register("priority")}>
          {PRIORITIES.map((p) => <option key={p}>{p}</option>)}
        </select>
      </Field>
      <Field label="Category">
        <select className={inputClass} {...register("category")}>
          {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
        </select>
      </Field>
      <Field label="Repeat">
        <select className={inputClass} {...register("repeat_pattern")}>
          {REPEATS.map((r) => <option key={r}>{r}</option>)}
        </select>
      </Field>
      <Field label="Notify via" hint="Primary channel for this reminder (must also be enabled in Settings).">
        <select className={inputClass} {...register("notification_type")}>
          {NOTIFY.map((n) => <option key={n} value={n}>{n}</option>)}
        </select>
      </Field>
      <Field label="Extra reminder times" hint="Optional. Comma-separated local times, e.g. 2026-10-20T17:00, 2026-10-21T09:00">
        <input className={inputClass} placeholder="2026-10-20T17:00" {...register("extra_times_text")} />
      </Field>
      {recurringScope && (
        <Field label="Apply changes to" hint="Recurring series: this occurrence or all future ones.">
          <select className={inputClass} value={scope} onChange={(e) => setScope(e.target.value as "this" | "all")}>
            <option value="this">This occurrence only</option>
            <option value="all">All future occurrences</option>
          </select>
        </Field>
      )}
      <div className="flex items-end justify-end gap-2 sm:col-span-2">
        {onCancel && <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>}
        <Button type="submit" disabled={isSubmitting}>{submitLabel}</Button>
      </div>
    </form>
  );
}

function QuickAdd({ onCreated, onManualFallback }: {
  onCreated: () => void;
  onManualFallback: (draft: Draft, reason: string) => void;
}) {
  const [text, setText] = useState("");
  const [parsed, setParsed] = useState<ParseResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const run = useAction();

  const parse = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = await api.reminders.parse(text);
      if (result.status === "failed") {
        onManualFallback(
          draftFromParse(result, text),
          result.message || "Couldn't parse that — finish the details in the form.",
        );
        setParsed(null);
        setText("");
        return;
      }
      setParsed(result);
    } catch (err) {
      // Network / LLM hard failure: open the manual form pre-filled (F07).
      onManualFallback(
        { ...EMPTY_DRAFT, title: text.trim() },
        errorMessage(err) || "Parser unavailable — use the form below.",
      );
      setParsed(null);
    } finally {
      setBusy(false);
    }
  };

  const confirm = async (draft: Draft) => {
    const created = await run(
      () => api.reminders.create(toReminderInput(draft), "nl"),
      `Reminder "${draft.title}" scheduled`,
    );
    if (created) {
      setParsed(null);
      setText("");
      onCreated();
    }
  };

  const initial: Draft | null = parsed?.draft ? draftFromParse(parsed, text) : null;

  return (
    <Card title="Quick add" className="!border-brand-500/20">
      <form onSubmit={parse} className="flex flex-col gap-2 sm:flex-row">
        <input className={inputClass} value={text} onChange={(e) => setText(e.target.value)}
          placeholder='Try "call mom tomorrow at 9am"' />
        <Button type="submit" disabled={busy || !text.trim()} className="sm:min-w-[6.5rem]">
          {busy ? "Parsing…" : "Parse"}
        </Button>
      </form>
      <ErrorNote message={error} />
      {parsed && (
        <div className="mt-4 rounded-lg border border-brand-500/25 bg-brand-500/10 p-4">
          <div className="mb-3 flex items-center justify-between text-sm">
            <span className="font-medium">
              {parsed.status === "ok" ? "Does this look right?" : parsed.question ?? parsed.message}
            </span>
            <span className="text-xs text-ink-muted">parsed by {parsed.decided_by}</span>
          </div>
          {initial
            ? <DraftForm key={JSON.stringify(initial)} initial={initial} submitLabel="Confirm"
                onSubmit={async (d) => confirm(d)} onCancel={() => setParsed(null)} />
            : <p className="text-sm text-ink-soft">Couldn't understand that. Use the form below instead.</p>}
        </div>
      )}
    </Card>
  );
}

export default function Tasks() {
  const [filters, setFilters] = useState({ status: "open", category: "", priority: "" });
  const reminders = useApi(() => api.reminders.list({
    status: filters.status, category: filters.category || undefined, priority: filters.priority || undefined,
  }), [filters]);
  const preview = useApi(() => api.reminders.priorityPreview());
  const [showForm, setShowForm] = useState(false);
  const [formInitial, setFormInitial] = useState<Draft>(EMPTY_DRAFT);
  const [editing, setEditing] = useState<Reminder | null>(null);
  const run = useAction();
  const toast = useAppStore((s) => s.toast);

  const refresh = () => { void reminders.reload(); void preview.reload(); };
  const act = async (fn: () => Promise<unknown>, msg: string) => { if (await run(fn, msg) !== undefined) refresh(); };

  const openManual = (draft: Draft, reason?: string) => {
    setEditing(null);
    setFormInitial(draft);
    setShowForm(true);
    if (reason) toast(reason, "info");
  };

  const create = async (draft: Draft) => {
    if (await run(() => api.reminders.create(toReminderInput(draft)), `Reminder "${draft.title}" scheduled`)) {
      setShowForm(false);
      setFormInitial(EMPTY_DRAFT);
      refresh();
    }
  };

  const saveEdit = async (draft: Draft, scope: "this" | "all") => {
    if (!editing) return;
    if (await run(
      () => api.reminders.update(editing.id, toReminderInput(draft), scope),
      `Updated "${draft.title}"`,
    )) {
      setEditing(null);
      refresh();
    }
  };

  const remove = (r: Reminder) => {
    if (confirm(`Delete "${r.title}"?`)) void act(() => api.reminders.remove(r.id).then(() => true), "Reminder deleted");
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tasks"
        subtitle="Natural-language reminders, priorities, and follow-ups in one place."
        actions={(
          <Button variant="secondary" onClick={() => {
            setShowForm((v) => !v);
            setEditing(null);
            if (!showForm) setFormInitial(EMPTY_DRAFT);
          }}>
            {showForm ? "Close form" : "+ Manual add"}
          </Button>
        )}
      />
      <QuickAdd onCreated={refresh} onManualFallback={openManual} />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2" title="Your list">
          {showForm && <div className="mb-4 rounded-2xl border border-line bg-surface-sunk/50 p-4">
            <DraftForm key={JSON.stringify(formInitial)} initial={formInitial} submitLabel="Add reminder"
              onSubmit={async (d) => create(d)} />
          </div>}
          {editing && (
            <div className="mb-4 rounded-2xl border border-brand-500/25 bg-brand-500/10 p-4">
              <div className="mb-2 text-sm font-medium">Edit “{editing.title}”</div>
              <DraftForm
                key={editing.id}
                initial={reminderToDraft(editing)}
                submitLabel="Save changes"
                recurringScope={editing.repeat_pattern !== "none"}
                onSubmit={saveEdit}
                onCancel={() => setEditing(null)}
              />
            </div>
          )}
          <div className="mb-3 grid grid-cols-1 gap-2 sm:flex sm:flex-wrap">
            <select className={`${inputClass} sm:w-auto`} value={filters.status}
              onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
              {["open", "scheduled", "notifying", "completed", "missed", "all"].map((v) => <option key={v}>{v}</option>)}
            </select>
            {(["category", "priority"] as const).map((key) => (
              <select key={key} className={`${inputClass} sm:w-auto`} value={filters[key]}
                onChange={(e) => setFilters({ ...filters, [key]: e.target.value })}>
                <option value="">All {key === "category" ? "categories" : "priorities"}</option>
                {(key === "category" ? CATEGORIES : PRIORITIES).map((v) => <option key={v}>{v}</option>)}
              </select>
            ))}
          </div>
          <ErrorNote message={reminders.error} />
          {reminders.data?.length === 0 && <Empty>No tasks match.</Empty>}
          <ul className="row-divider">
            {reminders.data?.map((r) => {
              const open = r.status === "scheduled" || r.status === "notifying";
              return (
                <li key={r.id} className="flex flex-col gap-3 py-4">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <div className={`font-medium ${open ? "" : "text-ink-muted line-through"}`}>{r.title}</div>
                      <div className="text-xs text-ink-muted">
                        {r.due_local} · {r.category} · {r.notification_type}
                        {r.repeat_pattern !== "none" && ` · ${r.repeat_pattern}`}
                        {r.extra_times_local?.length ? ` · also ${r.extra_times_local.join(", ")}` : ""}
                        {r.escalation_level > 0 && ` · reminder level ${r.escalation_level}`}
                      </div>
                    </div>
                    <div className="flex gap-1.5">
                      <Badge value={r.priority} />
                      <Badge value={r.status} />
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {open && <>
                      <Button variant="secondary" onClick={() => act(() => api.reminders.complete(r.id), "Marked done")}>Done</Button>
                      <Button variant="secondary" onClick={() => act(() => api.reminders.complete(r.id, true), "Marked already done")}>Already done</Button>
                      <Button variant="secondary" onClick={() => act(() => api.reminders.snooze(r.id), "Snoozed")}>Snooze</Button>
                    </>}
                    {r.status !== "completed" && (
                      <Button variant="secondary" onClick={() => { setEditing(r); setShowForm(false); }}>Edit</Button>
                    )}
                    <Button variant="ghost" onClick={() => remove(r)}>Delete</Button>
                  </div>
                </li>
              );
            })}
          </ul>
        </Card>

        <Card title="AI priority order">
          {preview.data?.order.length === 0 && <Empty>Nothing due right now.</Empty>}
          <ol className="list-decimal space-y-1 pl-5 text-sm">
            {preview.data?.order.map((r) => <li key={r.id}>{r.title} <Badge value={r.priority} /></li>)}
          </ol>
          {preview.data && preview.data.order.length > 0 && (
            <p className="mt-3 text-xs text-ink-muted">
              {preview.data.reasoning}
              <br />
              {preview.data.fallback_used ? "Rule-based fallback" : `Decided by ${preview.data.decided_by}`}
            </p>
          )}
        </Card>
      </div>
    </div>
  );
}
