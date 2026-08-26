"use client";

import Link from "next/link";
import { bandBg, bandColor, deviceDot, deviceLabel } from "@/lib/bandStyle";
import {
  BAND_LABEL,
  SENSORS,
  TURBIDITY_VOLTS,
  bandOfScore,
  bandOfValue,
  riskScore,
} from "@/lib/config";
import { ageLabel, deviceStateFor, sensorHealth } from "@/lib/derive";
import ConnectionBanner from "@/components/ConnectionBanner";
import IngestHelp from "@/components/IngestHelp";
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

  const tempBand = latest?.tempC != null ? bandOfValue(latest.tempC, SENSORS.temperature) : "safe";
  const turbBand =
    latest?.turbidityNtu != null ? bandOfValue(latest.turbidityNtu, SENSORS.turbidity) : "safe";
  const score = latest ? riskScore(latest.tempC, latest.turbidityNtu) : 0;
  const scoreBand = bandOfScore(score);

  const tempHealth = sensorHealth(readings, "temperature", state);
  const turbHealth = sensorHealth(readings, "turbidity", state);
  const liveSensors = [tempHealth, turbHealth].filter((h) => h.ok).length;

  const tempPoints = readings.map((r) => ({ t: r.t, v: r.tempC }));

  // Chart whichever turbidity signal is actually meaningful right now — a
  // clamped NTU value would draw a flat line and look like a dead feed.
  const showVolts = latest?.turbidityNtu == null;
  const turbSpec = showVolts ? TURBIDITY_VOLTS : SENSORS.turbidity;
  const turbPoints = readings.map((r) => ({
    t: r.t,
    v: showVolts ? r.turbidityV : r.turbidityNtu,
  }));

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

  const kpis = [
    {
      label: SENSORS.temperature.name,
      value: tempHealth.ok ? latest.tempC!.toFixed(2) : "—",
      unit: tempHealth.ok ? "°C" : "",
      color: tempHealth.ok ? bandColor[tempBand] : "var(--muted-2)",
      sub: tempHealth.ok
        ? `${BAND_LABEL[tempBand]} · normal ${SENSORS.temperature.safe[0]}–${SENSORS.temperature.safe[1]} °C`
        : tempHealth.label,
      faulted: !tempHealth.ok,
    },
    {
      // When NTU is off scale the voltage is the only real number we have, so
      // show that rather than a dash.
      label: turbHealth.ok ? SENSORS.turbidity.name : "Turbidity probe",
      value: turbHealth.ok
        ? latest.turbidityNtu!.toFixed(1)
        : latest.turbidityV != null
          ? latest.turbidityV.toFixed(3)
          : "—",
      unit: turbHealth.ok ? "NTU" : latest.turbidityV != null ? "V" : "",
      color: turbHealth.ok ? bandColor[turbBand] : "var(--critical)",
      sub: turbHealth.ok
        ? `${BAND_LABEL[turbBand]} · probe at ${latest.turbidityV!.toFixed(3)} V`
        : turbHealth.label,
      faulted: !turbHealth.ok,
    },
    {
      label: "Composite risk",
      value: String(score),
      unit: "/100",
      color: bandColor[scoreBand],
      sub:
        liveSensors === 2
          ? `${BAND_LABEL[scoreBand]} band · both sensors combined`
          : liveSensors === 1
            ? `${BAND_LABEL[scoreBand]} band · from 1 sensor only`
            : "no sensor reporting",
      faulted: liveSensors < 2,
    },
    {
      label: "Sensors reading",
      value: `${liveSensors}/2`,
      unit: "",
      color: liveSensors === 2 ? "var(--safe)" : "var(--critical)",
      sub: `${readings.length} samples · ${ageLabel(latest.t, now)}`,
      faulted: liveSensors < 2,
    },
  ];

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
          {/* A clean bill of health is only claimable when both probes are answering. */}
          {liveSensors === 2 ? (
            <StatusPill band={scoreBand} label={`${BAND_LABEL[scoreBand]} · risk ${score}`} />
          ) : (
            <span
              className="pill"
              style={{ color: "var(--critical)", background: "var(--critical-bg)" }}
            >
              {liveSensors === 0
                ? "No sensor coverage"
                : `Partial coverage · ${liveSensors}/2 sensors`}
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
        style={{ gridTemplateColumns: "repeat(auto-fit,minmax(210px,1fr))", marginBottom: 26 }}
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
          <SensorCard
            spec={SENSORS.temperature}
            data={tempPoints}
            value={latest.tempC}
            band={tempBand}
            health={tempHealth}
          />
          <SensorCard
            spec={turbSpec}
            data={turbPoints}
            value={showVolts ? latest.turbidityV : latest.turbidityNtu}
            band={
              showVolts && latest.turbidityV != null
                ? bandOfValue(latest.turbidityV, TURBIDITY_VOLTS)
                : turbBand
            }
            health={turbHealth}
            secondary={showVolts ? "NTU off scale" : `${latest.turbidityV!.toFixed(3)} V`}
          />
        </div>

        <div className="grid" style={{ gridTemplateColumns: "1fr", alignContent: "start" }}>
          <div
            className="card card-pad"
            style={{ display: "flex", gap: 16, alignItems: "center" }}
          >
            <RiskDial score={score} size={86} />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 14.5, fontWeight: 600 }}>Composite risk</div>
              <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 3, lineHeight: 1.5 }}>
                {liveSensors === 2
                  ? "Combined severity of temperature and turbidity. Either sensor alone can drive it."
                  : liveSensors === 1
                    ? "Scored from one sensor only — the other is not reading, so this understates the real risk."
                    : "Neither sensor is reporting, so there is nothing to score."}
              </div>
              <div style={{ marginTop: 10 }}>
                {liveSensors === 2 ? (
                  <StatusPill band={scoreBand} />
                ) : (
                  <span
                    className="pill"
                    style={{ color: "var(--critical)", background: "var(--critical-bg)" }}
                  >
                    Incomplete · {liveSensors}/2 sensors
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
                  const b = bandOfScore(riskScore(r.tempC, r.turbidityNtu));
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
                        {r.tempC === null ? "n/d" : `${r.tempC.toFixed(2)} °C`} ·{" "}
                        {r.turbidityNtu === null ? "n/d" : `${r.turbidityNtu.toFixed(1)} NTU`}
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
              background: liveSensors === 2 ? bandBg[scoreBand] : "var(--critical-bg)",
              borderColor: "transparent",
            }}
          >
            <div
              style={{
                fontSize: 12.5,
                fontWeight: 600,
                color: liveSensors === 2 ? bandColor[scoreBand] : "var(--critical)",
              }}
            >
              {liveSensors === 0
                ? "No sensor data to judge"
                : liveSensors === 1
                  ? "Partial coverage — fix the sensor before trusting this"
                  : scoreBand === "safe"
                    ? "Water quality within limits"
                    : scoreBand === "watch"
                      ? "Drifting toward the limit"
                      : scoreBand === "warning"
                        ? "Outside safe range — investigate"
                        : "Critical — dispatch a field check"}
            </div>
            <div style={{ fontSize: 11.5, color: "var(--ink-2)", marginTop: 6, lineHeight: 1.55 }}>
              Temperature {tempHealth.ok ? BAND_LABEL[tempBand].toLowerCase() : "not reading"},
              turbidity {turbHealth.ok ? BAND_LABEL[turbBand].toLowerCase() : "not reading"}.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
