import { ChangeEvent, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api, errorMessage } from "../api/client";
import type { Challenge, ChallengeState, EscalationStatus } from "../api/types";
import { Badge, Button, Card, ErrorNote, formatWhen, inputClass } from "../components/ui";
import { useAction, useApi } from "../hooks/useApi";
import { AlarmAlert } from "../lib/alarmAlert";
import { useAppStore } from "../store/useAppStore";

function readAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(",")[1] ?? "");
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

export default function AlarmRinging() {
  const alarmId = Number(useParams().id);
  const navigate = useNavigate();
  const { setRinging, setAwaitingCheckin, toast } = useAppStore();
  const run = useAction();
  const state = useApi<ChallengeState>(() => api.alarms.challenge(alarmId), [alarmId]);
  const escalation = useApi<EscalationStatus>(() => api.alarms.escalation(alarmId), [alarmId]);
  const [challenge, setChallenge] = useState<Challenge | null>(null);
  const [answer, setAnswer] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const alertRef = useRef<AlarmAlert | null>(null);

  useEffect(() => {
    if (state.data) setChallenge(state.data.challenge);
  }, [state.data]);

  const ringing = state.data && (state.data.status === "ringing" || state.data.status === "escalating");

  useEffect(() => {
    if (!ringing || !state.data) {
      alertRef.current?.stop();
      alertRef.current = null;
      return;
    }
    const alert = new AlarmAlert();
    alertRef.current = alert;
    alert.start(state.data.sound ?? "classic", state.data.vibration ?? "medium");
    return () => {
      alert.stop();
      if (alertRef.current === alert) alertRef.current = null;
    };
  }, [ringing, state.data?.sound, state.data?.vibration, state.data?.alarm_id]);

  const stopAlert = () => {
    alertRef.current?.stop();
    alertRef.current = null;
  };

  const submit = async (body: Parameters<typeof api.alarms.verify>[1]) => {
    setBusy(true);
    setFeedback(null);
    try {
      const result = await api.alarms.verify(alarmId, body);
      if (result.success) {
        stopAlert();
        setRinging(null);
        if (result.checkin_at) setAwaitingCheckin(true);
        toast(result.message, "success");
        navigate("/");
      } else {
        setFeedback(result.message);
        if (result.challenge) setChallenge(result.challenge);
        setAnswer("");
      }
    } catch (err) {
      setFeedback(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const onPhoto = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    await submit({ image_base64: await readAsBase64(file) });
    e.target.value = "";
  };

  const snooze = async () => {
    if (await run(() => api.alarms.snooze(alarmId), "Snoozed")) {
      stopAlert();
      setRinging(null);
      navigate("/");
    }
  };

  const acknowledge = async () => {
    if (await run(() => api.escalations.acknowledge(), "Escalation acknowledged")) void escalation.reload();
  };

  if (state.error) return <ErrorNote message={state.error} />;
  if (!state.data) return null;

  if (!ringing) {
    return (
      <Card title={state.data.label ?? "Alarm"} className="mx-auto max-w-lg text-center">
        <p className="mb-4 text-ink-muted">
          This alarm is not ringing (status: {state.data.status.replace(/_/g, " ")}).
        </p>
        <Button onClick={() => { setRinging(null); navigate("/"); }}>Back to dashboard</Button>
      </Card>
    );
  }

  return (
    <div className="mx-auto max-w-lg space-y-5" onPointerDown={() => alertRef.current?.resume()}>
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-red-500 via-brand-500 to-brand-700 p-8 text-center text-white shadow-glow">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(255,255,255,0.25),transparent_45%)]" />
        <div className="relative">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-white/15 text-3xl animate-ring">
            !
          </div>
          <h1 className="mt-4 font-display text-3xl font-bold tracking-tight sm:text-4xl">
            {state.data.label ?? "Wake up!"}
          </h1>
          <p className="mt-2 text-sm text-white/85">Ringing since {formatWhen(state.data.triggered_at)}</p>
          <p className="mt-1 text-xs text-white/70">
            Sound: {state.data.sound ?? "classic"} · Vibration: {state.data.vibration ?? "medium"}
          </p>
          {state.data.status === "escalating" && (
            <p className="mt-3 inline-flex rounded-full bg-white/15 px-3 py-1 text-xs font-semibold uppercase tracking-wide">
              Escalation in progress
            </p>
          )}
        </div>
      </div>

      <Card title="Complete the task to dismiss">
        {challenge && (
          <div className="space-y-4">
            <p className="text-lg font-medium leading-snug">{challenge.question}</p>

            {challenge.input === "number" && (
              <form className="flex flex-col gap-2 sm:flex-row" onSubmit={(e) => { e.preventDefault(); void submit({ answer }); }}>
                <input className={inputClass} inputMode="numeric" autoFocus value={answer}
                  onChange={(e) => setAnswer(e.target.value)} placeholder="Your answer" />
                <Button type="submit" disabled={busy || !answer.trim()} className="sm:min-w-[6rem]">Check</Button>
              </form>
            )}

            {challenge.input === "choice" && (
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {challenge.choices?.map((choice, i) => (
                  <Button key={choice} variant="secondary" disabled={busy} className="justify-start text-left"
                    onClick={() => submit({ answer: String(i) })}>{choice}</Button>
                ))}
              </div>
            )}

            {challenge.input === "confirm" && (
              <Button disabled={busy} className="w-full" onClick={() => submit({ completed: true })}>
                I've done it
              </Button>
            )}

            {challenge.input === "photo" && (
              <div className="space-y-2">
                <label className="flex min-h-28 cursor-pointer flex-col items-center justify-center rounded-2xl border border-dashed border-line bg-surface-sunk px-4 text-center text-sm text-ink-soft">
                  <span className="font-medium text-ink">Take or upload a photo</span>
                  <span className="mt-1 text-xs text-ink-muted">Camera opens on phones</span>
                  <input type="file" accept="image/*" capture="environment" onChange={onPhoto} disabled={busy}
                    className="sr-only" />
                </label>
                <Button variant="ghost" disabled={busy} onClick={() => submit({ camera_error: true })}>
                  My camera isn't working
                </Button>
              </div>
            )}

            {feedback && <p className="text-sm font-medium text-red-600 dark:text-red-400">{feedback}</p>}
          </div>
        )}
        <div className="mt-6 flex items-center justify-between gap-2 border-t border-line pt-4">
          <Button variant="secondary" disabled={!state.data.snooze_available} onClick={snooze}
            title={state.data.snooze_available ? "" : "Snooze is off for this alarm"}>Snooze</Button>
          <Button variant="danger" disabled title="Complete the task to dismiss">Dismiss</Button>
        </div>
      </Card>

      {escalation.data && escalation.data.attempts.length > 0 && (
        <Card title="Escalation" actions={<Button variant="secondary" onClick={acknowledge}>I'm up</Button>}>
          <ul className="space-y-3 text-sm">
            {escalation.data.attempts.map((a) => (
              <li key={a.id} className="flex items-start justify-between gap-2 rounded-xl bg-surface-sunk/80 p-3">
                <div>
                  <span className="font-medium">#{a.attempt} {a.method ?? "none"}</span>
                  <span className="ml-2 text-xs text-ink-muted">
                    {a.fallback_used ? "rule-based fallback" : a.decided_by}
                  </span>
                  {a.reasoning && <p className="mt-1 text-xs text-ink-muted">{a.reasoning}</p>}
                </div>
                <Badge value={a.status} />
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
