import {
  DEGRADED_MS,
  ONLINE_MS,
  SENSORS,
  TURBIDITY_CLEAR_V,
  TURBIDITY_MAX_VALID_V,
  TURBIDITY_MIN_VALID_V,
  TURBIDITY_OPAQUE_V,
} from "./config";
import type { DeviceState, Reading, SensorKey } from "./types";

export function deviceStateFor(latest: Reading | null, now = Date.now()): DeviceState {
  if (!latest) return "offline";
  const age = now - latest.t;
  if (age <= ONLINE_MS) return "online";
  if (age <= DEGRADED_MS) return "degraded";
  return "offline";
}

export function ageLabel(t: number | null, now = Date.now()): string {
  if (t === null) return "never";
  const s = Math.max(0, Math.round((now - t) / 1000));
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ${s % 60}s ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ${m % 60}m ago`;
  return `${Math.floor(h / 24)}d ago`;
}

/* ------------------------------------------------------------------ *
 * Per-sensor health
 *
 * A sensor is only "reading" when the device is talking to us AND the probe
 * itself answered. Those are different failures with different fixes, so the
 * UI names which one happened rather than showing a plausible-looking value.
 * ------------------------------------------------------------------ */

export type HealthCode =
  | "reading"
  | "no-device"
  | "not-detected"
  | "out-of-range"
  /** Signal is live and trustworthy, but past the end of the probe's curve. */
  | "off-scale";

export type SensorHealth = {
  code: HealthCode;
  ok: boolean;
  label: string;
  detail: string;
  /** Timestamp of the most recent sample this sensor actually produced. */
  lastGood: number | null;
};

export function sensorValue(reading: Reading | null, key: SensorKey): number | null {
  if (!reading) return null;
  return key === "temperature" ? reading.tempC : reading.turbidityNtu;
}

function lastGoodAt(readings: Reading[], key: SensorKey): number | null {
  for (let i = readings.length - 1; i >= 0; i--) {
    if (sensorValue(readings[i], key) !== null) return readings[i].t;
  }
  return null;
}

export function sensorHealth(
  readings: Reading[],
  key: SensorKey,
  deviceState: DeviceState,
): SensorHealth {
  const latest = readings.length ? readings[readings.length - 1] : null;
  const lastGood = lastGoodAt(readings, key);
  const name = SENSORS[key].name;

  if (!latest || deviceState === "offline") {
    return {
      code: "no-device",
      ok: false,
      label: "No device",
      detail: latest
        ? "The node has stopped reporting, so this sensor's state is unknown."
        : "Waiting for the first payload from the ESP32.",
      lastGood,
    };
  }

  if (sensorValue(latest, key) !== null) {
    return { code: "reading", ok: true, label: "Reading", detail: `${name} reporting normally.`, lastGood };
  }

  if (key === "temperature") {
    return {
      code: "not-detected",
      ok: false,
      label: "Not detected",
      detail:
        "The DS18B20 is not answering on the 1-Wire bus. Check the data line on GPIO4 and the 4.7k pull-up to 3.3V.",
      lastGood,
    };
  }

  // Turbidity: the device reported, but the voltage can't have come from a
  // correctly wired probe. Which way it failed points at a different fault.
  const volts = latest.turbidityV;
  if (volts === null) {
    return {
      code: "not-detected",
      ok: false,
      label: "Not detected",
      detail: "The device sent no turbidity value. Check the ADS1115 on I2C (SDA GPIO8, SCL GPIO9).",
      lastGood,
    };
  }
  if (volts < TURBIDITY_MIN_VALID_V) {
    return {
      code: "not-detected",
      ok: false,
      label: "Not detected",
      detail: `Probe reading ${volts.toFixed(3)} V — near zero, so AOUT is likely unplugged and the 15k is pulling the divider to ground.`,
      lastGood,
    };
  }
  if (volts <= TURBIDITY_MAX_VALID_V) {
    // The probe is alive and the voltage is real — it is just below the bottom
    // of the characterised curve, so no honest NTU can be derived from it.
    return {
      code: "off-scale",
      ok: false,
      label: "Off scale",
      detail: `Probe steady at ${volts.toFixed(3)} V, below the ${TURBIDITY_OPAQUE_V} V where the NTU curve ends. The signal is live — chart the voltage. Clear water should sit near ${TURBIDITY_CLEAR_V} V, so this points at the probe's 5V supply rather than the water.`,
      lastGood,
    };
  }
  return {
    code: "out-of-range",
    ok: false,
    label: "Out of range",
    detail: `Probe reading ${volts.toFixed(3)} V, above the ${TURBIDITY_MAX_VALID_V} V a wired probe can produce. The 10k/15k divider is probably missing and the ADS1115 is saturating.`,
    lastGood,
  };
}

/**
 * Counts restarts by watching the device's own uptime counter go backwards.
 * A node that keeps resetting is the single most useful thing to surface: it
 * explains missing data, and on the S3 it also explains a dead Serial Monitor,
 * because each reset re-enumerates the USB port.
 */
export function rebootStats(readings: Reading[]): {
  count: number;
  lastReason: string | null;
  medianUptimeS: number | null;
} {
  let count = 0;
  let prev: number | null = null;
  const uptimes: number[] = [];

  for (const r of readings) {
    const u = r.uptimeMs;
    if (u === undefined) continue;
    if (prev !== null && u < prev) {
      count++;
      uptimes.push(prev / 1000);
    }
    prev = u;
  }

  const withReason = [...readings].reverse().find((r) => r.resetReason);
  uptimes.sort((a, b) => a - b);

  return {
    count,
    lastReason: withReason?.resetReason ?? null,
    medianUptimeS: uptimes.length ? Math.round(uptimes[Math.floor(uptimes.length / 2)]) : null,
  };
}

/** Median gap between samples — the device's effective reporting period. */
export function medianPeriodMs(readings: Reading[]): number | null {
  if (readings.length < 3) return null;
  const tail = readings.slice(-60);
  const gaps: number[] = [];
  for (let i = 1; i < tail.length; i++) gaps.push(tail[i].t - tail[i - 1].t);
  gaps.sort((a, b) => a - b);
  return gaps[Math.floor(gaps.length / 2)];
}

export type TimelineBucket = {
  state: DeviceState;
  label: string;
  count: number;
  /** False for hours before the node's first ever reading — those aren't downtime. */
  observed: boolean;
};

/**
 * Buckets the last 24 h into hourly slots and marks each by how much of the
 * expected traffic actually arrived. Empty hours before the first ever
 * reading are reported as offline, same as the design's connectivity strip.
 */
export function connectivityTimeline(
  readings: Reading[],
  now = Date.now(),
  buckets = 24,
): TimelineBucket[] {
  const period = medianPeriodMs(readings) ?? 10_000;
  const slot = 3_600_000;
  const counts = new Array<number>(buckets).fill(0);
  const start = now - buckets * slot;
  for (const r of readings) {
    if (r.t < start) continue;
    const idx = Math.min(buckets - 1, Math.floor((r.t - start) / slot));
    if (idx >= 0) counts[idx]++;
  }

  const firstSeen = readings.length ? readings[0].t : null;

  return counts.map((count, i) => {
    const slotStart = start + i * slot;
    // The newest bucket is only part-way through its hour — scale what we expect.
    const elapsed = Math.min(slot, now - slotStart);
    const expected = Math.max(1, Math.round(elapsed / period));
    const ratio = count / expected;
    const state: DeviceState = ratio >= 0.7 ? "online" : ratio > 0 ? "degraded" : "offline";
    const observed = firstSeen !== null && slotStart + slot > firstSeen;
    const hoursAgo = buckets - i;
    return {
      state,
      count,
      observed,
      label: observed ? `-${hoursAgo}h · ${count} readings` : `-${hoursAgo}h · before first reading`,
    };
  });
}

/** Uptime across the hours the node has actually existed for, not a flat 24 h. */
export function uptimePct(timeline: TimelineBucket[]): number {
  const seen = timeline.filter((b) => b.observed);
  if (!seen.length) return 0;
  const good = seen.filter((b) => b.state === "online").length;
  const partial = seen.filter((b) => b.state === "degraded").length;
  return Math.round(((good + partial * 0.5) / seen.length) * 100);
}
