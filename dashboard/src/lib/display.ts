import {
  LEVEL_DISTANCE,
  PH_VOLTS,
  SENSORS,
  TDS_VOLTS,
  TURBIDITY_VOLTS,
  bandOfValue,
  type SensorSpec,
} from "./config";
import { sensorHealth, sensorRaw, sensorValue, type SensorHealth } from "./derive";
import type { Band, DeviceState, Reading, SensorKey } from "./types";

/**
 * Turning one sensor into something chartable.
 *
 * Every derived sensor here shares a problem: the calibrated figure (NTU, pH,
 * ppm, % full) is only meaningful while the hardware sits on its characterised
 * curve, but the raw signal is real the whole time. Charting a clamped
 * calibrated value draws a flat line that reads as a dead feed, so when the
 * calibration gives up we chart the raw signal instead and say so. This used to
 * live inline in the turbidity card; with five sensors it belongs in one place.
 */

export type ChartPoint = { t: number; v: number | null };

/**
 * The honest raw-signal spec for each sensor that has one — volts for the three
 * analog probes, centimetres for the ultrasonic. The unit differs; the job does
 * not, so both go through the same fallback.
 */
export const RAW_SPEC: Partial<Record<SensorKey, SensorSpec>> = {
  ph: PH_VOLTS,
  tds: TDS_VOLTS,
  turbidity: TURBIDITY_VOLTS,
  level: LEVEL_DISTANCE,
};

/** "volts" is the historical name for "show me the raw signal". */
export type UnitMode = "auto" | "calibrated" | "volts";

export type SensorView = {
  key: SensorKey;
  /** The spec actually being displayed — calibrated, or the raw fallback. */
  spec: SensorSpec;
  value: number | null;
  band: Band;
  points: ChartPoint[];
  /** The other number, shown small beside the headline figure. */
  secondary?: string;
  health: SensorHealth;
  showingVolts: boolean;
};

export function sensorView(
  readings: Reading[],
  key: SensorKey,
  deviceState: DeviceState,
  mode: UnitMode = "auto",
): SensorView {
  const latest = readings.length ? readings[readings.length - 1] : null;
  const health = sensorHealth(readings, key, deviceState);
  const calibrated = SENSORS[key];
  const rawSpec = RAW_SPEC[key];

  const calValue = sensorValue(latest, key);
  const raw = sensorRaw(latest, key);

  // Only fall back where there is a raw signal to fall back to — the DS18B20 is
  // digital, so a missing temperature is simply missing.
  const showingVolts =
    rawSpec !== undefined &&
    (mode === "volts" || (mode === "auto" && calValue === null && raw !== null));

  const spec = showingVolts && rawSpec ? rawSpec : calibrated;
  const value = showingVolts ? raw : calValue;

  const points: ChartPoint[] = readings.map((r) =>
    showingVolts ? { t: r.t, v: sensorRaw(r, key) } : { t: r.t, v: sensorValue(r, key) },
  );

  let secondary: string | undefined;
  if (showingVolts) {
    secondary =
      calValue !== null
        ? `${calValue.toFixed(calibrated.decimals)} ${calibrated.unit}`
        : `${calibrated.unit} off scale`;
  } else if (raw !== null && rawSpec) {
    secondary = `${raw.toFixed(rawSpec.decimals)} ${rawSpec.unit} raw`;
  }

  return {
    key,
    spec,
    value,
    band: value === null ? "safe" : bandOfValue(value, spec),
    points,
    secondary,
    health,
    showingVolts,
  };
}

export function sensorViews(
  readings: Reading[],
  keys: SensorKey[],
  deviceState: DeviceState,
  mode: UnitMode = "auto",
): SensorView[] {
  return keys.map((k) => sensorView(readings, k, deviceState, mode));
}
