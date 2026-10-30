import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useParams } from "react-router-dom";
import { z } from "zod";
import { api, ApiError, errorMessage } from "../api/client";
import type { AlarmInput } from "../api/types";
import { Button, Card, ErrorNote, Field, PageHeader, inputClass } from "../components/ui";
import { useAppStore } from "../store/useAppStore";

const DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const;
const SOUNDS = ["classic", "chimes", "birds", "radar", "beacon"];

const schema = z.object({
  time: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Please enter time in HH:MM format"),
  label: z.string().max(60).optional(),
  repeat: z.enum(["once", "daily", "weekdays", "weekends", "custom"]),
  custom_days: z.array(z.enum(DAYS)),
  sound: z.string(),
  vibration: z.enum(["low", "medium", "high"]),
  snooze_enabled: z.boolean(),
  verification_task_type: z.enum(["math", "trivia", "custom", "object_detection"]),
  custom_task_text: z.string().optional(),
}).superRefine((v, ctx) => {
  if (v.repeat === "custom" && v.custom_days.length === 0) {
    ctx.addIssue({ code: "custom", path: ["custom_days"], message: "Pick at least one day" });
  }
  if (v.verification_task_type === "custom" && !v.custom_task_text?.trim()) {
    ctx.addIssue({ code: "custom", path: ["custom_task_text"], message: "Describe the task to complete" });
  }
});

type FormValues = z.infer<typeof schema>;

const DEFAULTS: FormValues = {
  time: "07:00", label: "", repeat: "once", custom_days: [], sound: "classic", vibration: "medium",
  snooze_enabled: false, verification_task_type: "math", custom_task_text: "",
};

function toPayload(v: FormValues): AlarmInput {
  return {
    time: v.time,
    label: v.label?.trim() || undefined,
    repeat_pattern: v.repeat === "custom" ? `custom:${v.custom_days.join(",")}` : v.repeat,
    sound: v.sound,
    vibration: v.vibration,
    snooze_enabled: v.snooze_enabled,
    verification_task_type: v.verification_task_type,
    custom_task_text: v.verification_task_type === "custom" ? v.custom_task_text : undefined,
  };
}

export default function AlarmForm() {
  const { id } = useParams();
  const alarmId = id ? Number(id) : null;
  const navigate = useNavigate();
  const toast = useAppStore((s) => s.toast);
  const [serverError, setServerError] = useState<string | null>(null);
  const { register, handleSubmit, watch, reset, setError, formState: { errors, isSubmitting } } =
    useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: DEFAULTS });

  useEffect(() => {
    if (alarmId) {
      api.alarms.get(alarmId).then((a) => {
        const custom = a.repeat_pattern.startsWith("custom:");
        reset({
          time: a.time, label: a.label ?? "", sound: a.sound, vibration: a.vibration as FormValues["vibration"],
          repeat: (custom ? "custom" : a.repeat_pattern) as FormValues["repeat"],
          custom_days: custom ? (a.repeat_pattern.slice(7).split(",") as FormValues["custom_days"]) : [],
          snooze_enabled: a.snooze_enabled, verification_task_type: a.verification_task_type,
          custom_task_text: a.custom_task_text ?? "",
        });
      }).catch((err) => setServerError(errorMessage(err)));
    } else {
      api.prefs.get().then((p) => reset({ ...DEFAULTS, verification_task_type: p.preferences.default_task_type }))
        .catch(() => undefined);
    }
  }, [alarmId, reset]);

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    try {
      const alarm = alarmId
        ? await api.alarms.update(alarmId, toPayload(values))
        : await api.alarms.create(toPayload(values));
      toast(`Alarm set for ${alarm.time}${alarm.next_trigger_local ? ` (next: ${alarm.next_trigger_local})` : ""}`, "success");
      navigate("/");
    } catch (err) {
      const field = err instanceof ApiError ? (err.detail?.field as keyof FormValues | undefined) : undefined;
      if (field && field in DEFAULTS) setError(field, { message: errorMessage(err) });
      else setServerError(errorMessage(err));
    }
  };

  const repeat = watch("repeat");
  const taskType = watch("verification_task_type");

  return (
    <div className="mx-auto max-w-xl">
      <PageHeader
        title={alarmId ? "Edit alarm" : "New alarm"}
        subtitle="Time, sound, and the challenge you must finish before the alarm stops."
      />
      <Card>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <ErrorNote message={serverError} />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Time" error={errors.time?.message}>
              <input type="time" className={inputClass} {...register("time")} />
            </Field>
            <Field label="Label" error={errors.label?.message}>
              <input className={inputClass} placeholder="Work" {...register("label")} />
            </Field>
          </div>

          <Field label="Repeat" error={errors.custom_days?.message}>
            <select className={inputClass} {...register("repeat")}>
              <option value="once">Once</option>
              <option value="daily">Daily</option>
              <option value="weekdays">Weekdays</option>
              <option value="weekends">Weekends</option>
              <option value="custom">Custom days</option>
            </select>
            {repeat === "custom" && (
              <div className="mt-2 flex flex-wrap gap-2">
                {DAYS.map((d) => (
                  <label key={d} className="flex min-h-10 items-center gap-1.5 rounded-xl border border-line px-2.5 py-1.5 text-xs uppercase">
                    <input type="checkbox" value={d} {...register("custom_days")} /> {d}
                  </label>
                ))}
              </div>
            )}
          </Field>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Sound">
              <select className={inputClass} {...register("sound")}>
                {SOUNDS.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </Field>
            <Field label="Vibration" error={errors.vibration?.message}>
              <select className={inputClass} {...register("vibration")}>
                <option value="low">Low</option>
                <option value="medium">Medium</option>
                <option value="high">High</option>
              </select>
            </Field>
          </div>

          <Field label="Wake-up task" hint="You must complete it to dismiss the alarm.">
            <select className={inputClass} {...register("verification_task_type")}>
              <option value="math">Math puzzle</option>
              <option value="trivia">Trivia question</option>
              <option value="object_detection">Photo of an object (falls back to math without a camera)</option>
              <option value="custom">Custom task</option>
            </select>
          </Field>
          {taskType === "custom" && (
            <Field label="Custom task" error={errors.custom_task_text?.message}>
              <input className={inputClass} placeholder="Do 10 push-ups" {...register("custom_task_text")} />
            </Field>
          )}

          <label className="flex min-h-11 items-center gap-2 text-sm">
            <input type="checkbox" className="h-4 w-4 rounded border-line text-brand-500" {...register("snooze_enabled")} />
            Allow snooze
          </label>

          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="secondary" onClick={() => navigate(-1)}>Cancel</Button>
            <Button type="submit" disabled={isSubmitting}>{alarmId ? "Save" : "Create alarm"}</Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
