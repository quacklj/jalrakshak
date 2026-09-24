"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { deviceLabel } from "@/lib/bandStyle";
import type { DeviceState, ServoView } from "@/lib/types";
import ServoDial from "./ServoDial";

type ServoResponse = {
  now: number;
  pollMs: number;
  positions: { deg: number; name: string }[];
  driftWatch: number;
  driftWarning: number;
  servo: ServoView;
};

function useServo(pollMs = 1200) {
  const [data, setData] = useState<ServoResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Same guard as the pump controls: drop a GET that was already in flight
  // when the command went out, or the dial visibly snaps back for a frame.
  const commandedAt = useRef(0);

  const load = useCallback(async () => {
    const issued = Date.now();
    try {
      const res = await fetch("/api/servo", { cache: "no-store" });
      const json = (await res.json()) as ServoResponse;
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
        const res = await fetch("/api/servo", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify(body),
        });
        const json = (await res.json()) as ServoResponse & { ok?: boolean; error?: string };
        if (!res.ok) {
          setError(json.error ?? "The server refused that command.");
          return null;
        }
        setData(json);
        setError(null);
        return json.servo;
      } catch {
        setError("The command did not reach the server. The servo may not have moved.");
        void load();
        return null;
      }
    },
    [load],
  );

  return { data, error, send };
}

/**
 * Servo positioner.
 *
 * The node polls for commands the same way it polls for relay positions, so
 * nothing has to call inward to a device behind NAT. What comes back the other
 * way is the node's own belief about the angle — dead reckoned from a
 * stopwatch, never measured — and this component's main job is to keep that
 * distinction visible rather than quietly rendering a guess as a readout.
 */
export default function ServoControl({
  deviceState,
  now,
}: {
  deviceState: DeviceState;
  now: number;
}) {
  const { data, error, send } = useServo();
  const [wanted, setWanted] = useState(0);
  // When a move was commanded and how long it should take, so the dial can
  // show progress. The node only reports every upload interval, which is far
  // slower than a 2.3s move — this is a prediction, and it is labelled as one.
  const [move, setMove] = useState<{ startedAt: number; ms: number } | null>(null);

  const servo = data?.servo ?? null;
  const reachable = deviceState === "online" && !error;

  const progress =
    move && move.ms > 0
      ? Math.min(1, Math.max(0, (now - move.startedAt) / move.ms))
      : null;
  const predicting = progress !== null && progress < 1;

  const drift = servo?.movesSinceZero ?? null;
  const driftBad =
    servo?.uncertain === true ||
    (drift !== null && data !== null && drift >= data.driftWarning);
  const driftWatch =
    !driftBad && drift !== null && data !== null && drift >= data.driftWatch;

  const tone = !reachable
    ? "var(--muted)"
    : predicting || servo?.moving
      ? "var(--accent)"
      : driftBad
        ? "var(--critical)"
        : driftWatch
          ? "var(--watch)"
          : "var(--safe)";

  const go = async (body: Record<string, unknown>) => {
    const next = await send(body);
    // issuedAt is the server's own timestamp for the command, which is both
    // more truthful than reading a clock here and keeps render pure.
    if (next?.planMs) setMove({ startedAt: next.issuedAt, ms: next.planMs });
  };

  const angleText =
    servo?.actualDeg === null || servo === null ? "—" : `${servo.actualDeg.toFixed(1)}°`;

  return (
    <div className="card card-pad">
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 10,
        }}
      >
        <div style={{ fontSize: 14, fontWeight: 600 }}>Servo positioner</div>
        <span className="pill pill-neutral mono" style={{ fontSize: 10.5 }}>
          MG996R · GPIO 16
        </span>
      </div>

      {!reachable && (
        <div
          style={{
            fontSize: 11.5,
            color: "var(--ink-2)",
            background: error ? "var(--critical-bg)" : "var(--surface-2)",
            borderRadius: 10,
            padding: "9px 11px",
            margin: "10px 0 0",
            lineHeight: 1.55,
          }}
        >
          {error ??
            `The node is ${deviceLabel[deviceState].toLowerCase()}, so a move cannot be confirmed. Controls stay disabled until it reports again.`}
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "center", marginTop: 6 }}>
        <div style={{ position: "relative", lineHeight: 0 }}>
          <ServoDial
            deg={servo?.actualDeg ?? null}
            targetDeg={servo?.targetDeg ?? null}
            planDir={servo?.planDir ?? null}
            planSweep={servo?.planSweep ?? null}
            progress={predicting ? progress : null}
            tone={tone}
          />
          <div
            style={{
              position: "absolute",
              inset: 0,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              pointerEvents: "none",
            }}
          >
            <div className="figure" style={{ fontSize: 30, color: tone }}>
              {angleText}
            </div>
            {/* Never "position". The word has to carry that nothing measured it. */}
            <div style={{ fontSize: 10, color: "var(--muted-2)", marginTop: 2 }}>
              believed
            </div>
          </div>
        </div>
      </div>

      <div
        style={{
          textAlign: "center",
          fontSize: 11,
          lineHeight: 1.55,
          marginTop: 4,
          color: driftBad ? "var(--critical)" : driftWatch ? "var(--watch)" : "var(--muted-2)",
          fontWeight: driftBad || driftWatch ? 600 : 400,
        }}
      >
        {servo?.uncertain
          ? "Stopped mid-move — the angle is an interpolation. Re-zero it."
          : drift === null
            ? "No servo reported yet."
            : `${drift} move${drift === 1 ? "" : "s"} since zero · no feedback, so drift accumulates`}
      </div>

      {predicting && servo && (
        <div
          style={{
            marginTop: 10,
            padding: "9px 11px",
            borderRadius: 10,
            background: "var(--accent-tint)",
            border: "1px solid var(--accent-tint-border)",
          }}
        >
          <div style={{ fontSize: 11.5, color: "var(--ink-2)", marginBottom: 6 }}>
            Rotating {servo.planSweep?.toFixed(0)}° {servo.planDir === 1 ? "forward" : "reverse"} ·{" "}
            {(((servo.planMs ?? 0) * (1 - (progress ?? 0))) / 1000).toFixed(1)}s left{" "}
            <span style={{ color: "var(--muted-2)" }}>(predicted)</span>
          </div>
          <div style={{ height: 4, borderRadius: 2, background: "var(--border)" }}>
            <div
              style={{
                height: "100%",
                borderRadius: 2,
                width: `${(progress ?? 0) * 100}%`,
                background: "var(--accent)",
              }}
            />
          </div>
        </div>
      )}

      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        {(data?.positions ?? []).map((p) => (
          <button
            key={p.deg}
            className="btn btn-sm"
            disabled={!reachable}
            onClick={() => void go({ command: "goto", deg: p.deg })}
            style={{ flex: 1, justifyContent: "center" }}
          >
            {p.name}
          </button>
        ))}
      </div>

      <div style={{ display: "flex", gap: 8, alignItems: "center", marginTop: 10 }}>
        <input
          type="range"
          min={0}
          max={359}
          step={1}
          value={wanted}
          disabled={!reachable}
          onChange={(e) => setWanted(Number(e.target.value))}
          style={{ flex: 1, accentColor: "var(--accent)" }}
        />
        <span className="mono" style={{ fontSize: 12, minWidth: 42, textAlign: "right" }}>
          {wanted}°
        </span>
        <button
          className="btn btn-primary btn-sm"
          disabled={!reachable}
          onClick={() => void go({ command: "goto", deg: wanted })}
        >
          Go
        </button>
      </div>

      <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
        {/* Never disabled by device state. A servo that is physically running
            is exactly the case where the dashboard says the node is unreachable,
            and that is the moment you most want this button to work. */}
        <button
          className="btn btn-sm"
          onClick={() => {
            setMove(null);
            void send({ command: "stop" });
          }}
          style={{ flex: 1, justifyContent: "center", color: "var(--critical)", borderColor: "var(--critical)" }}
        >
          Stop
        </button>
        <button
          className="btn btn-sm"
          disabled={!reachable}
          onClick={() => {
            setMove(null);
            void send({ command: "zero" });
          }}
          style={{ flex: 1.4, justifyContent: "center" }}
        >
          Set here = 0°
        </button>
      </div>

      <div
        style={{
          display: "flex",
          gap: 8,
          marginTop: 10,
          paddingTop: 10,
          borderTop: "1px solid var(--border-soft)",
        }}
      >
        <span style={{ fontSize: 11, color: "var(--muted-2)", alignSelf: "center", flex: 1 }}>
          Full turn (flush)
        </span>
        <button className="btn btn-sm" disabled={!reachable} onClick={() => void go({ command: "turn", revs: 1 })}>
          +360°
        </button>
        <button className="btn btn-sm" disabled={!reachable} onClick={() => void go({ command: "turn", revs: -1 })}>
          −360°
        </button>
      </div>

      <div style={{ fontSize: 11, color: "var(--muted-2)", marginTop: 10, lineHeight: 1.55 }}>
        This servo has no encoder. Every angle here is timed, not measured, and the timings only
        hold at the supply they were calibrated on. Re-zero against a physical mark whenever the
        count above climbs — it is the only thing that resets the error.
      </div>
    </div>
  );
}
