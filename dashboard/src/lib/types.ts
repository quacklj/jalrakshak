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

  /* ---- Servo, as the NODE believes it ---------------------------------- *
   * Every one of these is dead reckoning. The servo is a continuous-rotation
   * MG996R driven by a stopwatch, with no encoder and no feedback of any kind,
   * so "position" here means "where the node calculates it ended up". Treated
   * as a measurement it will quietly lie; the UI must never present it as one.
   * ---------------------------------------------------------------------- */

  /** Believed angle, 0–359.9°. null when the node reports no servo at all. */
  servoDeg: number | null;
  /** True while a timed move is still running. */
  servoMoving?: boolean;
  /**
   * Moves completed since the last re-zero. The drift proxy: every timed move
   * adds a little error, so this is how much the belief above has decayed.
   */
  servoMovesSinceZero?: number;
  /**
   * Set when a move was cut short — a STOP mid-travel, or a reset while
   * moving. The angle is then an interpolation of an interrupted move, which
   * is a materially weaker claim than a completed one.
   */
  servoUncertain?: boolean;
  /** The command sequence the node last acted on. See ServoView.seq. */
  servoAckSeq?: number;
  /** Duration of the last free spin, ms, timed on the node. */
  servoSpinMs?: number;
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

  /** Believed servo angle in degrees, dead reckoned by the node. */
  servo_deg?: number | null;
  servo_moving?: number | boolean;
  servo_moves?: number;
  servo_uncertain?: number | boolean;
  servo_ack?: number;
  /** Duration of the last free spin, ms, timed on the node. */
  servo_spin_ms?: number;
};

/* ------------------------------------------------------------------ *
 * Servo
 * ------------------------------------------------------------------ */

/**
 * What the dashboard is asking the servo to do.
 *
 *   goto  rotate to an absolute angle, shortest path by TIME
 *   stop  halt immediately, wherever it is
 *   zero  declare the current physical position to be 0°, without moving
 *   turn  a whole revolution, for flushing — a movement, not a position
 *
 * The rest exist for the calibration bench, where you are measuring the servo
 * rather than using it, and want the motor driven for an exact time with no
 * angle maths in the way:
 *
 *   run       drive for a signed number of milliseconds, then stop
 *   spin      drive continuously until told to stop, for timing revolutions
 *   testangle assume the mark is at 0, then travel to an angle and stop
 *   steptest  0 → 120 → 240 → 360 with a pause at each, from the 0 mark
 */
export type ServoCommand =
  | "goto"
  | "stop"
  | "zero"
  | "turn"
  | "run"
  | "spin"
  | "testangle"
  | "steptest";

export type ServoView = {
  /**
   * Bumped on every new command. The node compares it against the last seq it
   * acted on, which is what stops a repeated poll from re-running the same
   * move over and over — the poll is level-triggered, the command edge-triggered.
   */
  seq: number;
  command: ServoCommand;
  /** Target angle for "goto"; signed revolutions for "turn"; 0 otherwise. */
  arg: number;
  issuedAt: number;
  source: string;

  /** Where the dashboard reckons it will end up, for the target ghost marker. */
  targetDeg: number | null;
  /** Direction and duration the dashboard predicts for this move. */
  planDir: 1 | -1 | null;
  planSweep: number | null;
  planMs: number | null;

  /* ---- what the NODE last said, which is a different fact ---- */
  actualDeg: number | null;
  actualAt: number | null;
  moving: boolean;
  movesSinceZero: number | null;
  uncertain: boolean;
  /** null until the node has acknowledged any command at all. */
  ackSeq: number | null;
  /**
   * How long the last free spin ran, milliseconds, as timed by the node. This
   * is the measurement the "spin N turns and stop" bench test produces, and it
   * has to come from the node because network latency would corrupt anything
   * the browser timed itself.
   */
  lastSpinMs: number | null;
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
