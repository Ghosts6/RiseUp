import { ButtonHTMLAttributes, ReactNode } from "react";

export function Card({ title, actions, children, className = "" }: {
  title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string;
}) {
  return (
    <section className={`panel animate-fade-up ${className}`}>
      {(title || actions) && (
        <header className="mb-4 flex flex-wrap items-center justify-between gap-2">
          {title && <h2 className="font-display text-lg font-semibold tracking-tight text-ink">{title}</h2>}
          {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}

const VARIANTS = {
  primary:
    "bg-brand-500 text-white shadow-sm hover:bg-brand-600 active:scale-[0.98] disabled:bg-ink-muted/30 disabled:text-ink-muted disabled:shadow-none",
  secondary:
    "border border-line bg-surface-raised text-ink-soft hover:bg-surface-sunk disabled:text-ink-muted",
  danger:
    "bg-red-600 text-white hover:bg-red-700 disabled:bg-ink-muted/30",
  ghost:
    "text-ink-soft hover:bg-surface-sunk disabled:text-ink-muted",
};

export function Button({ variant = "primary", className = "", ...props }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: keyof typeof VARIANTS }) {
  return (
    <button
      className={`inline-flex min-h-10 items-center justify-center rounded-xl px-3.5 py-2 text-sm font-medium transition disabled:cursor-not-allowed ${VARIANTS[variant]} ${className}`}
      {...props}
    />
  );
}

const BADGE_COLORS: Record<string, string> = {
  ringing: "bg-red-500/15 text-red-700 dark:text-red-300",
  escalating: "bg-red-500/15 text-red-700 dark:text-red-300",
  critical: "bg-red-500/15 text-red-700 dark:text-red-300",
  missed: "bg-red-500/15 text-red-700 dark:text-red-300",
  failed: "bg-red-500/15 text-red-700 dark:text-red-300",
  high: "bg-orange-500/15 text-orange-700 dark:text-orange-300",
  snoozed: "bg-amber-500/15 text-amber-800 dark:text-amber-300",
  notifying: "bg-amber-500/15 text-amber-800 dark:text-amber-300",
  awaiting_checkin: "bg-amber-500/15 text-amber-800 dark:text-amber-300",
  pending: "bg-amber-500/15 text-amber-800 dark:text-amber-300",
  medium: "bg-sky-500/15 text-sky-800 dark:text-sky-300",
  scheduled: "bg-sky-500/15 text-sky-800 dark:text-sky-300",
  sent: "bg-sky-500/15 text-sky-800 dark:text-sky-300",
  mock: "bg-sky-500/15 text-sky-800 dark:text-sky-300",
  fallback_mock: "bg-amber-500/15 text-amber-800 dark:text-amber-300",
  not_configured: "bg-surface-sunk text-ink-muted",
  completed: "bg-emerald-500/15 text-emerald-800 dark:text-emerald-300",
  acknowledged: "bg-emerald-500/15 text-emerald-800 dark:text-emerald-300",
  accepted: "bg-emerald-500/15 text-emerald-800 dark:text-emerald-300",
  granted: "bg-emerald-500/15 text-emerald-800 dark:text-emerald-300",
  ok: "bg-emerald-500/15 text-emerald-800 dark:text-emerald-300",
  live: "bg-emerald-500/15 text-emerald-800 dark:text-emerald-300",
  running: "bg-emerald-500/15 text-emerald-800 dark:text-emerald-300",
  local: "bg-sky-500/15 text-sky-800 dark:text-sky-300",
  denied: "bg-red-500/15 text-red-700 dark:text-red-300",
  declined: "bg-red-500/15 text-red-700 dark:text-red-300",
  low: "bg-surface-sunk text-ink-muted",
  idle: "bg-surface-sunk text-ink-muted",
  disabled: "bg-surface-sunk text-ink-muted",
  stopped: "bg-amber-500/15 text-amber-800 dark:text-amber-300",
};

export function Badge({ value, children }: { value: string; children?: ReactNode }) {
  const color = BADGE_COLORS[value] ?? "bg-surface-sunk text-ink-muted";
  return (
    <span className={`inline-block rounded-lg px-2 py-0.5 text-xs font-medium capitalize ${color}`}>
      {children ?? value.replace(/_/g, " ")}
    </span>
  );
}

export function Modal({ title, open, onClose, children }: {
  title: string; open: boolean; onClose: () => void; children: ReactNode;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/50 p-0 backdrop-blur-sm sm:items-center sm:p-4" onClick={onClose}>
      <div
        className="max-h-[90dvh] w-full max-w-2xl overflow-y-auto rounded-t-3xl border border-line bg-surface-raised p-5 shadow-soft sm:rounded-2xl sm:p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between gap-3">
          <h3 className="font-display text-lg font-semibold">{title}</h3>
          <Button variant="ghost" onClick={onClose} aria-label="Close">Close</Button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Field({ label, error, hint, children }: {
  label: string; error?: string; hint?: string; children: ReactNode;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1.5 block font-medium text-ink-soft">{label}</span>
      {children}
      {hint && !error && <span className="mt-1.5 block text-xs text-ink-muted">{hint}</span>}
      {error && <span className="mt-1.5 block text-xs text-red-600 dark:text-red-400">{error}</span>}
    </label>
  );
}

export const inputClass =
  "w-full min-h-11 rounded-xl border border-line bg-surface-raised px-3 py-2 text-sm text-ink placeholder:text-ink-muted focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/25";

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-8 text-center text-sm text-ink-muted">{children}</p>;
}

export function ErrorNote({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p className="rounded-xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-700 dark:text-red-300">
      {message}
    </p>
  );
}

export function PageHeader({ title, subtitle, actions }: {
  title: string; subtitle?: string; actions?: ReactNode;
}) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3 sm:mb-6">
      <div>
        <h1 className="page-title">{title}</h1>
        {subtitle && <p className="page-sub">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function formatWhen(iso: string | null | undefined): string {
  if (!iso) return "-";
  return new Date(iso).toLocaleString(undefined, {
    month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
  });
}

export function percent(rate: number | null | undefined): string {
  return rate == null ? "-" : `${Math.round(rate * 100)}%`;
}
