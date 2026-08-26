"use client";

import { SENSORS, SENSOR_ORDER } from "@/lib/config";
import { ageLabel, sensorHealth } from "@/lib/derive";
import type { DeviceState, Reading } from "@/lib/types";

/**
 * Sits at the top of a page when something is wrong with the feed, and stays
 * out of the way when it isn't. Device-level problems win over sensor-level
 * ones: if nothing is arriving, naming a broken probe would be a guess.
 */
export default function ConnectionBanner({
  readings,
  deviceState,
  now,
}: {
  readings: Reading[];
  deviceState: DeviceState;
  now: number;
}) {
  const latest = readings.length ? readings[readings.length - 1] : null;

  let tone: "critical" | "watch" | null = null;
  let title = "";
  let detail = "";

  if (!latest) {
    tone = "watch";
    title = "No data yet";
    detail = "The dashboard is running but the ESP32 has not posted a reading. Check SERVER_URL and Wi-Fi.";
  } else if (deviceState === "offline") {
    tone = "critical";
    title = "Device offline";
    detail = `Last payload ${ageLabel(latest.t, now)}. Readings below are the last known values, not current ones.`;
  } else if (deviceState === "degraded") {
    tone = "watch";
    title = "Device reporting late";
    detail = `Last payload ${ageLabel(latest.t, now)}. Expect a reading every few seconds.`;
  } else {
    const faults = SENSOR_ORDER.map((key) => ({
      key,
      health: sensorHealth(readings, key, deviceState),
    })).filter((r) => !r.health.ok);

    if (faults.length) {
      const first = faults[0];
      // "Off scale" means the probe IS answering, so don't call it silent.
      const offScale = first.health.code === "off-scale";
      tone = offScale ? "watch" : "critical";

      if (faults.length > 1) {
        title = "Both sensors need attention";
        detail = `The node is online but neither probe is usable. ${first.health.detail}`;
      } else {
        title = `${SENSORS[first.key].name} ${first.health.label.toLowerCase()}`;
        detail = offScale
          ? first.health.detail
          : `The node is online and ${SENSORS[first.key].name.toLowerCase()} is not answering. ${first.health.detail}`;
      }
    }
  }

  if (!tone) return null;

  const color = tone === "critical" ? "var(--critical)" : "var(--watch)";
  const bg = tone === "critical" ? "var(--critical-bg)" : "var(--watch-bg)";

  return (
    <div
      role="status"
      style={{
        display: "flex",
        gap: 12,
        alignItems: "flex-start",
        background: bg,
        border: `1px solid ${color}22`,
        borderRadius: "var(--radius-md)",
        padding: "13px 16px",
        marginBottom: 20,
      }}
    >
      <span style={{ color, display: "inline-flex", marginTop: 1, flexShrink: 0 }}>
        <svg width={16} height={16} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.7}>
          <path d="M8 2.2 14.4 13.4H1.6z" strokeLinejoin="round" />
          <path d="M8 6.4v3.1" strokeLinecap="round" />
          <circle cx={8} cy={11.2} r={0.6} fill="currentColor" stroke="none" />
        </svg>
      </span>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color }}>{title}</div>
        <div style={{ fontSize: 12, color: "var(--ink-2)", marginTop: 3, lineHeight: 1.55 }}>
          {detail}
        </div>
      </div>
    </div>
  );
}
