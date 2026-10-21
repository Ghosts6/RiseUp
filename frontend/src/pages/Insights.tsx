import { useState } from "react";
import {
  Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { api } from "../api/client";
import { Button, Card, Empty, ErrorNote, PageHeader, percent } from "../components/ui";
import { useAction, useApi } from "../hooks/useApi";

const RANGES = ["7d", "30d", "90d"];

function cssVar(name: string, fallback: string): string {
  if (typeof window === "undefined") return fallback;
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value ? `rgb(${value})` : fallback;
}

function minutesToHHMM(value: number): string {
  const h = Math.floor(value / 60);
  const m = Math.round(value % 60);
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-2xl border border-line bg-surface-raised/80 p-4 shadow-soft">
      <div className="text-xs uppercase tracking-wide text-ink-muted">{label}</div>
      <div className="mt-1 font-display text-2xl font-semibold tabular-nums">{value}</div>
    </div>
  );
}

export default function Insights() {
  const [range, setRange] = useState("30d");
  const stats = useApi(() => api.insights.stats(range), [range]);
  const profile = useApi(() => api.insights.profile());
  const run = useAction();
  const s = stats.data;

  const escalationData = s ? Object.entries(s.escalation).map(([method, m]) => ({
    method, attempts: m.attempts, successes: m.successes,
  })) : [];
  const categoryData = s ? Object.entries(s.tasks).map(([category, t]) => ({
    category, completed: t.completed, missed: t.missed,
  })) : [];

  const recompute = async () => {
    if (await run(() => api.insights.recompute(), "Profile recomputed")) {
      void profile.reload();
      void stats.reload();
    }
  };

  const grid = cssVar("--chart-grid", "#e2e8f0");
  const accent = cssVar("--chart-accent", "#ff6b35");
  const ok = cssVar("--chart-ok", "#10b981");
  const bad = cssVar("--chart-bad", "#ef4444");
  const mute = cssVar("--chart-mute", "#94a3b8");
  const tick = cssVar("--ink-muted", "#64748b");

  return (
    <div className="space-y-6">
      <PageHeader
        title="Insights"
        subtitle="Wake times, verification success, and what escalation channels work for you."
        actions={(
          <div className="flex flex-wrap gap-1">
            {RANGES.map((r) => (
              <Button key={r} variant={r === range ? "primary" : "secondary"} onClick={() => setRange(r)}>{r}</Button>
            ))}
            <Button variant="ghost" onClick={recompute}>Recompute</Button>
          </div>
        )}
      />
      <ErrorNote message={stats.error} />

      {s?.empty && <Card><Empty>No activity in the last {s.range_days} days yet. Use RiseUp for a while to see trends.</Empty></Card>}

      {s && !s.empty && (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <Stat label="Average wake time" value={s.avg_wake_time ?? "-"} />
            <Stat label="Verification success" value={percent(s.verification.success_rate)} />
            <Stat label="Alarms rung" value={s.verification.triggered} />
            <Stat label="Fastest task" value={s.verification.fastest_task_type ?? "-"} />
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card title="Wake-up times">
              {s.wake_times.length === 0 ? <Empty>No verified wake-ups yet.</Empty> : (
                <ResponsiveContainer width="100%" height={240}>
                  <LineChart data={s.wake_times}>
                    <CartesianGrid strokeDasharray="3 3" stroke={grid} />
                    <XAxis dataKey="date" fontSize={12} stroke={tick} />
                    <YAxis domain={["dataMin - 30", "dataMax + 30"]} tickFormatter={minutesToHHMM} fontSize={12} stroke={tick} />
                    <Tooltip formatter={(v: number) => minutesToHHMM(v)} />
                    <Line type="monotone" dataKey="minutes" name="Wake time" stroke={accent} strokeWidth={2} />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </Card>

            <Card title="Escalation methods">
              {escalationData.length === 0 ? <Empty>No escalations in this range.</Empty> : (
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={escalationData}>
                    <CartesianGrid strokeDasharray="3 3" stroke={grid} />
                    <XAxis dataKey="method" fontSize={12} stroke={tick} />
                    <YAxis allowDecimals={false} fontSize={12} stroke={tick} />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="attempts" fill={mute} radius={[6, 6, 0, 0]} />
                    <Bar dataKey="successes" fill={ok} radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </Card>

            <Card title="Tasks per day">
              {s.tasks_daily.length === 0 ? <Empty>No completed or missed tasks yet.</Empty> : (
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={s.tasks_daily}>
                    <CartesianGrid strokeDasharray="3 3" stroke={grid} />
                    <XAxis dataKey="date" fontSize={12} stroke={tick} />
                    <YAxis allowDecimals={false} fontSize={12} stroke={tick} />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="completed" stackId="t" fill={ok} />
                    <Bar dataKey="missed" stackId="t" fill={bad} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </Card>

            <Card title="Completion by category">
              {categoryData.length === 0 ? <Empty>No task outcomes yet.</Empty> : (
                <table className="w-full text-sm">
                  <thead className="text-left text-xs uppercase text-ink-muted">
                    <tr><th className="py-1">Category</th><th>Completed</th><th>Missed</th><th>Rate</th></tr>
                  </thead>
                  <tbody>
                    {Object.entries(s.tasks).map(([c, t]) => (
                      <tr key={c} className="border-t border-line">
                        <td className="py-1 capitalize">{c}</td><td>{t.completed}</td><td>{t.missed}</td><td>{percent(t.rate)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </Card>
          </div>
        </>
      )}

      {profile.data && (
        <Card title="Learned profile">
          {profile.data.data_status === "insufficient_data" && (
            <p className="mb-3 text-sm text-amber-900 dark:text-amber-100 dark:text-amber-300">Not enough data yet; suggestions improve after a few days of use.</p>
          )}
          <dl className="grid gap-2 text-sm sm:grid-cols-3">
            {Object.entries(profile.data.optimal_settings).map(([k, v]) => (
              <div key={k}>
                <dt className="text-xs uppercase text-ink-muted">{k.replace(/_/g, " ")}</dt>
                <dd className="font-medium">{v ?? "-"}</dd>
              </div>
            ))}
          </dl>
        </Card>
      )}
    </div>
  );
}
