"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import ServoDial from "@/components/ServoDial";
import { useLive } from "@/components/useLive";
import { deviceLabel } from "@/lib/bandStyle";
import { deviceStateFor } from "@/lib/derive";
import type { ServoCal } from "@/lib/servoCal";
import type { ServoView } from "@/lib/types";

/**
 * The servo calibration bench.
 *
 * Separate from Live Monitoring on purpose. Everything here drives the motor
 * for a measured time with no angle maths in the way — that is what measuring
 * a servo requires, and it is exactly what you do not want one mis-tap away
 * from the operating controls.
 *
 * The method is the same one the standalone bench sketch used, because it
 * works: put a mark at zero, press Test, see where it lands, adjust the
 * milliseconds. What changed is where the numbers live — on the server, so
 * they survive reflashing the node.
 */

type CalResponse = { cal: ServoCal; rev: number; defaults: ServoCal };
type ServoResponse = { servo: ServoView; positions: { deg: number; name: string }[] };

function Field({
  label,
  value,
  onChange,
  suffix = "ms",
  width = 96,
  ...rest
}: {
  label?: string;
  value: number;
  onChange: (v: number) => void;
  suffix?: string;
  width?: number;
  min?: number;
  max?: number;
}) {
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 7 }}>
      {label && <strong style={{ fontSize: 13, minWidth: 46 }}>{label}</strong>}
      <input
        type="number"
        className="mono"
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        style={{
          width,
          padding: "7px 9px",
          borderRadius: 8,
          border: "1px solid var(--border)",
          background: "var(--surface-2)",
          color: "var(--ink)",
          fontSize: 13,
          textAlign: "right",
        }}
        {...rest}
      />
      <span style={{ fontSize: 12, color: "var(--muted-2)" }}>{suffix}</span>
    </span>
  );
}

function Card({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="card card-pad" style={{ marginBottom: 14 }}>
      <div style={{ fontSize: 14, fontWeight: 600 }}>{title}</div>
      {hint && (
        <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 6, lineHeight: 1.6 }}>{hint}</div>
      )}
      <div style={{ marginTop: 12 }}>{children}</div>
    </div>
  );
}

export default function CalibratePage() {
  const { readings, now } = useLive(60_000, 120);
  const latest = readings.length ? readings[readings.length - 1] : null;
  const deviceState = deviceStateFor(latest, now);

  const [cal, setCal] = useState<ServoCal | null>(null);
  const [defaults, setDefaults] = useState<ServoCal | null>(null);
  const [servo, setServo] = useState<ServoView | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [revs, setRevs] = useState(5);
  const [pauseMs, setPauseMs] = useState(2000);
  const [measured, setMeasured] = useState<number | null>(null);
  const commandedAt = useRef(0);

  const reachable = deviceState === "online";

  const loadCal = useCallback(async () => {
    try {
      const res = await fetch("/api/servo/cal", { cache: "no-store" });
      const json = (await res.json()) as CalResponse;
      setCal(json.cal);
      setDefaults(json.defaults);
    } catch {
      setErr("Cannot reach the dashboard server.");
    }
  }, []);

  const loadServo = useCallback(async () => {
    const issued = Date.now();
    try {
      const res = await fetch("/api/servo", { cache: "no-store" });
      const json = (await res.json()) as ServoResponse;
      if (issued >= commandedAt.current) setServo(json.servo);
    } catch {
      /* next tick */
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      if (!cancelled) await loadCal();
    })();
    return () => {
      cancelled = true;
    };
  }, [loadCal]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      if (!cancelled) await loadServo();
    })();
    // Twice a second: fast enough to watch a 700 ms test move actually happen.
    const id = setInterval(loadServo, 500);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [loadServo]);

  const flash = (text: string) => {
    setMsg(text);
    setErr(null);
    setTimeout(() => setMsg(null), 2500);
  };

  /** Saves first, then commands — so a Test always runs the numbers on screen. */
  const saveCal = useCallback(
    async (patch?: Partial<ServoCal>) => {
      const body = { ...(cal ?? {}), ...(patch ?? {}) };
      const res = await fetch("/api/servo/cal", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      const json = (await res.json()) as { ok: boolean; cal: ServoCal; error?: string };
      if (!json.ok) {
        setErr(json.error ?? "Could not save.");
        return false;
      }
      setCal(json.cal);
      setErr(null);
      return true;
    },
    [cal],
  );

  const command = useCallback(async (body: Record<string, unknown>) => {
    commandedAt.current = Date.now();
    try {
      const res = await fetch("/api/servo", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      const json = (await res.json()) as { ok?: boolean; error?: string; servo?: ServoView };
      if (!res.ok) {
        setErr(json.error ?? "The server refused that command.");
        return;
      }
      if (json.servo) setServo(json.servo);
      setErr(null);
    } catch {
      setErr("The command did not reach the server. The servo may not have moved.");
    }
  }, []);

  /** Every Test writes the on-screen numbers first, or you measure the old ones. */
  const saveThen = async (body: Record<string, unknown>) => {
    if (await saveCal()) await command(body);
  };

  const testAngle = (deg: number) => void saveThen({ command: "testangle", deg });

  const stopAndMeasure = async () => {
    await command({ command: "stop" });
    // The node times its own spin; the browser only reads the answer back.
    setTimeout(async () => {
      const res = await fetch("/api/servo", { cache: "no-store" });
      const json = (await res.json()) as ServoResponse;
      const ms = json.servo.lastSpinMs;
      if (ms && revs > 0) setMeasured(Math.round(ms / revs));
      setServo(json.servo);
    }, 1200);
  };

  const useMeasuredForward = () => {
    if (!cal || !measured) return;
    // Rescales all three anchors by the same factor, keeping the shape of the
    // curve that was measured and only correcting its overall speed.
    const k = measured / cal.t360;
    void saveCal({
      t120: Math.round(cal.t120 * k),
      t240: Math.round(cal.t240 * k),
      t360: measured,
    }).then((ok) => ok && flash("Forward timings rescaled"));
  };

  if (!cal || !defaults) {
    return (
      <div className="page-inner">
        <div className="subtle">Loading calibration…</div>
      </div>
    );
  }

  const set = (patch: Partial<ServoCal>) => setCal({ ...cal, ...patch });
  const btn = { justifyContent: "center" } as const;

  return (
    <div className="page-inner">
      <div className="h2">Servo Calibration</div>
      <div className="subtle" style={{ marginTop: 8, marginBottom: 20 }}>
        Bench tools for measuring the MG996R. Everything here drives the motor directly — use Live
        Monitoring for normal operation.
      </div>

      {!reachable && (
        <div
          className="card card-pad"
          style={{ borderColor: "var(--critical)", marginBottom: 14, fontSize: 12.5, lineHeight: 1.6 }}
        >
          The node is <strong>{deviceLabel[deviceState].toLowerCase()}</strong>. Commands are
          disabled until it reports again — nothing here can be confirmed while it is silent.
        </div>
      )}

      {(msg || err) && (
        <div
          className="card card-pad"
          style={{
            marginBottom: 14,
            borderColor: err ? "var(--critical)" : "var(--safe)",
            color: err ? "var(--critical)" : "var(--safe)",
            fontSize: 12.5,
            fontWeight: 600,
          }}
        >
          {err ?? msg}
        </div>
      )}

      <div className="grid" style={{ gridTemplateColumns: "minmax(0,320px) minmax(0,1fr)", alignItems: "start" }}>
        <div>
          <div className="card card-pad" style={{ marginBottom: 14, textAlign: "center" }}>
            <div style={{ position: "relative", display: "inline-block", lineHeight: 0 }}>
              <ServoDial
                deg={servo?.actualDeg ?? null}
                targetDeg={servo?.targetDeg ?? null}
                planDir={servo?.planDir ?? null}
                planSweep={servo?.planSweep ?? null}
                progress={null}
                tone={servo?.moving ? "var(--accent)" : "var(--safe)"}
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
                <div className="figure" style={{ fontSize: 28 }}>
                  {servo?.actualDeg == null ? "—" : `${servo.actualDeg.toFixed(0)}°`}
                </div>
                <div style={{ fontSize: 10, color: "var(--muted-2)" }}>
                  {servo?.moving ? "moving" : "believed"}
                </div>
              </div>
            </div>

            <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
              <button
                className="btn btn-sm"
                style={{ ...btn, flex: 1, color: "var(--critical)", borderColor: "var(--critical)" }}
                onClick={() => void command({ command: "stop" })}
              >
                Stop
              </button>
              <button
                className="btn btn-sm"
                style={{ ...btn, flex: 1.3 }}
                disabled={!reachable}
                onClick={() => void command({ command: "zero" }).then(() => flash("Zero set here"))}
              >
                Set here = 0°
              </button>
            </div>
          </div>

          <Card
            title="Motor calibration"
            hint="Changing the speed offset changes every timing below it — they were all measured at this speed. Re-run the angle tests after touching it."
          >
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 12.5, fontWeight: 600, marginBottom: 6 }}>
                Stop pulse{" "}
                <span style={{ fontWeight: 400, color: "var(--muted-2)" }}>
                  — the horn must be completely still
                </span>
              </div>
              <input
                type="range"
                min={1400}
                max={1600}
                value={cal.stopPulse}
                onChange={(e) => set({ stopPulse: Number(e.target.value) })}
                onMouseUp={() => void saveCal().then((ok) => ok && flash("Stop pulse saved"))}
                onTouchEnd={() => void saveCal().then((ok) => ok && flash("Stop pulse saved"))}
                style={{ width: "100%", accentColor: "var(--accent)" }}
              />
              <div className="mono" style={{ fontSize: 12, textAlign: "center" }}>
                {cal.stopPulse} µs
              </div>
            </div>

            <Field
              label="Speed"
              value={cal.speedOffset}
              onChange={(v) => set({ speedOffset: v })}
              suffix="µs offset"
              min={50}
              max={500}
            />
            <div className="mono" style={{ fontSize: 11, color: "var(--muted-2)", marginTop: 8 }}>
              drive = {cal.stopPulse + cal.speedOffset} µs fwd / {cal.stopPulse - cal.speedOffset} µs rev
            </div>
          </Card>
        </div>

        <div>
          <Card
            title="Angle timing — forward ( + )"
            hint="Put the mark at 0 and press Set here = 0°. Then press Test. Overshot → lower the ms. Short → raise it. Every test starts from the 0 mark."
          >
            {([120, 240, 360] as const).map((deg) => {
              const key = (deg === 120 ? "t120" : deg === 240 ? "t240" : "t360") as keyof ServoCal;
              return (
                <div
                  key={deg}
                  style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 10 }}
                >
                  <Field
                    label={`${deg}°`}
                    value={cal[key]}
                    onChange={(v) => set({ [key]: v } as Partial<ServoCal>)}
                  />
                  <button
                    className="btn btn-sm"
                    disabled={!reachable}
                    onClick={() => testAngle(deg)}
                  >
                    Test
                  </button>
                  <span className="mono" style={{ fontSize: 11, color: "var(--muted-2)" }}>
                    {(cal[key] / deg).toFixed(2)} ms/°
                  </span>
                </div>
              );
            })}
          </Card>

          <Card
            title="Step test"
            hint="0 → 120 → 240 → 360 with a pause at each, starting from the 0 mark. A full lap should finish back on the mark; whatever it misses by is your accumulated error."
          >
            <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
              <Field label="Pause" value={pauseMs} onChange={setPauseMs} min={0} max={30000} />
              <button
                className="btn btn-primary btn-sm"
                disabled={!reachable}
                onClick={() => void saveThen({ command: "steptest", pauseMs })}
              >
                Run step test
              </button>
            </div>
          </Card>

          <Card
            title="Reverse ( − ) direction"
            hint="Reverse runs slightly faster than forward, so it gets its own figure. The intermediate reverse angles are scaled from this one."
          >
            <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
              <Field label="1 rev" value={cal.tRev} onChange={(v) => set({ tRev: v })} />
              <button
                className="btn btn-sm"
                disabled={!reachable}
                onClick={() => void saveThen({ command: "run", ms: -cal.tRev })}
              >
                Test 1 rev −
              </button>
              <span className="mono" style={{ fontSize: 11, color: "var(--muted-2)" }}>
                {((cal.tRev / cal.t360) * 100).toFixed(1)}% of forward
              </span>
            </div>
          </Card>

          <Card
            title="Measure a revolution"
            hint="Free-spin the servo, count whole turns by eye, then stop. The node times itself, so network lag cannot corrupt the measurement."
          >
            <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
              <Field label="Turns" value={revs} onChange={setRevs} suffix="" width={70} min={1} />
              <button
                className="btn btn-sm"
                disabled={!reachable}
                onClick={() => void command({ command: "spin", dir: 1 })}
              >
                Spin +
              </button>
              <button
                className="btn btn-sm"
                disabled={!reachable}
                onClick={() => void command({ command: "spin", dir: -1 })}
              >
                Spin −
              </button>
              <button
                className="btn btn-sm"
                style={{ color: "var(--critical)", borderColor: "var(--critical)" }}
                onClick={() => void stopAndMeasure()}
              >
                Stop &amp; measure
              </button>
            </div>
            <div style={{ marginTop: 12, display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
              <span className="mono" style={{ fontSize: 13 }}>
                Measured:{" "}
                <strong style={{ color: measured ? "var(--safe)" : "var(--muted-2)" }}>
                  {measured ?? "—"}
                </strong>{" "}
                ms per turn
              </span>
              <button className="btn btn-sm" disabled={!measured} onClick={useMeasuredForward}>
                Use for + (rescales all three)
              </button>
              <button
                className="btn btn-sm"
                disabled={!measured}
                onClick={() =>
                  void saveCal({ tRev: measured! }).then((ok) => ok && flash("Reverse updated"))
                }
              >
                Use for −
              </button>
            </div>
          </Card>

          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button
              className="btn btn-primary btn-sm"
              onClick={() => void saveCal().then((ok) => ok && flash("Calibration saved"))}
            >
              Save timing
            </button>
            <button
              className="btn btn-sm"
              onClick={() => {
                if (!confirm(`Reset to ${defaults.t120} / ${defaults.t240} / ${defaults.t360} ms?`)) return;
                void fetch("/api/servo/cal", {
                  method: "POST",
                  headers: { "content-type": "application/json" },
                  body: JSON.stringify({ reset: true }),
                })
                  .then((r) => r.json())
                  .then((j: CalResponse) => {
                    setCal(j.cal);
                    flash("Reset to defaults");
                  });
              }}
            >
              Reset to defaults
            </button>
          </div>

          <div style={{ fontSize: 11.5, color: "var(--muted-2)", marginTop: 14, lineHeight: 1.65 }}>
            These numbers live on the server, not in the ESP32&apos;s flash — so reflashing the node
            does not lose an afternoon of bench work, and a replacement board picks up the same
            calibration as soon as it boots. The node fetches them and reports back what it is
            actually running on.
          </div>
        </div>
      </div>
    </div>
  );
}
