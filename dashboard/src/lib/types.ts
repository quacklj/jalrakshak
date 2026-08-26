export type SensorKey = "temperature" | "turbidity";

export type Band = "safe" | "watch" | "warning" | "critical";

export type DeviceState = "online" | "degraded" | "offline";

/** One sample as stored on the server. `t` is epoch milliseconds (server clock). */
export type Reading = {
  t: number;
  deviceId: string;
  /** Degrees Celsius. null when the DS18B20 reported DEVICE_DISCONNECTED_C. */
  tempC: number | null;
  /**
   * Raw sensor output in volts, with the 10k/15k divider already undone.
   * null when the device sent no turbidity value at all.
   */
  turbidityV: number | null;
  /**
   * Estimated NTU derived from turbidityV — uncalibrated, see lib/config.ts.
   * null when turbidityV is missing or outside the range a wired-up probe can
   * physically produce (see isTurbidityPlausible).
   */
  turbidityNtu: number | null;
  /** Raw ADS1115 counts, kept for debugging the analog front end. */
  raw?: number;
  rssi?: number;
  uptimeMs?: number;
  /** Why the chip last restarted, e.g. "brownout". Reported by the firmware. */
  resetReason?: string;
  /** Free heap in bytes — a steady decline points at a leak. */
  heap?: number;
};

/** Payload the ESP32 posts to /api/ingest. */
export type IngestPayload = {
  device_id?: string;
  temp_c?: number | null;
  turbidity_v?: number | null;
  raw?: number;
  rssi?: number;
  uptime_ms?: number;
  reset_reason?: string;
  heap?: number;
};
