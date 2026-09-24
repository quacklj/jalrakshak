import { SERVO_POSITIONS, servoNormDeg, servoPlan, servoTurnMs } from "./config";
import { latestReading } from "./store";
import type { ServoCommand, ServoView } from "./types";

/**
 * What the dashboard is asking the servo to do.
 *
 * In memory and deliberately not persisted, for the same reason the relay
 * commands are not: a move that survives a restart and drives a valve the
 * moment the server comes back up is a hazard, not a feature. On a restart the
 * servo is simply left wherever it physically is, and the node keeps its own
 * belief about the angle — which is the only belief that was ever grounded in
 * having actually run the motor.
 */

type Command = {
  seq: number;
  command: ServoCommand;
  arg: number;
  issuedAt: number;
  source: string;
};

const globalRef = globalThis as typeof globalThis & { __jalrakshaServo?: Command };

function store(): Command {
  if (!globalRef.__jalrakshaServo) {
    // seq 0 is the boot state and is never executed: the node only acts on a
    // seq it has not seen, and it starts up having "seen" 0. So a fresh server
    // cannot move the servo until somebody actually asks it to.
    globalRef.__jalrakshaServo = {
      seq: 0,
      command: "stop",
      arg: 0,
      issuedAt: Date.now(),
      source: "boot",
    };
  }
  return globalRef.__jalrakshaServo;
}

export function isServoCommand(v: unknown): v is ServoCommand {
  return v === "goto" || v === "stop" || v === "zero" || v === "turn";
}

export function issueServo(command: ServoCommand, arg = 0, source = "dashboard"): Command {
  const prev = store();
  const next: Command = {
    seq: prev.seq + 1,
    command,
    arg: command === "goto" ? servoNormDeg(arg) : arg,
    issuedAt: Date.now(),
    source,
  };
  globalRef.__jalrakshaServo = next;
  return next;
}

/** Everything the UI needs: what was asked, and what the node says it did. */
export function servoView(): ServoView {
  const c = store();
  const latest = latestReading();

  const actualDeg = latest?.servoDeg ?? null;
  const moving = latest?.servoMoving ?? false;

  // The predicted move is drawn from where the NODE says it is, not from the
  // last thing the dashboard asked for. Planning from our own command would
  // compound the dashboard's guess on top of the node's guess.
  let targetDeg: number | null = null;
  let planDir: 1 | -1 | null = null;
  let planSweep: number | null = null;
  let planMs: number | null = null;

  if (c.command === "goto") {
    targetDeg = c.arg;
    if (actualDeg !== null) {
      const plan = servoPlan(actualDeg, c.arg);
      planDir = plan.dir;
      planSweep = plan.sweep;
      planMs = plan.ms;
    }
  } else if (c.command === "turn") {
    targetDeg = actualDeg;
    planDir = c.arg < 0 ? -1 : 1;
    planSweep = 360 * Math.min(1, Math.abs(c.arg));
    planMs = servoTurnMs(c.arg);
  }

  return {
    seq: c.seq,
    command: c.command,
    arg: c.arg,
    issuedAt: c.issuedAt,
    source: c.source,
    targetDeg,
    planDir,
    planSweep,
    planMs,
    actualDeg,
    actualAt: actualDeg === null ? null : (latest?.t ?? null),
    moving,
    movesSinceZero: latest?.servoMovesSinceZero ?? null,
    uncertain: latest?.servoUncertain ?? false,
    ackSeq: latest?.servoAckSeq ?? null,
  };
}

const LETTER: Record<ServoCommand, string> = {
  goto: "G",
  stop: "S",
  zero: "Z",
  turn: "T",
};

/**
 * The line the firmware polls: `<seq>,<letter>,<arg>`.
 *
 * Same reasoning as the relay poll's two bytes — the node has no JSON parser
 * and does not need one. The seq is what makes this safe to poll every second:
 * the node only acts when the seq changes, so re-reading the same line a
 * thousand times cannot re-run the move a thousand times.
 */
export function servoTextLine(): string {
  const c = store();
  return `${c.seq},${LETTER[c.command]},${Math.round(c.arg)}`;
}

/** The presets, for anything that needs to validate an incoming angle. */
export function isKnownPosition(deg: number): boolean {
  return SERVO_POSITIONS.some((p) => p.deg === servoNormDeg(deg));
}
