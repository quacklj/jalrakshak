import type { Band, Reading, RelayId, SensorKey } from "./types";

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

/* ------------------------------------------------------------------ *
 * pH calibration
 *
 * The analog pH board (PH4502C / SEN0161 family) outputs a voltage that
 * FALLS as the water gets more alkaline. Two anchors define the line:
 * the voltage in pH 7.00 buffer, and how many volts one pH unit is worth.
 *
 * Factory-typical numbers are below. They are a starting point, not a
 * calibration — dip the probe in pH 7.00 buffer, read the volts on Live
 * Monitoring, and put that number in PH_NEUTRAL_V. For the slope, repeat
 * in pH 4.00 buffer: PH_VOLTS_PER_UNIT = (V_at_4 − V_at_7) / 3.
 * ------------------------------------------------------------------ */
export const PH_NEUTRAL_V = 2.5; // volts in pH 7.00 buffer
export const PH_VOLTS_PER_UNIT = 0.19; // volts per pH unit (falling with pH)

/**
 * What a wired pH board can actually put out. A floating ADS1115 input drifts
 * near zero, which is how a disconnected AOUT shows up.
 *
 * The ceiling is deliberately just above the ADS1115's own 3.3 V supply: if
 * the pH board is powered from 5 V its output can exceed the ADC's rail, which
 * both saturates the reading and stresses the input. Seeing values pinned up
 * here means "check the probe's supply", not "the water is acidic".
 */
export const PH_MIN_VALID_V = 0.08;
export const PH_MAX_VALID_V = 3.4;

export function isPhPlausible(volts: number): boolean {
  return volts >= PH_MIN_VALID_V && volts <= PH_MAX_VALID_V;
}

export function voltsToPh(volts: number): number | null {
  if (!isPhPlausible(volts)) return null;
  const ph = 7 + (PH_NEUTRAL_V - volts) / PH_VOLTS_PER_UNIT;
  // Outside 0–14 the arithmetic still returns a number, but it isn't a pH.
  if (ph < 0 || ph > 14) return null;
  return Math.round(ph * 100) / 100;
}

/* ------------------------------------------------------------------ *
 * TDS calibration
 *
 * DFRobot's gravity TDS probe, using their published cubic against the
 * probe voltage, with temperature compensation from the DS18B20. The
 * K factor is the one thing worth calibrating: put the probe in a solution
 * of known ppm and scale TDS_K until the reading matches.
 * ------------------------------------------------------------------ */
export const TDS_K = 1.0;
/** The probe's output span. Dry or unplugged both sit at the bottom. */
export const TDS_MIN_VALID_V = 0.02;
export const TDS_MAX_VALID_V = 2.6;

export function isTdsPlausible(volts: number): boolean {
  return volts >= TDS_MIN_VALID_V && volts <= TDS_MAX_VALID_V;
}

/**
 * Conductivity rises with temperature, so the same water reads higher when
 * warm. Without the DS18B20 we assume 25 °C and the number drifts with the
 * weather — which is why the temperature probe failing degrades this one too.
 */
export function voltsToTds(volts: number, tempC: number | null): number | null {
  if (!isTdsPlausible(volts)) return null;
  const t = tempC ?? 25;
  const compensated = volts / (1 + 0.02 * (t - 25));
  const ppm =
    (133.42 * compensated ** 3 - 255.86 * compensated ** 2 + 857.39 * compensated) * 0.5 * TDS_K;
  if (!Number.isFinite(ppm) || ppm < 0) return null;
  return Math.round(ppm * 10) / 10;
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

/** Same idea for pH: the voltage is real even when the calibration isn't. */
export const PH_VOLTS: SensorSpec = {
  key: "ph",
  name: "pH (probe voltage)",
  unit: "V",
  decimals: 3,
  safe: [PH_NEUTRAL_V - 0.3, PH_NEUTRAL_V + 0.3],
  watch: [PH_NEUTRAL_V - 0.6, PH_NEUTRAL_V + 0.6],
  warning: [PH_MIN_VALID_V, PH_MAX_VALID_V],
  extent: [0, 3.6],
  blurb: "raw AOUT on ADS1115 A0",
};

export const TDS_VOLTS: SensorSpec = {
  key: "tds",
  name: "TDS (probe voltage)",
  unit: "V",
  decimals: 3,
  safe: [0.05, 1.0],
  watch: [0.02, 1.6],
  warning: [0, TDS_MAX_VALID_V],
  extent: [0, 2.6],
  blurb: "raw AOUT on ADS1115 A1",
};

/* ------------------------------------------------------------------ *
 * Banding
 *
 * Drinking-water limits follow IS 10500:2012 — the "acceptable" figure is
 * the safe band, the "permissible in the absence of an alternate source"
 * figure is where warning ends.
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
  ph: {
    key: "ph",
    name: "pH",
    unit: "pH",
    decimals: 2,
    // IS 10500 acceptable: 6.5–8.5. No permissible relaxation is allowed for pH.
    safe: [6.5, 8.5],
    watch: [6.0, 9.0],
    warning: [5.0, 10.0],
    extent: [0, 14],
    blurb: "Analog probe · ADS1115 A0 (uncalibrated)",
  },
  tds: {
    key: "tds",
    name: "Total dissolved solids",
    unit: "ppm",
    decimals: 0,
    // IS 10500: 500 acceptable, 2000 permissible without an alternate source.
    safe: [0, 500],
    watch: [0, 1000],
    warning: [0, 2000],
    extent: [0, 2000],
    blurb: "Analog probe · ADS1115 A1 (temp-compensated)",
  },
  turbidity: {
    key: "turbidity",
    name: "Turbidity",
    unit: "NTU",
    decimals: 1,
    // IS 10500: 1 NTU acceptable, 5 permissible. The safe band is widened to 5
    // because this probe cannot resolve a single NTU without calibration.
    safe: [0, 5],
    watch: [0, 10],
    warning: [0, 25],
    // Full sensor span — the chart auto-zooms, this only stops it clipping a spike.
    extent: [0, TURBIDITY_MAX_NTU],
    blurb: "Analog probe · ADS1115 A2 (est. NTU)",
  },
};

export const SENSOR_ORDER: SensorKey[] = ["temperature", "ph", "tds", "turbidity"];
export const SENSOR_COUNT = SENSOR_ORDER.length;

/* ------------------------------------------------------------------ *
 * Pump relays
 * ------------------------------------------------------------------ */

export type RelaySpec = { id: RelayId; name: string; pin: number; blurb: string };

export const RELAYS: RelaySpec[] = [
  { id: "pump1", name: "Pump 1", pin: 14, blurb: "Relay IN1 · GPIO 14" },
  { id: "pump2", name: "Pump 2", pin: 18, blurb: "Relay IN2 · GPIO 18" },
];

/**
 * A pump left running is the one thing on this board that can do physical
 * damage — an overflowing tank, or a dry-running motor burning itself out.
 * There is no float switch wired to stop it, so the only backstop is a clock.
 * The server drops the command after this long, and the firmware runs the same
 * timer independently so a dead dashboard cannot leave a motor on.
 */
export const PUMP_MAX_RUN_MS = 5 * 60_000;

/**
 * If the node cannot reach the dashboard for this long it turns both relays
 * off by itself. Losing contact with a running pump is exactly when you least
 * want it latched on.
 */
export const PUMP_COMMS_FAILSAFE_MS = 30_000;

/** How often the firmware asks the server what the relays should be doing. */
export const PUMP_POLL_MS = 1_000;

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

/** The calibrated value each sensor contributes to scoring, or null. */
export function scoredValue(r: Reading, key: SensorKey): number | null {
  switch (key) {
    case "temperature":
      return r.tempC;
    case "ph":
      return r.ph;
    case "tds":
      return r.tds;
    case "turbidity":
      return r.turbidityNtu;
  }
}

/**
 * Composite risk, 0–100. Probabilistic OR across every sensor that is actually
 * reporting, so any one of them going critical is enough to light up the
 * network — and a sensor that is down contributes nothing rather than being
 * scored as a comfortable zero.
 */
export function riskScore(reading: Reading | null): number {
  if (!reading) return 0;
  let intact = 1;
  for (const key of SENSOR_ORDER) {
    const v = scoredValue(reading, key);
    if (v === null) continue;
    intact *= 1 - severity(v, SENSORS[key]);
  }
  return Math.round(100 * (1 - intact));
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
