import { create } from "zustand";
import type { LiveEvent } from "../api/types";
import {
  applyTheme,
  cycleTheme,
  getStoredTheme,
  type ThemeMode,
} from "../theme";

export interface Toast {
  id: number;
  kind: "info" | "success" | "error";
  text: string;
}

interface AppState {
  connected: boolean;
  ringingAlarmId: number | null;
  awaitingCheckin: boolean;
  events: LiveEvent[];
  revision: number;
  toasts: Toast[];
  theme: ThemeMode;
  setConnected: (value: boolean) => void;
  pushEvent: (event: LiveEvent) => void;
  setRinging: (id: number | null) => void;
  setAwaitingCheckin: (value: boolean) => void;
  toast: (text: string, kind?: Toast["kind"]) => void;
  dismissToast: (id: number) => void;
  setTheme: (mode: ThemeMode) => void;
  toggleTheme: () => void;
}

let toastSeq = 0;

export const useAppStore = create<AppState>((set, get) => ({
  connected: false,
  ringingAlarmId: null,
  awaitingCheckin: false,
  events: [],
  revision: 0,
  toasts: [],
  theme: getStoredTheme(),
  setConnected: (connected) => set({ connected }),
  pushEvent: (event) => {
    const patch: Partial<AppState> = {
      events: [event, ...get().events].slice(0, 50),
      revision: get().revision + 1,
    };
    if (event.subject_type === "alarm") {
      if (event.status === "ringing" || event.status === "escalating") patch.ringingAlarmId = event.subject_id;
      else if (get().ringingAlarmId === event.subject_id) patch.ringingAlarmId = null;
      if (event.status === "awaiting_checkin") patch.awaitingCheckin = true;
      if (event.status === "completed") patch.awaitingCheckin = false;
    }
    set(patch);
  },
  setRinging: (ringingAlarmId) => set({ ringingAlarmId }),
  setAwaitingCheckin: (awaitingCheckin) => set({ awaitingCheckin }),
  toast: (text, kind = "info") => {
    const id = ++toastSeq;
    set({ toasts: [...get().toasts, { id, kind, text }] });
    setTimeout(() => get().dismissToast(id), 5000);
  },
  dismissToast: (id) => set({ toasts: get().toasts.filter((t) => t.id !== id) }),
  setTheme: (theme) => {
    applyTheme(theme);
    set({ theme });
  },
  toggleTheme: () => {
    const theme = cycleTheme(get().theme);
    applyTheme(theme);
    set({ theme });
  },
}));
