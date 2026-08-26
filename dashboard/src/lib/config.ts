import type { Band, SensorKey } from "./types";

/**
 * Everything tunable lives here so the firmware and the dashboard can be
 * calibrated together without hunting through components.
 */

export const DEFAULT_DEVICE_ID = "ESP32-JR01";
export const DEVICE_LABEL = "Jalraksha field node";

/** Ring-buffer size. 8640 samples ≈ 24 h at one reading every 10 s. */
export const MAX_READINGS = 8640;

/** Device is "online" under this age, "degraded" under the second, else offline. */
export const ONLINE_MS = 30_000;
export const DEGRADED_MS = 180_000;

/* ------------------------------------------------------------------ *
 * Turbidity calibration
 *
 * The sensor is a voltage-out probe: highest in clear water, dropping as
 * the water gets cloudy. Until it is calibrated against formazin standards
 * this is a linear map, so treat NTU as an estimate, not a measurement.
 * Put the voltage you read in clean water into CLEAR_WATER_V.
 * ------------------------------------------------------------------ */
export const TURBIDITY_CLEAR_V = 4.2; // volts at ~0 NTU
export const TURBIDITY_OPAQUE_V = 2.5; // volts at TURBIDITY_MAX_NTU
export const TURBIDITY_MAX_NTU = 3000;

/**
 * Voltages a correctly wired probe can actually produce. Outside this window
 * the number is a wiring fault, not water:
 *   · at/near 0 V   → AOUT disconnected, so the 15k pulls the divider to ground
 *   · above ~5 V    → divider missing or wrong, and the ADS1115 is saturating
 * Water that genuinely opaque bottoms out around 2.5 V, so the floor has plenty
 * of headroom before it can reject a real reading.
 */
export const TURBIDITY_MIN_VALID_V = 0.05;
export const TURBIDITY_MAX_VALID_V = 5.0;

export function isTurbidityPlausible(volts: number): boolean {
  return volts >= TURBIDITY_MIN_VALID_V && volts <= TURBIDITY_MAX_VALID_V;
}

/**
 * The probe's characterised curve only spans TURBIDITY_OPAQUE_V..TURBIDITY_CLEAR_V.
 * Below that we know the water is past TURBIDITY_MAX_NTU but not by how much, so
 * returning the clamped ceiling would draw a flat line that looks like a frozen
 * feed. null means "off the scale" — chart the voltage instead.
 */
export function voltsToNtu(volts: number): number | null {
  if (volts < TURBIDITY_OPAQUE_V) return null;
  const span = TURBIDITY_CLEAR_V - TURBIDITY_OPAQUE_V;
  const ntu = ((TURBIDITY_CLEAR_V - volts) / span) * TURBIDITY_MAX_NTU;
  return Math.round(Math.max(0, Math.min(TURBIDITY_MAX_NTU, ntu)) * 10) / 10;
}

/** Chartable spec for the raw probe voltage — the honest signal while uncalibrated. */
export const TURBIDITY_VOLTS: SensorSpec = {
  key: "turbidity",
  name: "Turbidity (probe voltage)",
  unit: "V",
  decimals: 3,
  safe: [TURBIDITY_OPAQUE_V, 4.5],
  watch: [1.5, 4.8],
  warning: [0.3, 5.0],
  extent: [0, 5.5],
  blurb: "raw AOUT, 10k/15k divider undone",
};

/* ------------------------------------------------------------------ *
 * Banding
 * ------------------------------------------------------------------ */

export type SensorSpec = {
  key: SensorKey;
  name: string;
  unit: string;
  decimals: number;
  /** Inclusive edges: inside `safe` is fine, outside `warning` is critical. */
  safe: [number, number];
  watch: [number, number];
  warning: [number, number];
  /** Chart y-axis extent. */
  extent: [number, number];
  blurb: string;
};

export const SENSORS: Record<SensorKey, SensorSpec> = {
  temperature: {
    key: "temperature",
    name: "Water temperature",
    unit: "°C",
    decimals: 2,
    safe: [10, 30],
    watch: [7, 33],
    warning: [2, 40],
    extent: [0, 45],
    blurb: "DS18B20 · 1-Wire on GPIO4",
  },
  turbidity: {
    key: "turbidity",
    name: "Turbidity",
    unit: "NTU",
    decimals: 1,
    safe: [0, 5],
    watch: [0, 10],
    warning: [0, 25],
    // Full sensor span — the chart auto-zooms, this only stops it clipping a spike.
    extent: [0, TURBIDITY_MAX_NTU],
    blurb: "Analog probe · ADS1115 A2 (est. NTU)",
  },
};

export const SENSOR_ORDER: SensorKey[] = ["temperature", "turbidity"];

export function bandOfValue(value: number, spec: SensorSpec): Band {
  if (value >= spec.safe[0] && value <= spec.safe[1]) return "safe";
  if (value >= spec.watch[0] && value <= spec.watch[1]) return "watch";
  if (value >= spec.warning[0] && value <= spec.warning[1]) return "warning";
  return "critical";
}

/** 0 = squarely in range, 1 = far past the critical edge. */
export function severity(value: number, spec: SensorSpec): number {
  if (value >= spec.safe[0] && value <= spec.safe[1]) return 0;
  const low = value < spec.safe[0];
  const safeEdge = low ? spec.safe[0] : spec.safe[1];
  const watchEdge = low ? spec.watch[0] : spec.watch[1];
  const warnEdge = low ? spec.warning[0] : spec.warning[1];
  const d = Math.abs(value - safeEdge);
  const toWatch = Math.abs(watchEdge - safeEdge) || 1;
  const toWarn = Math.abs(warnEdge - watchEdge) || 1;
  if (d <= toWatch) return (d / toWatch) * 0.3;
  if (d <= toWatch + toWarn) return 0.3 + ((d - toWatch) / toWarn) * 0.35;
  return Math.min(1, 0.65 + ((d - toWatch - toWarn) / toWarn) * 0.35);
}

/**
 * Composite risk, 0–100. Probabilistic OR of the two severities, so either
 * sensor going critical on its own is enough to light up the network.
 */
export function riskScore(tempC: number | null, ntu: number | null): number {
  // A sensor that isn't reporting contributes nothing — it must not be scored
  // as if it were reading zero, and it must not mask the other sensor either.
  const st = tempC === null ? 0 : severity(tempC, SENSORS.temperature);
  const su = ntu === null ? 0 : severity(ntu, SENSORS.turbidity);
  return Math.round(100 * (1 - (1 - st) * (1 - su)));
}

export function bandOfScore(score: number): Band {
  if (score >= 80) return "critical";
  if (score >= 55) return "warning";
  if (score >= 30) return "watch";
  return "safe";
}

export const BAND_LABEL: Record<Band, string> = {
  safe: "Safe",
  watch: "Watch",
  warning: "Warning",
  critical: "Critical",
};
