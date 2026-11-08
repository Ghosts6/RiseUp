import { useState } from "react";
import { api, errorMessage } from "../api/client";
import type { Explanation, HistoryItem } from "../api/types";
import { Badge, Button, Card, Empty, ErrorNote, PageHeader, formatWhen, inputClass, Modal, percent } from "../components/ui";
import { useApi } from "../hooks/useApi";

const KINDS = ["", "alarm", "checkin", "escalation", "reminder", "prioritization"];
const SINCE = ["7d", "30d", "90d"];

function ExplanationView({ data }: { data: Explanation }) {
  return (
    <div className="space-y-4 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <Badge value={data.fallback_used ? "pending" : "ok"}>{data.label}</Badge>
        <span className="text-xs text-ink-muted">{formatWhen(data.at)}</span>
      </div>
      {data.kind === "escalation" && (
        <dl className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <div><dt className="text-xs text-ink-muted">Alarm</dt><dd>{data.alarm ? `${data.alarm.label} (${data.alarm.time})` : "-"}</dd></div>
          <div><dt className="text-xs text-ink-muted">Trigger</dt><dd>{data.trigger} (attempt {data.attempt})</dd></div>
          <div><dt className="text-xs text-ink-muted">Method chosen</dt><dd className="font-medium">{data.method ?? "-"}</dd></div>
          <div><dt className="text-xs text-ink-muted">Confidence</dt><dd>{percent(data.confidence)}</dd></div>
          <div><dt className="text-xs text-ink-muted">Outcome</dt><dd>{data.outcome}</dd></div>
          {data.error && <div><dt className="text-xs text-ink-muted">Error</dt><dd>{data.error}</dd></div>}
        </dl>
      )}
      {data.kind === "prioritization" && data.titles && (
        <div>
          <div className="text-xs text-ink-muted">Order chosen</div>
          <ol className="list-decimal pl-5">{data.titles.map((t, i) => <li key={i}>{t}</li>)}</ol>
        </div>
      )}
      <div>
        <div className="text-xs text-ink-muted">Reasoning</div>
        <p className="rounded-xl bg-surface-sunk p-3">{data.reasoning || "-"}</p>
      </div>
      <details>
        <summary className="cursor-pointer text-xs text-ink-muted">Inputs the decision was based on</summary>
        <pre className="mt-2 overflow-x-auto rounded-xl bg-ink p-3 text-xs text-surface">
          {JSON.stringify(data.inputs, null, 2)}
        </pre>
      </details>
    </div>
  );
}

export default function History() {
  const [kind, setKind] = useState("");
  const [since, setSince] = useState("7d");
  const [page, setPage] = useState(1);
  const history = useApi(() => api.history.list({ type: kind || undefined, since, page, page_size: 20 }), [kind, since, page]);
  const [explanation, setExplanation] = useState<Explanation | null>(null);
  const [explainError, setExplainError] = useState<string | null>(null);

  const explain = async (item: HistoryItem) => {
    setExplainError(null);
    try {
      setExplanation(await api.history.explain(item.kind, item.ref_id));
    } catch (err) {
      setExplainError(errorMessage(err));
    }
  };

  const data = history.data;
  return (
    <div>
      <PageHeader title="History" subtitle="Searchable log of alarms, check-ins, escalations, and AI decisions." />
      <Card>
        <div className="mb-4 flex flex-wrap gap-2">
          <select className={`${inputClass} w-full sm:w-auto`} value={kind} onChange={(e) => { setKind(e.target.value); setPage(1); }}>
            {KINDS.map((k) => <option key={k} value={k}>{k ? k : "All events"}</option>)}
          </select>
          <select className={`${inputClass} w-full sm:w-auto`} value={since} onChange={(e) => { setSince(e.target.value); setPage(1); }}>
            {SINCE.map((s) => <option key={s} value={s}>Last {s}</option>)}
          </select>
        </div>
        <ErrorNote message={history.error ?? explainError} />
        {data?.items.length === 0 && <Empty>No events in this period.</Empty>}

        {data && data.items.length > 0 && (
          <>
            <ul className="space-y-3 md:hidden">
              {data.items.map((item) => (
                <li key={`${item.kind}-${item.ref_id}`} className="rounded-2xl border border-line bg-surface-sunk/60 p-3">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="text-xs capitalize text-ink-muted">{item.kind} · {formatWhen(item.at)}</div>
                      <div className="mt-1 font-medium">{item.title}</div>
                      <div className="text-sm text-ink-muted">{item.summary}</div>
                    </div>
                    <Badge value={item.status} />
                  </div>
                  {item.explainable && (
                    <Button variant="ghost" className="mt-2" onClick={() => explain(item)}>Why?</Button>
                  )}
                </li>
              ))}
            </ul>

            <div className="hidden overflow-x-auto md:block">
              <table className="w-full min-w-[40rem] text-sm">
                <thead className="text-left text-xs uppercase tracking-wide text-ink-muted">
                  <tr>
                    <th className="py-2 pr-3">When</th>
                    <th className="pr-3">Type</th>
                    <th className="pr-3">Event</th>
                    <th className="pr-3">Details</th>
                    <th className="pr-3">Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((item) => (
                    <tr key={`${item.kind}-${item.ref_id}`} className="border-t border-line">
                      <td className="whitespace-nowrap py-3 pr-3 text-ink-muted">{formatWhen(item.at)}</td>
                      <td className="pr-3 capitalize">{item.kind}</td>
                      <td className="pr-3">{item.title}</td>
                      <td className="pr-3 text-ink-muted">{item.summary}</td>
                      <td className="pr-3"><Badge value={item.status} /></td>
                      <td className="text-right">
                        {item.explainable && <Button variant="ghost" onClick={() => explain(item)}>Why?</Button>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        {data && data.pages > 1 && (
          <div className="mt-4 flex items-center justify-end gap-2 text-sm">
            <Button variant="secondary" disabled={page <= 1} onClick={() => setPage(page - 1)}>Previous</Button>
            <span>Page {data.page} / {data.pages}</span>
            <Button variant="secondary" disabled={page >= data.pages} onClick={() => setPage(page + 1)}>Next</Button>
          </div>
        )}
        <Modal title="Why did RiseUp do this?" open={!!explanation} onClose={() => setExplanation(null)}>
          {explanation && <ExplanationView data={explanation} />}
        </Modal>
      </Card>
    </div>
  );
}
