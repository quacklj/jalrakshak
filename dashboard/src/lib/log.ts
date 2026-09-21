import { SENSORS, SENSOR_ORDER } from "./config";
import { sensorRaw, sensorValue } from "./derive";
import type { Reading } from "./types";

/**
 * One line per reading on the server console.
 *
 * The dashboard is the only place a deployed node's traffic is visible — on an
 * ESP32-S3 the USB serial port dies with every reset, so the node's own output
 * is gone the moment it is running on its own power. This is the replacement:
 * what arrived, when, and from which device.
 *
 * Set JALRAKSHA_LOG_READINGS=0 to silence it. At one reading a second that is
 * 86k lines a day, which is fine in a terminal you are watching and noise in a
 * hosting provider's log viewer.
 */
const ENABLED = process.env.JALRAKSHA_LOG_READINGS !== "0";

/**
 * A sensor's cell. Three cases, and they are deliberately distinguishable at a
 * glance because they need different fixes:
 *
 *   26.94    reading normally
 *   n/d      nothing came back from the probe at all
 *   n/d[8.0] the hardware answered, but with something the calibration cannot
 *            use — an 8 cm echo inside the blind zone, a floating 0.004 V pin.
 *            That distinction is the whole diagnosis, so the raw figure stays.
 *
 * Never 0 for a dead sensor, matching the rule the ingest payload follows: 0 is
 * a measurement, and 0 NTU reads as unusually clean water.
 */
function cell(r: Reading, key: (typeof SENSOR_ORDER)[number]): string {
  const spec = SENSORS[key];
  const value = sensorValue(r, key);
  if (value !== null) return `${value.toFixed(spec.decimals)} ${spec.unit}`.padStart(11);

  const raw = sensorRaw(r, key);
  return (raw === null ? "n/d" : `n/d[${raw.toFixed(1)}]`).padStart(11);
}

/** Built separately from the logging so it can be tested without a console. */
export function formatReading(r: Reading): string {
  const clock = new Date(r.t).toTimeString().slice(0, 8);
  // Driven off SENSOR_ORDER rather than a hand-written list, so a sixth sensor
  // shows up here the moment it is added rather than being silently dropped.
  const cells = SENSOR_ORDER.map((key) => cell(r, key)).join(" ");
  // "--" is not "off": it means the node sent no relay positions at all.
  const pumps = r.relays
    ? `${r.relays.pump1 ? "1" : "0"}${r.relays.pump2 ? "1" : "0"}`
    : "--";
  const rssi = r.rssi === undefined ? "" : ` ${String(r.rssi).padStart(4)} dBm`;
  return `${clock} ${r.deviceId.padEnd(12)}${cells}  pumps ${pumps}${rssi}`;
}

export function logReading(r: Reading) {
  if (!ENABLED) return;
  console.log(formatReading(r));
}
