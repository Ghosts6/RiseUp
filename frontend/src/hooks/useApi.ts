import { DependencyList, useCallback, useEffect, useState } from "react";
import { errorMessage } from "../api/client";
import { useAppStore } from "../store/useAppStore";

interface ApiState<T> {
  data: T | undefined;
  error: string | null;
  loading: boolean;
  reload: () => Promise<void>;
  setData: (value: T) => void;
}

/** Loads data on mount, when deps change, and whenever a live server event arrives. */
export function useApi<T>(loader: () => Promise<T>, deps: DependencyList = [], live = true): ApiState<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const revision = useAppStore((s) => (live ? s.revision : 0));

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(loader, deps);

  const reload = useCallback(async () => {
    try {
      setData(await load());
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [load]);

  useEffect(() => {
    void reload();
  }, [reload, revision]);

  return { data, error, loading, reload, setData };
}

/** Wraps a mutating call with toast feedback; returns the result or undefined on failure. */
export function useAction() {
  const toast = useAppStore((s) => s.toast);
  return useCallback(
    async <T>(fn: () => Promise<T>, success?: string): Promise<T | undefined> => {
      try {
        const result = await fn();
        if (success) toast(success, "success");
        return result;
      } catch (err) {
        toast(errorMessage(err), "error");
        return undefined;
      }
    },
    [toast],
  );
}
