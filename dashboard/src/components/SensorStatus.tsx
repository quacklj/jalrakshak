"use client";

import { bandBg, bandColor } from "@/lib/bandStyle";
import { BAND_LABEL, SENSORS, SENSOR_ORDER, bandOfValue } from "@/lib/config";
import { ageLabel, sensorHealth, sensorValue, sensorVolts, type SensorHealth } from "@/lib/derive";
import type { DeviceState, Reading, SensorKey } from "@/lib/types";
import { BandIcon, NavIcon } from "./icons";

const SENSOR_ICON: Record<SensorKey, string> = {
  temperature: "thermometer",
  ph: "ph",
  tds: "tds",
  turbidity: "droplet",
};

function FaultIcon({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.7}>
      <circle cx={8} cy={8} r={6.2} />
      <path d="M5.6 5.6l4.8 4.8" strokeLinecap="round" />
    </svg>
  );
}

/** One row: what the sensor is, whether it is reading, and what to do if not. */
function Row({
  sensorKey,
  health,
  latest,
  now,
}: {
  sensorKey: SensorKey;
  health: SensorHealth;
  latest: Reading | null;
  now: number;
}) {
  const spec = SENSORS[sensorKey];
  const value = sensorValue(latest, sensorKey);
  const band = value === null ? null : bandOfValue(value, spec);
  // Falls back to the raw voltage so a live-but-off-scale probe still shows a
  // moving number instead of a dash.
  const volts = sensorVolts(latest, sensorKey);

  const tone = health.ok
    ? { c: bandColor[band ?? "safe"], bg: bandBg[band ?? "safe"] }
    : health.code === "no-device"
      ? { c: "var(--muted)", bg: "var(--surface-2)" }
      : { c: "var(--critical)", bg: "var(--critical-bg)" };

  return (
    <div
      style={{
        display: "flex",
        alignItems: "flex-start",
        gap: 12,
        padding: "13px 0",
        borderBottom: "1px solid var(--border-soft)",
      }}
    >
      <span
        style={{
          width: 32,
          height: 32,
          borderRadius: 9,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: tone.bg,
          color: tone.c,
          flexShrink: 0,
        }}
      >
        <NavIcon id={SENSOR_ICON[sensorKey]} size={17} />
      </span>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
          <span style={{ fontSize: 13.5, fontWeight: 600 }}>{spec.name}</span>
          <span
            className="pill"
            style={{ color: tone.c, background: tone.bg, fontSize: 11 }}
          >
            {health.ok ? (
              <>
                <BandIcon band={band ?? "safe"} size={11} />
                {BAND_LABEL[band ?? "safe"]}
              </>
            ) : (
              <>
                <FaultIcon size={11} />
                {health.label}
              </>
            )}
          </span>
        </div>

        <div
          style={{
            fontSize: 11.5,
            color: health.ok ? "var(--muted)" : "var(--ink-2)",
            marginTop: 4,
            lineHeight: 1.5,
          }}
        >
          {health.ok ? spec.blurb : health.detail}
        </div>

        {!health.ok && (
          <div className="mono" style={{ fontSize: 10.5, color: "var(--muted-2)", marginTop: 5 }}>
            {health.lastGood
              ? `last good reading ${ageLabel(health.lastGood, now)}`
              : "no good reading yet"}
          </div>
        )}
      </div>

      <div className="mono" style={{ fontSize: 14, fontWeight: 500, color: tone.c, whiteSpace: "nowrap" }}>
        {/* Falls back to the raw voltage so a live-but-off-scale probe still
            shows a moving number instead of a dash. */}
        {value !== null ? (
          <>
            {value.toFixed(spec.decimals)}
            <span style={{ fontSize: 10, color: "var(--muted-2)", marginLeft: 3 }}>{spec.unit}</span>
          </>
        ) : volts !== null ? (
          <>
            {volts.toFixed(3)}
            <span style={{ fontSize: 10, color: "var(--muted-2)", marginLeft: 3 }}>V</span>
          </>
        ) : (
          "—"
        )}
      </div>
    </div>
  );
}

export default function SensorStatus({
  readings,
  deviceState,
  now,
  title = "Sensor status",
}: {
  readings: Reading[];
  deviceState: DeviceState;
  now: number;
  title?: string;
}) {
  const latest = readings.length ? readings[readings.length - 1] : null;
  const rows = SENSOR_ORDER.map((key) => ({
    key,
    health: sensorHealth(readings, key, deviceState),
  }));
  const faults = rows.filter((r) => !r.health.ok).length;

  return (
    <div className="card card-pad">
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 10,
          marginBottom: 4,
        }}
      >
        <div style={{ fontSize: 14, fontWeight: 600 }}>{title}</div>
        <span
          className="pill"
          style={
            faults
              ? { color: "var(--critical)", background: "var(--critical-bg)" }
              : { color: "var(--safe)", background: "var(--safe-bg)" }
          }
        >
          {faults ? `${faults} of ${rows.length} not reading` : `${rows.length} of ${rows.length} reading`}
        </span>
      </div>

      {rows.map(({ key, health }) => (
        <Row key={key} sensorKey={key} health={health} latest={latest} now={now} />
      ))}

      <div style={{ fontSize: 11, color: "var(--muted-2)", marginTop: 10, lineHeight: 1.5 }}>
        A sensor that stops answering is shown as not reading — it is never charted as zero, and it
        is left out of the composite risk score.
      </div>
    </div>
  );
}
