import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { Recommendation } from "../api/types";
import { Button, Card, Empty, ErrorNote, PageHeader } from "../components/ui";
import { useAction, useApi } from "../hooks/useApi";

function describeChange(change: Record<string, unknown>): string {
  return Object.entries(change)
    .map(([key, value]) => `${key.replace(/_/g, " ")} → ${Array.isArray(value) ? value.join(", ") : String(value)}`)
    .join("; ");
}

export default function Recommendations() {
  const list = useApi(() => api.insights.recommendations());
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const run = useAction();

  const generate = async () => {
    setBusy(true);
    const result = await run(() => api.insights.generate());
    setBusy(false);
    if (result) {
      setMessage(`${result.message} (source: ${result.source})`);
      void list.reload();
    }
  };

  const act = async (rec: Recommendation, accept: boolean) => {
    const done = await run(
      () => (accept ? api.insights.accept(rec.id) : api.insights.dismiss(rec.id)),
      accept ? "Applied to your settings" : "Dismissed",
    );
    if (done) void list.reload();
  };

  return (
    <div>
      <PageHeader
        title="Tips"
        subtitle="Personalized setting changes based on what has worked for you."
        actions={(
          <Button onClick={generate} disabled={busy}>{busy ? "Analyzing…" : "Get new suggestions"}</Button>
        )}
      />
      <Card>
        <ErrorNote message={list.error} />
        {message && (
          <p className="mb-4 rounded-xl border border-line bg-surface-sunk px-3 py-2 text-sm text-ink-soft">{message}</p>
        )}
        {list.data?.length === 0 && (
          <Empty>
            No pending suggestions.{" "}
            <button type="button" className="font-medium text-brand-600 underline" onClick={generate}>
              Generate some
            </button>
            {" "}or check your <Link className="font-medium text-brand-600 underline" to="/insights">insights</Link>.
          </Empty>
        )}
        <ul className="space-y-3">
          {list.data?.map((rec) => (
            <li key={rec.id} className="rounded-2xl border border-line bg-surface-sunk/50 p-4">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <div className="font-medium">{rec.title}</div>
                  <p className="mt-1 text-sm text-ink-soft">{rec.rationale}</p>
                  <p className="mt-2 text-xs text-ink-muted">
                    Change: {describeChange(rec.change)} · {rec.source}
                  </p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Button onClick={() => act(rec, true)}>Accept</Button>
                  <Button variant="secondary" onClick={() => act(rec, false)}>Dismiss</Button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
