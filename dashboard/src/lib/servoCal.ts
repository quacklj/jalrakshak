import fs from "node:fs";
import path from "node:path";

/**
 * Servo calibration, owned by the server rather than by the node's flash.
 *
 * The bench sketch kept these in ESP32 Preferences, which works right up until
 * you reflash the node and silently lose an afternoon of bench work. Holding
 * them here means the numbers outlive the firmware, the dashboard can show what
 * the node is actually running on, and a replacement board picks up the same
 * calibration the moment it boots.
 *
 * The node fetches them; it does not store them.
 */

export type ServoCal = {
  /** Pulse width the servo reads as "stop". Trim if the horn creeps. */
  stopPulse: number;
  /**
   * How far either side of neutral to drive. THIS IS PART OF THE TIMING
   * CALIBRATION, not a separate setting: the milliseconds below were measured
   * at this speed, so changing it invalidates all four of them.
   */
  speedOffset: number;
  /** Milliseconds from the 0 mark, forward. Must increase. */
  t120: number;
  t240: number;
  t360: number;
  /** Full turn in reverse. Reverse runs slightly faster than forward. */
  tRev: number;
};

export const SERVO_CAL_DEFAULTS: ServoCal = {
  stopPulse: 1500,
  speedOffset: 300,
  t120: 715,
  t240: 1460,
  t360: 2300,
  tRev: 2080,
};

const PERSIST = process.env.JALRAKSHA_PERSIST !== "0";
const DATA_DIR = process.env.JALRAKSHA_DATA_DIR || path.join(process.cwd(), "data");
const CAL_FILE = path.join(DATA_DIR, "servo-cal.json");

type CalStore = { cal: ServoCal; rev: number };

const globalRef = globalThis as typeof globalThis & { __jalrakshaServoCal?: CalStore };

function store(): CalStore {
  if (!globalRef.__jalrakshaServoCal) {
    globalRef.__jalrakshaServoCal = { cal: { ...SERVO_CAL_DEFAULTS }, rev: 1 };
    if (PERSIST) {
      try {
        if (fs.existsSync(CAL_FILE)) {
          const saved = JSON.parse(fs.readFileSync(CAL_FILE, "utf8")) as Partial<ServoCal>;
          globalRef.__jalrakshaServoCal.cal = sanitise({ ...SERVO_CAL_DEFAULTS, ...saved }).cal;
        }
      } catch (err) {
        console.warn("[servo-cal] could not restore, using defaults:", (err as Error).message);
      }
    }
  }
  return globalRef.__jalrakshaServoCal;
}

/**
 * Drops keys whose value is undefined.
 *
 * Object spread keeps an explicitly-undefined key and lets it win, so
 * `{...current, stopPulse: undefined}` erases stopPulse rather than leaving it
 * alone. Every partial update arrives shaped exactly like that, so without this
 * a request that sets one field quietly blanks the other five and then fails
 * validation on ordering — which looks nothing like the actual cause.
 */
function defined(input: Partial<ServoCal>): Partial<ServoCal> {
  return Object.fromEntries(
    Object.entries(input).filter(([, v]) => v !== undefined),
  ) as Partial<ServoCal>;
}

/**
 * Rejects a calibration that cannot describe a real servo.
 *
 * The ordering rule is the important one: the times must increase with angle,
 * because they are cumulative from the 0 mark. A set where 240° is quicker than
 * 120° makes the interpolation run backwards and the servo drive the wrong way,
 * which is a confusing thing to debug on a bench with a motor moving.
 */
export function sanitise(input: Partial<ServoCal>): { cal: ServoCal; error: string | null } {
  const c: ServoCal = { ...SERVO_CAL_DEFAULTS, ...defined(input) };
  const int = (v: number, lo: number, hi: number) =>
    Math.round(Math.max(lo, Math.min(hi, Number.isFinite(v) ? v : 0)));

  c.stopPulse = int(c.stopPulse, 1400, 1600);
  c.speedOffset = int(c.speedOffset, 50, 500);
  c.t120 = int(c.t120, 1, 60000);
  c.t240 = int(c.t240, 1, 60000);
  c.t360 = int(c.t360, 1, 60000);
  c.tRev = int(c.tRev, 1, 60000);

  if (!(c.t120 < c.t240 && c.t240 < c.t360)) {
    return { cal: c, error: "times must increase: 120° < 240° < 360°" };
  }
  return { cal: c, error: null };
}

export function getCal(): ServoCal {
  return { ...store().cal };
}

/** Bumped on every save, so the node can tell a change from a repeat. */
export function getCalRev(): number {
  return store().rev;
}

export function setCal(input: Partial<ServoCal>): { cal: ServoCal; error: string | null } {
  const { cal, error } = sanitise({ ...store().cal, ...defined(input) });
  if (error) return { cal: store().cal, error };

  const s = store();
  s.cal = cal;
  s.rev++;

  if (PERSIST) {
    try {
      fs.mkdirSync(DATA_DIR, { recursive: true });
      fs.writeFileSync(CAL_FILE, JSON.stringify(cal, null, 2));
    } catch (err) {
      // A read-only filesystem is not fatal — it just will not survive a restart.
      console.warn("[servo-cal] not persisted:", (err as Error).message);
    }
  }
  return { cal, error: null };
}

export function resetCal(): ServoCal {
  return setCal(SERVO_CAL_DEFAULTS).cal;
}

/** The line the node fetches: six integers, no JSON parser needed. */
export function calTextLine(): string {
  const c = getCal();
  return `${getCalRev()},${c.stopPulse},${c.speedOffset},${c.t120},${c.t240},${c.t360},${c.tRev}`;
}
