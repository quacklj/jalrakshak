"use client";

import Link from "next/link";
import { bandBg, bandColor, deviceDot, deviceLabel } from "@/lib/bandStyle";
import {
  BAND_LABEL,
  SENSORS,
  SENSOR_COUNT,
  SENSOR_ORDER,
  bandOfScore,
  riskScore,
} from "@/lib/config";
import { sensorViews } from "@/lib/display";
import { ageLabel, deviceStateFor } from "@/lib/derive";
import ConnectionBanner from "@/components/ConnectionBanner";
import IngestHelp from "@/components/IngestHelp";
import PumpControls from "@/components/PumpControls";
import RiskDial from "@/components/RiskDial";
import SensorCard from "@/components/SensorCard";
import SensorStatus from "@/components/SensorStatus";
import StatusPill from "@/components/StatusPill";
import { useLive } from "@/components/useLive";

const WINDOW = 30 * 60 * 1000; // 30 minutes of context on the overview

export default function OverviewPage() {
  const { readings, loading, now } = useLive(WINDOW, 400);

  const latest = readings.length ? readings[readings.length - 1] : null;
  const state = deviceStateFor(latest, now);

  const views = sensorViews(readings, SENSOR_ORDER, state);
  const score = riskScore(latest);
  const scoreBand = bandOfScore(score);
  const liveSensors = views.filter((v) => v.health.ok).length;
  const allLive = liveSensors === SENSOR_COUNT;

  if (loading) {
    return (
      <div className="page-inner">
        <div className="subtle">Loading readings…</div>
      </div>
    );
  }

  if (!latest) {
    return (
      <div className="page-inner">
        <div className="eyebrow" style={{ marginBottom: 8 }}>
          Jalraksha field node
        </div>
        <div className="display">
          Waiting for the first reading<span className="accent">.</span>
        </div>
        <div className="subtle" style={{ marginTop: 10, marginBottom: 24 }}>
          The dashboard is up. It just hasn&apos;t heard from the ESP32 yet.
        </div>
        <div className="card card-pad" style={{ maxWidth: 720 }}>
          <IngestHelp />
        </div>
      </div>
    );
  }

  // One KPI per sensor, plus the composite. A faulted probe shows its raw
  // voltage rather than a dash — a live-but-uncalibrated signal is still
  // information, and a dash looks like the same failure as a dead node.
  const kpis = views.map((v) => ({
    label: v.showingVolts ? `${SENSORS[v.key].name} (raw)` : SENSORS[v.key].name,
    value: v.value === null ? "—" : v.value.toFixed(v.spec.decimals),
    unit: v.value === null ? "" : v.spec.unit,
    color: v.health.ok ? bandColor[v.band] : "var(--critical)",
    sub: v.health.ok
      ? `${BAND_LABEL[v.band]} · normal ${v.spec.safe[0]}–${v.spec.safe[1]} ${v.spec.unit}`
      : v.health.label,
    faulted: !v.health.ok,
  }));

  return (
    <div className="page-inner">
      <div
        style={{
          display: "flex",
          alignItems: "flex-end",
          justifyContent: "space-between",
          gap: 16,
          marginBottom: 26,
          flexWrap: "wrap",
        }}
      >
        <div>
          <div className="eyebrow" style={{ marginBottom: 8 }}>
            Jalraksha field node
          </div>
          <div className="display">
            Network overview<span className="accent">.</span>
          </div>
          <div className="subtle" style={{ marginTop: 10 }}>
            {new Date(latest.t).toLocaleDateString([], {
              weekday: "long",
              day: "numeric",
              month: "long",
            })}{" "}
            · updated {ageLabel(latest.t, now)}
          </div>
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {/* A clean bill of health is only claimable when every probe answers. */}
          {allLive ? (
            <StatusPill band={scoreBand} label={`${BAND_LABEL[scoreBand]} · risk ${score}`} />
          ) : (
            <span
              className="pill"
              style={{ color: "var(--critical)", background: "var(--critical-bg)" }}
            >
              {liveSensors === 0
                ? "No sensor coverage"
                : `Partial coverage · ${liveSensors}/${SENSOR_COUNT} sensors`}
            </span>
          )}
          <span className="pill pill-neutral">
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: "50%",
                background: deviceDot[state],
              }}
            />
            {deviceLabel[state]}
          </span>
        </div>
      </div>

      <ConnectionBanner readings={readings} deviceState={state} now={now} />

      <div
        className="grid"
        style={{ gridTemplateColumns: "repeat(auto-fit,minmax(190px,1fr))", marginBottom: 14 }}
      >
        {kpis.map((k) => (
          <div
            key={k.label}
            className="card card-pad"
            style={k.faulted ? { borderColor: "var(--critical)" } : undefined}
          >
            <div className="eyebrow">{k.label}</div>
            <div className="figure" style={{ marginTop: 8, color: k.color }}>
              {k.value}
              <span style={{ fontSize: 15, color: "var(--muted-2)", marginLeft: 4 }}>{k.unit}</span>
            </div>
            <div
              style={{
                fontSize: 12,
                color: k.faulted ? "var(--critical)" : "var(--muted)",
                marginTop: 6,
                fontWeight: k.faulted ? 600 : 400,
              }}
            >
              {k.sub}
            </div>
          </div>
        ))}
      </div>

      <div
        className="grid"
        style={{ gridTemplateColumns: "repeat(auto-fit,minmax(190px,1fr))", marginBottom: 26 }}
      >
        <div className="card card-pad">
          <div className="eyebrow">Composite risk</div>
          <div className="figure" style={{ marginTop: 8, color: bandColor[scoreBand] }}>
            {score}
            <span style={{ fontSize: 15, color: "var(--muted-2)", marginLeft: 4 }}>/100</span>
          </div>
          <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 6 }}>
            {allLive
              ? `${BAND_LABEL[scoreBand]} band · all ${SENSOR_COUNT} sensors combined`
              : liveSensors > 0
                ? `${BAND_LABEL[scoreBand]} band · from ${liveSensors} of ${SENSOR_COUNT} sensors`
                : "no sensor reporting"}
          </div>
        </div>
        <div className="card card-pad">
          <div className="eyebrow">Sensors reading</div>
          <div
            className="figure"
            style={{ marginTop: 8, color: allLive ? "var(--safe)" : "var(--critical)" }}
          >
            {liveSensors}
            <span style={{ fontSize: 15, color: "var(--muted-2)", marginLeft: 4 }}>
              /{SENSOR_COUNT}
            </span>
          </div>
          <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 6 }}>
            {readings.length} samples · {ageLabel(latest.t, now)}
          </div>
        </div>
      </div>

      <div
        className="grid"
        style={{ gridTemplateColumns: "minmax(0,2fr) minmax(280px,1fr)", alignItems: "start" }}
      >
        <div className="grid" style={{ gridTemplateColumns: "1fr", alignContent: "start" }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: -4,
            }}
          >
            <div style={{ fontSize: 15, fontWeight: 600 }}>Last 30 minutes</div>
            <Link href="/live" style={{ fontSize: 12, fontWeight: 600 }}>
              Live monitoring →
            </Link>
          </div>
          {views.map((v) => (
            <SensorCard
              key={v.key}
              spec={v.spec}
              data={v.points}
              value={v.value}
              band={v.band}
              health={v.health}
              secondary={v.secondary}
            />
          ))}
        </div>

        <div className="grid" style={{ gridTemplateColumns: "1fr", alignContent: "start" }}>
          <PumpControls deviceState={state} now={now} />

          <div
            className="card card-pad"
            style={{ display: "flex", gap: 16, alignItems: "center" }}
          >
            <RiskDial score={score} size={86} />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 14.5, fontWeight: 600 }}>Composite risk</div>
              <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 3, lineHeight: 1.5 }}>
                {allLive
                  ? "Combined severity of every probe. Any one of them alone can drive it."
                  : liveSensors > 0
                    ? `Scored from ${liveSensors} of ${SENSOR_COUNT} probes — the rest are not reading, so this understates the real risk.`
                    : "No probe is reporting, so there is nothing to score."}
              </div>
              <div style={{ marginTop: 10 }}>
                {allLive ? (
                  <StatusPill band={scoreBand} />
                ) : (
                  <span
                    className="pill"
                    style={{ color: "var(--critical)", background: "var(--critical-bg)" }}
                  >
                    Incomplete · {liveSensors}/{SENSOR_COUNT} sensors
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="card card-pad">
            <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 12 }}>Latest payloads</div>
            <div style={{ display: "flex", flexDirection: "column", gap: 9 }}>
              {readings
                .slice(-6)
                .reverse()
                .map((r) => {
                  const b = bandOfScore(riskScore(r));
                  return (
                    <div
                      key={r.t}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 10,
                        paddingBottom: 9,
                        borderBottom: "1px solid var(--border-soft)",
                      }}
                    >
                      <span
                        style={{
                          width: 7,
                          height: 7,
                          borderRadius: 2,
                          background: bandColor[b],
                          flexShrink: 0,
                        }}
                      />
                      <div className="mono" style={{ fontSize: 11.5, flex: 1, minWidth: 0 }}>
                        {r.tempC === null ? "n/d" : `${r.tempC.toFixed(1)}°`} ·{" "}
                        {r.ph === null ? "n/d" : `pH ${r.ph.toFixed(2)}`} ·{" "}
                        {r.tds === null ? "n/d" : `${r.tds.toFixed(0)}ppm`} ·{" "}
                        {r.turbidityNtu === null ? "n/d" : `${r.turbidityNtu.toFixed(1)}NTU`}
                      </div>
                      <div
                        className="mono"
                        style={{ fontSize: 10.5, color: "var(--muted-2)", whiteSpace: "nowrap" }}
                      >
                        {new Date(r.t).toLocaleTimeString()}
                      </div>
                    </div>
                  );
                })}
            </div>
            <Link
              href="/history"
              style={{ fontSize: 12, fontWeight: 600, display: "inline-block", marginTop: 4 }}
            >
              Full history →
            </Link>
          </div>

          <SensorStatus readings={readings} deviceState={state} now={now} />

          <div
            className="card card-pad"
            style={{
              background: allLive ? bandBg[scoreBand] : "var(--critical-bg)",
              borderColor: "transparent",
            }}
          >
            <div
              style={{
                fontSize: 12.5,
                fontWeight: 600,
                color: allLive ? bandColor[scoreBand] : "var(--critical)",
              }}
            >
              {liveSensors === 0
                ? "No sensor data to judge"
                : !allLive
                  ? "Partial coverage — fix the sensors before trusting this"
                  : scoreBand === "safe"
                    ? "Water quality within limits"
                    : scoreBand === "watch"
                      ? "Drifting toward the limit"
                      : scoreBand === "warning"
                        ? "Outside safe range — investigate"
                        : "Critical — dispatch a field check"}
            </div>
            <div style={{ fontSize: 11.5, color: "var(--ink-2)", marginTop: 6, lineHeight: 1.55 }}>
              {views
                .map(
                  (v) =>
                    `${SENSORS[v.key].name.toLowerCase()} ${
                      v.health.ok ? BAND_LABEL[v.band].toLowerCase() : "not reading"
                    }`,
                )
                .join(", ")}
              .
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
