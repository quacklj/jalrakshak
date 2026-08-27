import {
  PH_VOLTS,
  SENSORS,
  TDS_VOLTS,
  TURBIDITY_VOLTS,
  bandOfValue,
  type SensorSpec,
} from "./config";
import { sensorHealth, sensorValue, sensorVolts, type SensorHealth } from "./derive";
import type { Band, DeviceState, Reading, SensorKey } from "./types";

/**
 * Turning one sensor into something chartable.
 *
 * Every analog probe here shares a problem: the calibrated figure (NTU, pH,
 * ppm) is only meaningful while the probe sits on its characterised curve, but
 * the voltage is real the whole time. Charting a clamped calibrated value draws
 * a flat line that reads as a dead feed, so when the calibration gives up we
 * chart the voltage instead and say so. This used to live inline in the
 * turbidity card; with four sensors it belongs in one place.
 */

export type ChartPoint = { t: number; v: number | null };

/** The honest-voltage spec for each probe, for when its calibration is off scale. */
const VOLT_SPEC: Partial<Record<SensorKey, SensorSpec>> = {
  ph: PH_VOLTS,
  tds: TDS_VOLTS,
  turbidity: TURBIDITY_VOLTS,
};

export type UnitMode = "auto" | "calibrated" | "volts";

export type SensorView = {
  key: SensorKey;
  /** The spec actually being displayed — calibrated, or the volts fallback. */
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
  const voltSpec = VOLT_SPEC[key];

  const calValue = sensorValue(latest, key);
  const volts = sensorVolts(latest, key);

  // Only fall back where there is a voltage to fall back to — the DS18B20 is
  // digital, so a missing temperature is simply missing.
  const showingVolts =
    voltSpec !== undefined &&
    (mode === "volts" || (mode === "auto" && calValue === null && volts !== null));

  const spec = showingVolts && voltSpec ? voltSpec : calibrated;
  const value = showingVolts ? volts : calValue;

  const points: ChartPoint[] = readings.map((r) =>
    showingVolts ? { t: r.t, v: sensorVolts(r, key) } : { t: r.t, v: sensorValue(r, key) },
  );

  let secondary: string | undefined;
  if (showingVolts) {
    secondary =
      calValue !== null
        ? `${calValue.toFixed(calibrated.decimals)} ${calibrated.unit}`
        : `${calibrated.unit} off scale`;
  } else if (volts !== null) {
    secondary = `${volts.toFixed(3)} V raw`;
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
