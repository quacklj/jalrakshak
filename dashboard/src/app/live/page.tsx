"use client";

import { useState } from "react";
import ConnectionBanner from "@/components/ConnectionBanner";
import PumpControls from "@/components/PumpControls";
import SensorCard from "@/components/SensorCard";
import SensorStatus from "@/components/SensorStatus";
import { useLive } from "@/components/useLive";
import { deviceDot, deviceLabel } from "@/lib/bandStyle";
import { SENSOR_ORDER } from "@/lib/config";
import { sensorViews, type UnitMode } from "@/lib/display";
import {
  ageLabel,
  connectivityTimeline,
  deviceStateFor,
  medianPeriodMs,
  uptimePct,
} from "@/lib/derive";

const WINDOWS = [
  { label: "5 min", ms: 5 * 60 * 1000 },
  { label: "30 min", ms: 30 * 60 * 1000 },
  { label: "2 h", ms: 2 * 60 * 60 * 1000 },
  { label: "24 h", ms: 24 * 60 * 60 * 1000 },
];

export default function LivePage() {
  const [win, setWin] = useState(WINDOWS[1]);
  // One switch for every analog probe, not just turbidity: pH and TDS have the
  // same uncalibrated-voltage problem, and the raw volts are what you read off
  // the bench while calibrating any of them.
  const [unit, setUnit] = useState<UnitMode>("auto");
  const { readings, loading, streaming, now } = useLive(win.ms, 700);

  const latest = readings.length ? readings[readings.length - 1] : null;
  const clock = now;
  const state = deviceStateFor(latest, clock);
  const timeline = connectivityTimeline(readings, clock);
  const period = medianPeriodMs(readings);
  const views = sensorViews(readings, SENSOR_ORDER, state, unit);

  return (
    <div className="page-inner">
      <div className="h2">Live Monitoring</div>
      <div className="subtle" style={{ marginTop: 8, marginBottom: 20 }}>
        Real-time ESP32 sensor streams ·{" "}
        {streaming ? "server-sent events" : "polling every 3s"}
      </div>

      <ConnectionBanner readings={readings} deviceState={state} now={clock} />

      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 12,
          flexWrap: "wrap",
          marginBottom: 20,
        }}
      >
        <div className="seg">
          {WINDOWS.map((w) => (
            <button key={w.label} data-active={w.ms === win.ms} onClick={() => setWin(w)}>
              {w.label}
            </button>
          ))}
        </div>
        <div className="seg">
          {(
            [
              ["auto", "Auto"],
              ["calibrated", "Calibrated"],
              ["volts", "Volts"],
            ] as [UnitMode, string][]
          ).map(([k, l]) => (
            <button key={k} data-active={unit === k} onClick={() => setUnit(k)}>
              {l}
            </button>
          ))}
        </div>
        <span className="pill pill-neutral mono" style={{ marginLeft: "auto" }}>
          {readings.length} samples
        </span>
        {period && <span className="pill pill-neutral mono">~{Math.round(period / 1000)}s cadence</span>}
      </div>

      {loading ? (
        <div className="subtle">Loading…</div>
      ) : (
        <div
          className="grid"
          style={{ gridTemplateColumns: "minmax(0,2fr) minmax(280px,1fr)", alignItems: "start" }}
        >
          <div className="grid" style={{ gridTemplateColumns: "1fr", alignContent: "start" }}>
            {views.map((v) => (
              <SensorCard
                key={v.key}
                spec={v.spec}
                data={v.points}
                value={v.value}
                band={v.band}
                health={v.health}
                secondary={v.secondary}
                height={168}
                axes
              />
            ))}
          </div>

          <div className="grid" style={{ gridTemplateColumns: "1fr", alignContent: "start" }}>
            <PumpControls deviceState={state} now={clock} />

            <div className="card" style={{ padding: 20 }}>
              <div className="eyebrow" style={{ marginBottom: 16 }}>
                Device connectivity
              </div>
              <div className="mono" style={{ fontSize: 14, fontWeight: 500 }}>
                {latest?.deviceId ?? "—"}
              </div>
              <div className="row" style={{ margin: "12px 0" }}>
                <span
                  style={{
                    width: 9,
                    height: 9,
                    borderRadius: "50%",
                    background: deviceDot[state],
                  }}
                />
                <span style={{ fontSize: 14, fontWeight: 600 }}>{deviceLabel[state]}</span>
                <span
                  className="mono"
                  style={{ fontSize: 11.5, color: "var(--muted)", marginLeft: "auto" }}
                >
                  seen {latest ? ageLabel(latest.t, clock) : "never"}
                </span>
              </div>

              <div
                style={{
                  display: "flex",
                  gap: 26,
                  margin: "18px 0",
                  padding: "16px 0",
                  borderTop: "1px solid var(--border-soft)",
                  borderBottom: "1px solid var(--border-soft)",
                }}
              >
                <div>
                  <div className="figure" style={{ fontSize: 28 }}>
                    {uptimePct(timeline)}%
                  </div>
                  <div style={{ fontSize: 11, color: "var(--muted)", marginTop: 4 }}>uptime</div>
                </div>
                <div>
                  <div className="figure" style={{ fontSize: 28 }}>
                    {latest?.rssi != null ? `${latest.rssi}` : "—"}
                  </div>
                  <div style={{ fontSize: 11, color: "var(--muted)", marginTop: 4 }}>RSSI dBm</div>
                </div>
              </div>

              <div
                style={{
                  fontSize: 10.5,
                  letterSpacing: ".1em",
                  textTransform: "uppercase",
                  color: "var(--muted-2)",
                  fontWeight: 600,
                  marginBottom: 9,
                }}
              >
                Last 24h
              </div>
              <div style={{ display: "flex", gap: 3, height: 36 }}>
                {timeline.map((b, i) => (
                  <div
                    key={i}
                    title={b.label}
                    style={{
                      flex: 1,
                      borderRadius: 2,
                      background:
                        b.state === "online"
                          ? "var(--safe)"
                          : b.state === "degraded"
                            ? "var(--watch)"
                            : "var(--surface)",
                      border: b.state === "offline" ? "1px solid var(--border)" : "none",
                    }}
                  />
                ))}
              </div>
              <div
                className="mono"
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  fontSize: 10,
                  color: "var(--muted-2)",
                  marginTop: 7,
                }}
              >
                <span>-24h</span>
                <span>now</span>
              </div>
            </div>

            <SensorStatus readings={readings} deviceState={state} now={clock} />
          </div>
        </div>
      )}
    </div>
  );
}
