"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { deviceLabel } from "@/lib/bandStyle";
import type { DeviceState, RelayId, RelayView } from "@/lib/types";
import { NavIcon } from "./icons";

type RelayResponse = { now: number; maxRunMs: number; pollMs: number; relays: RelayView[] };

/** mm:ss, for run timers that never sensibly exceed an hour. */
function clock(ms: number): string {
  const s = Math.max(0, Math.round(ms / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

function useRelays(pollMs = 1500) {
  const [data, setData] = useState<RelayResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Ignore an in-flight GET that was issued before the last command, or the
  // button visibly snaps back to its old position for a moment.
  const commandedAt = useRef(0);

  const load = useCallback(async () => {
    const issued = Date.now();
    try {
      const res = await fetch("/api/relays", { cache: "no-store" });
      const json = (await res.json()) as RelayResponse;
      if (issued >= commandedAt.current) setData(json);
      setError(null);
    } catch {
      setError("Cannot reach the dashboard server.");
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      if (!cancelled) await load();
    })();
    const id = setInterval(load, pollMs);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [load, pollMs]);

  const send = useCallback(
    async (body: Record<string, unknown>) => {
      commandedAt.current = Date.now();
      try {
        const res = await fetch("/api/relays", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify(body),
        });
        const json = (await res.json()) as RelayResponse & { ok?: boolean; error?: string };
        if (!res.ok) {
          setError(json.error ?? "The server refused that command.");
          return;
        }
        setData(json);
        setError(null);
      } catch {
        // The command may or may not have landed, so say exactly that rather
        // than showing a state we did not confirm.
        setError("The command did not reach the server. The pump may not have changed.");
        void load();
      }
    },
    [load],
  );

  return { data, error, send };
}

function PumpRow({
  relay,
  now,
  reachable,
  onToggle,
}: {
  relay: RelayView;
  now: number;
  reachable: boolean;
  onToggle: (id: RelayId, on: boolean) => void;
}) {
  const running = relay.desired;
  // Commanded and confirmed are different facts. Until the node's next payload
  // agrees with the command, the button says "starting", not "running".
  const confirmed = relay.actual === relay.desired;
  const runFor = running ? now - relay.since : 0;
  const remaining = relay.autoOffAt ? relay.autoOffAt - now : 0;

  const tone = !running
    ? { c: "var(--muted)", bg: "var(--surface-2)" }
    : confirmed
      ? { c: "var(--safe)", bg: "var(--safe-bg)" }
      : { c: "var(--watch)", bg: "var(--watch-bg)" };

  const stateLabel = !running
    ? relay.actual === true
      ? "Stopping"
      : "Off"
    : confirmed
      ? "Running"
      : "Starting";

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 14,
        padding: "14px 0",
        borderBottom: "1px solid var(--border-soft)",
      }}
    >
      <span
        style={{
          width: 36,
          height: 36,
          borderRadius: 10,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: tone.bg,
          color: tone.c,
          flexShrink: 0,
        }}
      >
        <NavIcon id="pump" size={19} />
      </span>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
          <span style={{ fontSize: 13.5, fontWeight: 600 }}>{relay.name}</span>
          <span className="pill" style={{ color: tone.c, background: tone.bg, fontSize: 11 }}>
            {running && (
              <span
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: "50%",
                  background: tone.c,
                  animation: confirmed ? "pulse 1.6s ease-in-out infinite" : undefined,
                }}
              />
            )}
            {stateLabel}
          </span>
        </div>
        <div className="mono" style={{ fontSize: 11, color: "var(--muted-2)", marginTop: 4 }}>
          {relay.blurb}
          {running && ` · on for ${clock(runFor)} · auto-off in ${clock(remaining)}`}
          {!running && relay.actualAt !== null && ` · device confirmed off`}
        </div>
      </div>

      <button
        className={running ? "btn btn-sm" : "btn btn-primary btn-sm"}
        disabled={!reachable}
        onClick={() => onToggle(relay.id, !running)}
        style={{
          minWidth: 92,
          justifyContent: "center",
          ...(running ? { color: "var(--critical)", borderColor: "var(--critical)" } : {}),
        }}
      >
        {running ? "Stop" : "Start"}
      </button>
    </div>
  );
}

/**
 * Two pump relays, commanded from here and confirmed by the node.
 *
 * The controls are disabled whenever the node is not online: sending a pump
 * command you cannot confirm is worse than not sending one, because the button
 * would report a position nothing is actually holding.
 */
export default function PumpControls({
  deviceState,
  now,
}: {
  deviceState: DeviceState;
  now: number;
}) {
  const { data, error, send } = useRelays();
  const reachable = deviceState === "online" && !error;
  const anyRunning = data?.relays.some((r) => r.desired) ?? false;

  return (
    <div className="card card-pad">
      <style>{`@keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}`}</style>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 10,
          marginBottom: 2,
        }}
      >
        <div style={{ fontSize: 14, fontWeight: 600 }}>Pump control</div>
        <button
          className="btn btn-sm"
          disabled={!anyRunning}
          onClick={() => void send({ allOff: true })}
          style={anyRunning ? { color: "var(--critical)", borderColor: "var(--critical)" } : undefined}
        >
          Stop all
        </button>
      </div>

      {!reachable && (
        <div
          style={{
            fontSize: 11.5,
            color: "var(--ink-2)",
            background: error ? "var(--critical-bg)" : "var(--surface-2)",
            borderRadius: 10,
            padding: "9px 11px",
            margin: "10px 0 2px",
            lineHeight: 1.55,
          }}
        >
          {error ??
            `The node is ${deviceLabel[deviceState].toLowerCase()}, so a command cannot be confirmed. Controls stay disabled until it is reporting again — any pump still running will stop on its own within ${clock(30_000)}.`}
        </div>
      )}

      {data?.relays.map((relay) => (
        <PumpRow
          key={relay.id}
          relay={relay}
          now={now}
          reachable={reachable}
          onToggle={(id, on) => void send({ id, on })}
        />
      ))}

      {!data && <div className="subtle" style={{ padding: "14px 0" }}>Loading pump state…</div>}

      <div style={{ fontSize: 11, color: "var(--muted-2)", marginTop: 10, lineHeight: 1.55 }}>
        No float switch is wired, so nothing physical stops a running pump. Both the server and the
        firmware hold an independent {clock(data?.maxRunMs ?? 300_000)} run limit, and the node
        switches off by itself if it loses the dashboard.
      </div>
    </div>
  );
}
