export type ThemeMode = "light" | "dark" | "system";

const STORAGE_KEY = "riseup-theme";

export function getStoredTheme(): ThemeMode {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    if (value === "light" || value === "dark" || value === "system") return value;
  } catch {
    /* ignore */
  }
  return "system";
}

export function systemPrefersDark(): boolean {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function resolveDark(mode: ThemeMode): boolean {
  return mode === "dark" || (mode === "system" && systemPrefersDark());
}

export function applyTheme(mode: ThemeMode): void {
  document.documentElement.classList.toggle("dark", resolveDark(mode));
  try {
    localStorage.setItem(STORAGE_KEY, mode);
  } catch {
    /* ignore */
  }
}

/** Flip light ↔ dark from what the user currently sees (including when mode is Auto). */
export function cycleTheme(mode: ThemeMode): ThemeMode {
  return resolveDark(mode) ? "light" : "dark";
}

export function themeLabel(mode: ThemeMode): string {
  return resolveDark(mode) ? "Dark mode" : "Light mode";
}
