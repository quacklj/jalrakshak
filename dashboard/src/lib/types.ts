export type SensorKey = "temperature" | "ph" | "tds" | "turbidity" | "level";

export type Band = "safe" | "watch" | "warning" | "critical";

export type DeviceState = "online" | "degraded" | "offline";

/** The two pump relays, by the GPIO they sit on. See config.ts RELAYS. */
export type RelayId = "pump1" | "pump2";

/** One sample as stored on the server. `t` is epoch milliseconds (server clock). */
export type Reading = {
  t: number;
  deviceId: string;

  /** Degrees Celsius. null when the DS18B20 reported DEVICE_DISCONNECTED_C. */
  tempC: number | null;

  /**
   * pH probe output in volts, straight off ADS1115 A0 (no divider).
   * null when the device sent no pH value at all.
   */
  phV: number | null;
  /**
   * pH units derived from phV against the two-point calibration in config.ts.
   * null when phV is missing or outside what a wired probe can produce.
   * Uncalibrated until you set PH_NEUTRAL_V from a buffer solution.
   */
  ph: number | null;

  /** TDS probe output in volts, straight off ADS1115 A1 (no divider). */
  tdsV: number | null;
  /**
   * Parts per million, from the DFRobot polynomial with temperature
   * compensation applied using tempC. null when tdsV is implausible.
   */
  tds: number | null;

  /**
   * Turbidity probe output in volts, with the 10k/15k divider already undone.
   * null when the device sent no turbidity value at all.
   */
  turbidityV: number | null;
  /**
   * Estimated NTU derived from turbidityV — uncalibrated, see lib/config.ts.
   * null when turbidityV is missing or outside the range a wired-up probe can
   * physically produce (see isTurbidityPlausible).
   */
  turbidityNtu: number | null;

  /**
   * Distance from the ultrasonic sensor's face down to the water surface, cm.
   * This is what the AJ-SR04M actually measures — it falls as the tank fills.
   * null when no echo came back at all.
   */
  distanceCm: number | null;
  /**
   * Tank fullness 0–100%, from distanceCm against the tank geometry in
   * config.ts. null when the distance is outside what the sensor can resolve
   * (see isDistancePlausible) — the tank has no depth until it's measured.
   */
  levelPct: number | null;

  /** Raw ADS1115 counts, kept for debugging the analog front end. */
  raw?: number;
  rssi?: number;
  uptimeMs?: number;
  /** Why the chip last restarted, e.g. "brownout". Reported by the firmware. */
  resetReason?: string;
  /** Free heap in bytes — a steady decline points at a leak. */
  heap?: number;

  /**
   * Relay positions the device reported it was actually holding when it sent
   * this reading — not what the dashboard asked for. The gap between the two
   * is the only way to tell "commanded" from "running".
   */
  relays?: Partial<Record<RelayId, boolean>>;
};

/** Payload the ESP32 posts to /api/ingest. */
export type IngestPayload = {
  device_id?: string;
  temp_c?: number | null;
  ph_v?: number | null;
  tds_v?: number | null;
  turbidity_v?: number | null;
  /** Ultrasonic distance to the water surface in cm. null when no echo. */
  distance_cm?: number | null;
  raw?: number;
  rssi?: number;
  uptime_ms?: number;
  reset_reason?: string;
  heap?: number;
  /** Actual relay positions, 0/1 or false/true. */
  relay1?: number | boolean;
  relay2?: number | boolean;
};

/** One pump as the dashboard and the API talk about it. */
export type RelayView = {
  id: RelayId;
  name: string;
  pin: number;
  blurb: string;
  /** What the dashboard is asking the device to do. */
  desired: boolean;
  /** When `desired` last changed, epoch ms. */
  since: number;
  /** Epoch ms at which the server will drop `desired` back to off. */
  autoOffAt: number | null;
  /** What the device last said it was doing, null if it has never reported. */
  actual: boolean | null;
  /** Epoch ms of the reading `actual` came from. */
  actualAt: number | null;
};
