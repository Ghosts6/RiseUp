import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api } from "../api/client";
import type { Contact, Preferences, PrefsPayload } from "../api/types";
import { Link } from "react-router-dom";
import { Badge, Button, Card, ErrorNote, Field, PageHeader, inputClass } from "../components/ui";
import { useAction, useApi } from "../hooks/useApi";

const ESCALATION_CHANNELS = ["call", "sms", "alexa", "push", "emergency_contact"];
const TEST_CHANNELS = ["call", "sms", "alexa", "push"];
const NOTIFICATION_METHODS = ["push", "sms", "call", "email"];
const CHANNEL_LABELS: Record<string, string> = {
  call: "Phone call", sms: "SMS", alexa: "Alexa", push: "Push", emergency_contact: "Emergency contact", email: "Email",
};
const PHONE_RE = /^\+?[0-9 ()-]{7,20}$/;

const contactSchema = z.object({
  name: z.string().trim().min(1, "Name is required"),
  phone: z.string().regex(PHONE_RE, "Enter a valid phone number, e.g. +14165550123"),
});
type ContactForm = z.infer<typeof contactSchema>;

function toggle(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

function move<T>(list: T[], index: number, delta: number): T[] {
  const next = [...list];
  const target = index + delta;
  if (target < 0 || target >= next.length) return next;
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

function ContactsCard({ contacts, onChange }: { contacts: Contact[]; onChange: () => void }) {
  const run = useAction();
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } =
    useForm<ContactForm>({ resolver: zodResolver(contactSchema), defaultValues: { name: "", phone: "" } });

  const add = async (v: ContactForm) => {
    if (await run(() => api.contacts.add(v.name, v.phone), "Consent request sent to contact")) {
      reset();
      onChange();
    }
  };

  return (
    <Card title="Emergency contacts">
      <p className="mb-3 text-xs text-ink-muted">
        Contacts are only used for escalation after they accept the consent request sent by SMS.
      </p>
      <ul className="mb-4 divide-y divide-line">
        {contacts.map((c) => (
          <li key={c.id} className="flex flex-wrap items-center gap-2 py-2 text-sm">
            <span className="flex-1">{c.name} <span className="text-ink-muted">{c.phone}</span></span>
            <Badge value={c.consent_status} />
            {c.consent_status === "pending" && c.consent_link && (
              <a className="text-xs text-brand-600 underline" href={c.consent_link} target="_blank" rel="noreferrer">
                consent link
              </a>
            )}
            <Button variant="secondary" disabled={c.consent_status !== "granted"}
              onClick={() => run(() => api.contacts.test(c.id), "Test message sent")}>Test</Button>
            <Button variant="ghost" onClick={async () => {
              if (confirm(`Remove ${c.name}?`) && await run(() => api.contacts.remove(c.id).then(() => true), "Contact removed")) onChange();
            }}>Remove</Button>
          </li>
        ))}
      </ul>
      <form onSubmit={handleSubmit(add)} className="grid gap-2 sm:grid-cols-3">
        <Field label="Name" error={errors.name?.message}><input className={inputClass} {...register("name")} /></Field>
        <Field label="Phone" error={errors.phone?.message}>
          <input className={inputClass} placeholder="+14165550123" {...register("phone")} />
        </Field>
        <div className="flex items-end"><Button type="submit" disabled={isSubmitting}>Add contact</Button></div>
      </form>
    </Card>
  );
}

export default function Settings() {
  const prefs = useApi<PrefsPayload>(() => api.prefs.get(), [], false);
  const [draft, setDraft] = useState<Preferences | null>(null);
  const [profile, setProfile] = useState({ name: "", phone: "", timezone: "" });
  const [saving, setSaving] = useState(false);
  const run = useAction();

  useEffect(() => {
    if (!prefs.data) return;
    setDraft(prefs.data.preferences);
    setProfile({
      name: prefs.data.user.name, phone: prefs.data.user.phone ?? "", timezone: prefs.data.user.timezone,
    });
  }, [prefs.data]);

  if (prefs.error) return <ErrorNote message={prefs.error} />;
  if (!prefs.data || !draft) return null;
  const data = prefs.data;

  const set = (patch: Partial<Preferences>) => setDraft({ ...draft, ...patch });

  const save = async () => {
    setSaving(true);
    const result = await run(() => api.prefs.update({
      enabled_channels: draft.enabled_channels,
      escalation_order: draft.escalation_order,
      notification_methods: draft.notification_methods,
      quiet_hours_start: draft.quiet_hours_start || null,
      quiet_hours_end: draft.quiet_hours_end || null,
      checkin_enabled: draft.checkin_enabled,
      checkin_delay_minutes: draft.checkin_delay_minutes,
      default_task_type: draft.default_task_type,
      name: profile.name,
      phone: profile.phone || null,
      timezone: profile.timezone,
    }), "Settings saved");
    setSaving(false);
    if (result) prefs.setData(result);
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Settings"
        subtitle="Escalation channels, quiet hours, check-ins, and emergency contacts."
        actions={<Button onClick={save} disabled={saving}>{saving ? "Saving…" : "Save changes"}</Button>}
      />
      <div className="flex flex-wrap gap-2 md:hidden">
        <Link to="/recommendations"><Button variant="secondary">Tips</Button></Link>
        <Link to="/history"><Button variant="secondary">History</Button></Link>
      </div>
      {data.warnings.map((w) => <p key={w} className="rounded-xl border border-amber-500/25 bg-amber-500/15 px-3 py-2 text-sm text-amber-900 dark:text-amber-100">{w}</p>)}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Profile">
          <div className="space-y-3">
            <Field label="Name"><input className={inputClass} value={profile.name}
              onChange={(e) => setProfile({ ...profile, name: e.target.value })} /></Field>
            <Field label="Phone" hint="Used for calls and SMS escalation.">
              <input className={inputClass} value={profile.phone}
                onChange={(e) => setProfile({ ...profile, phone: e.target.value })} /></Field>
            <Field label="Timezone" hint="IANA name, e.g. America/Toronto">
              <input className={inputClass} value={profile.timezone}
                onChange={(e) => setProfile({ ...profile, timezone: e.target.value })} /></Field>
          </div>
        </Card>

        <Card title="Escalation channels">
          <p className="mb-3 text-xs text-ink-muted">
            When you don't complete the wake-up task, the AI agent picks from enabled channels; this order is its default.
          </p>
          <ul className="space-y-2">
            {draft.escalation_order.map((channel, i) => (
              <li key={channel} className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={draft.enabled_channels.includes(channel)}
                  onChange={() => set({ enabled_channels: toggle(draft.enabled_channels, channel) })} />
                <span className="flex-1">{i + 1}. {CHANNEL_LABELS[channel]}</span>
                {data.integrations[channel] && <Badge value={data.integrations[channel]} />}
                <Button variant="ghost" onClick={() => set({ escalation_order: move(draft.escalation_order, i, -1) })}>↑</Button>
                <Button variant="ghost" onClick={() => set({ escalation_order: move(draft.escalation_order, i, 1) })}>↓</Button>
              </li>
            ))}
            {ESCALATION_CHANNELS.filter((c) => !draft.escalation_order.includes(c)).map((c) => (
              <li key={c}><Button variant="ghost" onClick={() => set({ escalation_order: [...draft.escalation_order, c] })}>
                + {CHANNEL_LABELS[c]}</Button></li>
            ))}
          </ul>
          <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-line pt-3 text-sm">
            <span className="text-ink-soft">Send test:</span>
            {TEST_CHANNELS.map((c) => (
              <Button key={c} variant="secondary"
                onClick={() => run(() => api.prefs.test(c).then((r) => {
                  if (!r.success) throw new Error(r.detail);
                  return r;
                }), `Test ${CHANNEL_LABELS[c]} sent`)}>{CHANNEL_LABELS[c]}</Button>
            ))}
          </div>
        </Card>

        <Card title="Quiet hours & check-in">
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <Field label="Quiet from"><input type="time" className={inputClass} value={draft.quiet_hours_start ?? ""}
                onChange={(e) => set({ quiet_hours_start: e.target.value || null })} /></Field>
              <Field label="Quiet until"><input type="time" className={inputClass} value={draft.quiet_hours_end ?? ""}
                onChange={(e) => set({ quiet_hours_end: e.target.value || null })} /></Field>
            </div>
            <p className="text-xs text-ink-muted">Calls, SMS and emergency contacts are not used during quiet hours.</p>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={draft.checkin_enabled}
                onChange={(e) => set({ checkin_enabled: e.target.checked })} />
              Send a "still awake?" check-in after the alarm
            </label>
            <Field label="Check-in delay (minutes)">
              <input type="number" min={5} max={10} className={inputClass} value={draft.checkin_delay_minutes}
                onChange={(e) => set({ checkin_delay_minutes: Number(e.target.value) })} />
            </Field>
            <Field label="Default wake-up task">
              <select className={inputClass} value={draft.default_task_type}
                onChange={(e) => set({ default_task_type: e.target.value as Preferences["default_task_type"] })}>
                <option value="math">Math</option>
                <option value="trivia">Trivia</option>
                <option value="object_detection">Photo</option>
                <option value="custom">Custom</option>
              </select>
            </Field>
          </div>
        </Card>

        <Card title="Reminder notifications">
          <div className="space-y-2">
            {NOTIFICATION_METHODS.map((m) => (
              <label key={m} className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={draft.notification_methods.includes(m)}
                  onChange={() => set({ notification_methods: toggle(draft.notification_methods, m) })} />
                {CHANNEL_LABELS[m]}
              </label>
            ))}
          </div>
          <div className="mt-4 border-t border-line pt-3 text-xs text-ink-muted">
            AI routing: {Object.entries(data.llm).map(([task, model]) => `${task} → ${model}`).join(" · ")}
          </div>
        </Card>
      </div>

      <ContactsCard contacts={data.contacts} onChange={() => void prefs.reload()} />
    </div>
  );
}
