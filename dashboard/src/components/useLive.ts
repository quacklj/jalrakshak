"use client";

import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { Reading } from "@/lib/types";

type Loaded = { window: number; rows: Reading[] };

/** Ticks once a second. 0 during SSR, so the markup matches on hydration. */
function useClock(): number {
  return useSyncExternalStore(
    (onChange) => {
      const id = setInterval(onChange, 1000);
      return () => clearInterval(id);
    },
    // Stable within each second, so React only re-renders once per tick.
    () => Math.floor(Date.now() / 1000) * 1000,
    () => 0,
  );
}

/**
 * Loads a window of history, then keeps it current: server-sent events when
 * the browser and host allow it, incremental polling when they don't.
 */
export function useLive(
  windowMs: number,
  points = 800,
): {
  readings: Reading[];
  loading: boolean;
  /** SSE attached — the page is being pushed to rather than polling. */
  streaming: boolean;
  now: number;
  refresh: () => void;
} {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [streaming, setStreaming] = useState(false);
  const now = useClock();
  const lastT = useRef(0);

  // Derived, so no effect has to flip a loading flag when the window changes.
  const loading = loaded === null || loaded.window !== windowMs;
  const readings = loaded && loaded.window === windowMs ? loaded.rows : [];

  const trim = useCallback(
    (rows: Reading[]) => {
      const cutoff = Date.now() - windowMs;
      const kept = rows.filter((r) => r.t >= cutoff);
      return kept.length > points * 2 ? kept.slice(-points * 2) : kept;
    },
    [windowMs, points],
  );

  const refresh = useCallback(async () => {
    try {
      const res = await fetch(`/api/readings?window=${windowMs}&points=${points}`, {
        cache: "no-store",
      });
      const json = (await res.json()) as { readings: Reading[] };
      lastT.current = json.readings.length ? json.readings[json.readings.length - 1].t : 0;
      setLoaded({ window: windowMs, rows: json.readings });
    } catch {
      // Keep whatever we already have; the stream or the next poll will catch up.
      setLoaded((prev) => prev ?? { window: windowMs, rows: [] });
    }
  }, [windowMs, points]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      if (!cancelled) await refresh();
    })();
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  useEffect(() => {
    let poll: ReturnType<typeof setInterval> | null = null;
    let source: EventSource | null = null;

    const merge = (incoming: Reading[]) => {
      if (!incoming.length) return;
      lastT.current = Math.max(lastT.current, incoming[incoming.length - 1].t);
      setLoaded((prev) => (prev ? { ...prev, rows: trim([...prev.rows, ...incoming]) } : prev));
    };

    const startPolling = () => {
      if (poll) return;
      poll = setInterval(async () => {
        try {
          const res = await fetch(`/api/readings?since=${lastT.current}`, { cache: "no-store" });
          const json = (await res.json()) as { readings: Reading[] };
          merge(json.readings);
        } catch {
          /* transient network blip */
        }
      }, 3000);
    };

    try {
      source = new EventSource("/api/stream");
      source.addEventListener("hello", () => setStreaming(true));
      source.addEventListener("reading", (e) => merge([JSON.parse((e as MessageEvent).data)]));
      source.onerror = () => {
        setStreaming(false);
        startPolling();
      };
    } catch {
      startPolling();
    }

    return () => {
      source?.close();
      if (poll) clearInterval(poll);
    };
  }, [trim]);

  return { readings, loading, streaming, now, refresh };
}
