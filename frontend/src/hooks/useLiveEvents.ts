import { useEffect } from "react";
import { wsUrl } from "../api/client";
import type { LiveEvent } from "../api/types";
import { useAppStore } from "../store/useAppStore";

const MAX_BACKOFF_MS = 15000;

/** Keeps one WebSocket open to /ws and feeds server events into the store. */
export function useLiveEvents(): void {
  useEffect(() => {
    let socket: WebSocket | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let backoff = 1000;
    let closed = false;
    const { setConnected, pushEvent, toast } = useAppStore.getState();

    const connect = () => {
      socket = new WebSocket(wsUrl());
      socket.onopen = () => {
        backoff = 1000;
        setConnected(true);
      };
      socket.onmessage = (msg) => {
        try {
          const data = JSON.parse(msg.data) as LiveEvent;
          if (data.type !== "event") return;
          pushEvent(data);
          if (data.title) toast(data.body ? `${data.title}: ${data.body}` : data.title);
        } catch {
          /* ignore non-JSON frames such as pings */
        }
      };
      socket.onclose = () => {
        setConnected(false);
        if (closed) return;
        retry = setTimeout(connect, backoff);
        backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
      };
      socket.onerror = () => socket?.close();
    };

    connect();
    return () => {
      closed = true;
      clearTimeout(retry);
      socket?.close();
    };
  }, []);
}
