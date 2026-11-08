import { useEffect } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { applyTheme, resolveDark } from "../theme";
import { useAppStore } from "../store/useAppStore";
import ThemeToggle from "./ThemeToggle";
import { Button } from "./ui";

const NAV = [
  { to: "/", label: "Home", short: "Home", end: true },
  { to: "/tasks", label: "Tasks", short: "Tasks" },
  { to: "/insights", label: "Insights", short: "Stats" },
  { to: "/recommendations", label: "Tips", short: "Tips" },
  { to: "/history", label: "History", short: "Log" },
  { to: "/settings", label: "Settings", short: "More" },
];

const MOBILE_PRIMARY = NAV.filter((n) =>
  ["/", "/tasks", "/insights", "/history", "/settings"].includes(n.to),
);

export default function Layout() {
  const {
    connected, ringingAlarmId, awaitingCheckin, toasts, dismissToast,
    setRinging, setAwaitingCheckin, toast, theme, toggleTheme, setTheme,
  } = useAppStore();
  const navigate = useNavigate();
  const location = useLocation();
  const isRingingPage = location.pathname.endsWith("/ringing");

  useEffect(() => {
    applyTheme(theme);
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", resolveDark(theme) ? "#0B1220" : "#FF6B35");
  }, [theme]);

  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => {
      if (useAppStore.getState().theme === "system") applyTheme("system");
    };
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  useEffect(() => {
    api.alarms.list().then((alarms) => {
      const active = alarms.find((a) => a.status === "ringing" || a.status === "escalating");
      setRinging(active ? active.id : null);
      setAwaitingCheckin(alarms.some((a) => a.status === "awaiting_checkin"));
    }).catch(() => undefined);
  }, [setRinging, setAwaitingCheckin]);

  useEffect(() => {
    if (ringingAlarmId && !isRingingPage) {
      navigate(`/alarms/${ringingAlarmId}/ringing`);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ringingAlarmId]);

  useEffect(() => {
    if (!ringingAlarmId) return;
    const blockClose = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", blockClose);
    return () => window.removeEventListener("beforeunload", blockClose);
  }, [ringingAlarmId]);

  const confirmCheckin = async () => {
    try {
      const result = await api.checkins.confirm();
      setAwaitingCheckin(false);
      toast(result.message, "success");
    } catch (err) {
      toast(String((err as Error).message), "error");
    }
  };

  return (
    <div className="safe-top flex min-h-dvh flex-col">
      <header className="sticky top-0 z-30 border-b border-line/80 bg-surface-raised/80 backdrop-blur-xl">
        <div className="mx-auto flex max-w-app items-center gap-3 px-4 py-2.5 sm:py-3">
          <NavLink to="/" className="group flex min-w-0 shrink-0 items-center gap-2.5" aria-label="RiseUp home">
            <img
              src="/static/img/RiseUp.jpg"
              alt="RiseUp"
              className="h-9 w-auto object-contain sm:h-10 dark:rounded-md dark:bg-white dark:p-0.5"
            />
            <span className="hidden text-xs text-ink-muted lg:inline">wake smarter</span>
          </NavLink>

          <nav className="ml-2 hidden items-center gap-1 md:flex" aria-label="Main">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `rounded-xl px-3 py-2 text-sm font-medium transition ${
                    isActive
                      ? "bg-brand-500/15 text-brand-700 dark:text-brand-400"
                      : "text-ink-soft hover:bg-surface-sunk"
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            <span
              className="hidden items-center gap-1.5 rounded-full border border-line bg-surface-sunk px-2.5 py-1 text-[11px] font-medium text-ink-muted sm:inline-flex"
              title="Live updates (WebSocket)"
            >
              <span className={`h-1.5 w-1.5 rounded-full ${connected ? "bg-emerald-500" : "bg-ink-muted"}`} />
              {connected ? "Live" : "Offline"}
            </span>
            <ThemeToggle
              theme={theme}
              onToggle={toggleTheme}
              onResetSystem={() => setTheme("system")}
            />
          </div>
        </div>
      </header>

      {ringingAlarmId && !isRingingPage && (
        <div className="bg-red-600 text-white">
          <div className="mx-auto flex max-w-app items-center justify-between gap-3 px-4 py-2.5 text-sm">
            <span className="font-medium">An alarm is ringing</span>
            <Button variant="secondary" onClick={() => navigate(`/alarms/${ringingAlarmId}/ringing`)}>
              Open
            </Button>
          </div>
        </div>
      )}

      {awaitingCheckin && (
        <div className="border-b border-amber-500/30 bg-amber-500/15 text-amber-950 dark:text-amber-100">
          <div className="mx-auto flex max-w-app flex-wrap items-center justify-between gap-3 px-4 py-2.5 text-sm">
            <span>Still awake? Confirm so we don't escalate.</span>
            <Button onClick={confirmCheckin}>I'm awake</Button>
          </div>
        </div>
      )}

      <main className={`mx-auto w-full max-w-app flex-1 px-4 py-5 sm:py-7 ${isRingingPage ? "pb-8" : "pb-6"}`}>
        <Outlet />
      </main>

      {!isRingingPage && (
        <footer className="mt-auto border-t border-line/70 bg-surface-raised/40 pb-[4.75rem] md:pb-0">
          <div className="mx-auto flex max-w-app flex-col items-center justify-between gap-3 px-4 py-6 text-center sm:flex-row sm:text-left">
            <div className="flex items-center gap-2.5">
              <img
                src="/static/img/RiseUp.jpg"
                alt=""
                aria-hidden
                className="h-7 w-auto object-contain opacity-90 dark:rounded dark:bg-white dark:p-0.5"
              />
              <p className="text-sm text-ink-muted">
                Wake smarter. Stay accountable.
              </p>
            </div>
            <p className="text-xs text-ink-muted/80">
              © {new Date().getFullYear()} RiseUp
            </p>
          </div>
        </footer>
      )}

      {!isRingingPage && (
        <nav
          className="safe-bottom fixed inset-x-0 bottom-0 z-30 border-t border-line bg-surface-raised/95 backdrop-blur-xl md:hidden"
          aria-label="Mobile"
        >
          <div className="mx-auto grid max-w-app grid-cols-5 gap-0.5 px-1.5 py-2">
            {MOBILE_PRIMARY.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `flex min-h-12 flex-col items-center justify-center rounded-xl px-1 text-[10px] font-semibold transition sm:text-[11px] ${
                    isActive ? "bg-brand-500/15 text-brand-700 dark:text-brand-400" : "text-ink-muted"
                  }`
                }
              >
                {item.short}
              </NavLink>
            ))}
          </div>
        </nav>
      )}

      <div className="safe-bottom pointer-events-none fixed inset-x-0 bottom-20 z-40 flex flex-col items-end gap-2 px-4 md:bottom-4">
        {toasts.map((t) => (
          <div
            key={t.id}
            onClick={() => dismissToast(t.id)}
            className={`pointer-events-auto w-full max-w-sm cursor-pointer rounded-2xl px-4 py-3 text-sm shadow-soft animate-fade-up ${
              t.kind === "error"
                ? "bg-red-600 text-white"
                : t.kind === "success"
                  ? "bg-emerald-600 text-white"
                  : "bg-ink text-surface"
            }`}
          >
            {t.text}
          </div>
        ))}
      </div>
    </div>
  );
}
