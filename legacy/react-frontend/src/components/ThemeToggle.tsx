import { resolveDark, themeLabel, type ThemeMode } from "../theme";

function SunIcon({ className = "" }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="4" stroke="currentColor" strokeWidth="1.75" />
      <path
        d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.2 5.2l1.6 1.6M17.2 17.2l1.6 1.6M18.8 5.2l-1.6 1.6M6.8 17.2l-1.6 1.6"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
      />
    </svg>
  );
}

function MoonIcon({ className = "" }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M18.5 14.2A7.2 7.2 0 0 1 9.8 5.5 7.5 7.5 0 1 0 18.5 14.2Z"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export default function ThemeToggle({
  theme,
  onToggle,
  onResetSystem,
}: {
  theme: ThemeMode;
  onToggle: () => void;
  onResetSystem: () => void;
}) {
  const dark = resolveDark(theme);
  const label = themeLabel(theme);
  const Icon = dark ? MoonIcon : SunIcon;

  return (
    <div className="group relative">
      <button
        type="button"
        onClick={onToggle}
        onContextMenu={(e) => {
          e.preventDefault();
          onResetSystem();
        }}
        aria-label={label}
        className={[
          "relative inline-flex h-9 w-9 items-center justify-center rounded-xl border border-line",
          "bg-surface-sunk text-ink-soft outline-none transition-all duration-200",
          "hover:border-brand-500/40 hover:bg-brand-500/10 hover:text-brand-600",
          "hover:shadow-sm hover:shadow-brand-500/10",
          "active:scale-90 active:bg-brand-500/20",
          "focus-visible:ring-2 focus-visible:ring-brand-500/50 focus-visible:ring-offset-2",
          "focus-visible:ring-offset-surface-raised",
          "dark:hover:text-brand-400",
        ].join(" ")}
      >
        <Icon
          className={[
            "h-[1.15rem] w-[1.15rem] transition-transform duration-300 ease-out",
            "group-hover:scale-110",
            dark ? "group-hover:-rotate-12" : "group-hover:rotate-12",
            "group-active:scale-90 group-active:rotate-0",
          ].join(" ")}
        />
      </button>

      <span
        role="tooltip"
        className={[
          "pointer-events-none absolute left-1/2 top-[calc(100%+0.45rem)] z-50",
          "-translate-x-1/2 whitespace-nowrap rounded-md px-2 py-1",
          "bg-ink text-[11px] font-medium text-surface shadow-soft",
          "opacity-0 translate-y-0.5 transition-all duration-150",
          "group-hover:opacity-100 group-hover:translate-y-0",
          "group-focus-within:opacity-100 group-focus-within:translate-y-0",
        ].join(" ")}
      >
        {/* Arrow pointing up toward the icon */}
        <span
          aria-hidden
          className="absolute left-1/2 top-0 h-2 w-2 -translate-x-1/2 -translate-y-1/2 rotate-45 bg-ink"
        />
        <span className="relative z-10">{label}</span>
      </span>
    </div>
  );
}
