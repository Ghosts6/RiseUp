import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Alarm, ServiceStatus } from "../api/types";
import { Badge, Button, Card, Empty, ErrorNote, formatWhen } from "../components/ui";
import { useAction, useApi } from "../hooks/useApi";
import { useAppStore } from "../store/useAppStore";

const TASK_LABELS: Record<string, string> = {
  math: "Math", trivia: "Trivia", custom: "Custom", object_detection: "Photo",
};

function StatusRow({ name, row }: { name: string; row: ServiceStatus }) {
  return (
    <li className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <p className="capitalize text-ink">{name}</p>
        {row.detail && <p className="truncate text-xs text-ink-muted">{row.detail}</p>}
      </div>
      <Badge value={row.status}>{row.label}</Badge>
    </li>
  );
}

function asStatus(row: ServiceStatus | string, fallbackDetail = ""): ServiceStatus {
  if (typeof row !== "string") return row;
  return {
    status: row.includes("mock") ? "mock" : row,
    label: row,
    detail: fallbackDetail || row,
  };
}

export default function Dashboard() {
  const alarms = useApi(() => api.alarms.list());
  const reminders = useApi(() => api.reminders.list({ status: "scheduled" }));
  const notifications = useApi(() => api.notifications());
  const health = useApi(() => api.health(), [], false);
  const [systemOpen, setSystemOpen] = useState(false);
  const run = useAction();
  const navigate = useNavigate();
  const setRinging = useAppStore((s) => s.setRinging);

  const toggle = async (alarm: Alarm) => {
    await run(() => api.alarms.toggle(alarm.id, !alarm.is_active));
    void alarms.reload();
  };
  const remove = async (alarm: Alarm) => {
    if (!confirm(`Delete alarm ${alarm.time}${alarm.label ? ` (${alarm.label})` : ""}?`)) return;
    await run(() => api.alarms.remove(alarm.id), "Alarm deleted");
    void alarms.reload();
  };
  const ringNow = async (alarm: Alarm) => {
    const result = await run(() => api.alarms.trigger(alarm.id));
    if (result) {
      setRinging(alarm.id);
      navigate(`/alarms/${alarm.id}/ringing`);
    }
  };

  const upcoming = (reminders.data ?? []).slice(0, 5);
  const activeCount = alarms.data?.filter((a) => a.is_active).length ?? 0;

  return (
    <div className="space-y-6">
      <section className="panel relative overflow-hidden !p-0">
        <div className="absolute inset-0 bg-gradient-to-br from-brand-500/20 via-transparent to-sky-500/10" />
        <div className="relative grid gap-4 p-5 sm:grid-cols-[1.4fr_1fr] sm:p-7">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand-600">RiseUp</p>
            <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight sm:text-4xl">
              Start the day on your terms
            </h1>
            <p className="mt-2 max-w-md text-sm text-ink-muted">
              Alarms that make you prove you're up, then learn which wake-up methods actually work for you.
            </p>
            <div className="mt-5 flex flex-wrap gap-2">
              <Link to="/alarms/new"><Button>+ New alarm</Button></Link>
              <Link to="/tasks"><Button variant="secondary">Open tasks</Button></Link>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3 self-end">
            <div className="rounded-2xl border border-line bg-surface-raised/80 p-4">
              <div className="text-xs uppercase tracking-wide text-ink-muted">Active alarms</div>
              <div className="mt-1 font-display text-3xl font-semibold tabular-nums">{activeCount}</div>
            </div>
            <div className="rounded-2xl border border-line bg-surface-raised/80 p-4">
              <div className="text-xs uppercase tracking-wide text-ink-muted">Upcoming tasks</div>
              <div className="mt-1 font-display text-3xl font-semibold tabular-nums">{upcoming.length}</div>
            </div>
          </div>
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card title="Alarms" actions={<Link to="/alarms/new"><Button>+ New</Button></Link>}>
            <ErrorNote message={alarms.error} />
            {alarms.data?.length === 0 && <Empty>No alarms yet. Create one to get started.</Empty>}
            <ul className="row-divider">
              {alarms.data?.map((alarm) => (
                <li key={alarm.id} className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center">
                  <div className={`font-display text-3xl font-semibold tabular-nums tracking-tight ${alarm.is_active ? "text-ink" : "text-ink-muted"}`}>
                    {alarm.time}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="font-medium">{alarm.label || "Alarm"}</div>
                    <div className="text-xs text-ink-muted">
                      {alarm.repeat_pattern} · {TASK_LABELS[alarm.verification_task_type]} task
                      {alarm.next_trigger_local && ` · next ${alarm.next_trigger_local}`}
                    </div>
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      <Badge value={alarm.status} />
                      <label className="inline-flex items-center gap-1.5 text-xs text-ink-soft">
                        <input
                          type="checkbox"
                          className="h-4 w-4 rounded border-line text-brand-500 focus:ring-brand-500"
                          checked={alarm.is_active}
                          onChange={() => toggle(alarm)}
                        />
                        Enabled
                      </label>
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="secondary" onClick={() => ringNow(alarm)} title="Ring now (demo)">Ring</Button>
                    <Link to={`/alarms/${alarm.id}/edit`}><Button variant="secondary">Edit</Button></Link>
                    <Button variant="ghost" onClick={() => remove(alarm)}>Delete</Button>
                  </div>
                </li>
              ))}
            </ul>
          </Card>

          <Card title="Upcoming reminders" actions={<Link to="/tasks"><Button variant="secondary">All tasks</Button></Link>}>
            <ErrorNote message={reminders.error} />
            {upcoming.length === 0 && <Empty>Nothing scheduled.</Empty>}
            <ul className="row-divider">
              {upcoming.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-3 py-3 text-sm">
                  <span className="font-medium">{r.title}</span>
                  <span className="flex shrink-0 items-center gap-2 text-ink-muted">
                    <span className="tabular-nums">{r.due_local}</span>
                    <Badge value={r.priority} />
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        </div>

        <div className="space-y-6">
          <Card title="Quick links">
            <div className="grid grid-cols-2 gap-2">
              {[
                ["History", "/history"],
                ["Tips", "/recommendations"],
                ["Insights", "/insights"],
                ["Settings", "/settings"],
              ].map(([label, to]) => (
                <Link key={to} to={to}
                  className="rounded-xl border border-line bg-surface-sunk/70 px-3 py-3 text-center text-sm font-medium text-ink-soft transition hover:border-brand-400 hover:text-brand-600">
                  {label}
                </Link>
              ))}
            </div>
          </Card>

          <Card title="Activity">
            {notifications.data?.length === 0 && <Empty>No notifications yet.</Empty>}
            <ul className="space-y-3">
              {notifications.data?.slice(0, 10).map((n) => (
                <li key={n.id} className="text-sm">
                  <div className="font-medium">{n.title}</div>
                  {n.body && <div className="text-ink-soft">{n.body}</div>}
                  <div className="text-xs text-ink-muted">{formatWhen(n.created_at)}</div>
                </li>
              ))}
            </ul>
          </Card>

          {health.data && (
            <Card title="System">
              <div className="space-y-4 text-sm">
                <div>
                  <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-muted">Runtime</p>
                  <ul className="space-y-2">
                    {Object.entries(health.data.services ?? {}).map(([name, row]) => (
                      <StatusRow key={name} name={name} row={row} />
                    ))}
                  </ul>
                </div>

                <div
                  className={`grid transition-[grid-template-rows] duration-300 ease-out ${
                    systemOpen ? "grid-rows-[1fr]" : "grid-rows-[0fr]"
                  }`}
                >
                  <div className="min-h-0 overflow-hidden">
                    <div
                      className={`space-y-4 border-t border-line/70 pt-4 transition-opacity duration-300 ${
                        systemOpen ? "opacity-100" : "opacity-0"
                      }`}
                    >
                      <div>
                        <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-muted">
                          Integrations
                        </p>
                        <ul className="space-y-2">
                          {Object.entries(
                            health.data.integrations_detail ?? health.data.integrations,
                          ).map(([name, row]) => (
                            <StatusRow key={name} name={name} row={asStatus(row)} />
                          ))}
                        </ul>
                      </div>
                      <div>
                        <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-muted">
                          LLM
                        </p>
                        <ul className="space-y-2">
                          {Object.entries(health.data.llm_detail ?? health.data.llm).map(([task, row]) => (
                            <StatusRow key={task} name={task} row={asStatus(row)} />
                          ))}
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => setSystemOpen((open) => !open)}
                  aria-expanded={systemOpen}
                  className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-line bg-surface-sunk/60 px-3 py-2 text-xs font-medium text-ink-soft transition hover:border-brand-500/40 hover:bg-brand-500/10 hover:text-brand-600 active:scale-[0.98] dark:hover:text-brand-400"
                >
                  <span>{systemOpen ? "Show less" : "Show more"}</span>
                  <svg
                    className={`h-3.5 w-3.5 transition-transform duration-300 ${systemOpen ? "rotate-180" : ""}`}
                    viewBox="0 0 20 20"
                    fill="none"
                    aria-hidden
                  >
                    <path
                      d="M5 7.5 10 12.5 15 7.5"
                      stroke="currentColor"
                      strokeWidth="1.75"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </button>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
