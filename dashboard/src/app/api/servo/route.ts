import { NextResponse } from "next/server";
import {
  SERVO_DRIFT_WARNING,
  SERVO_DRIFT_WATCH,
  SERVO_POLL_MS,
  SERVO_POSITIONS,
} from "@/lib/config";
import { isServoCommand, issueServo, servoTextLine, servoView } from "@/lib/servo";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function authorized(req: Request): boolean {
  const expected = process.env.DEVICE_TOKEN;
  if (!expected) return true;
  const header = req.headers.get("x-device-token");
  const query = new URL(req.url).searchParams.get("token");
  return header === expected || query === expected;
}

/**
 * GET /api/servo            → full state for the dashboard
 * GET /api/servo?fmt=text   → "7,G,240" — what the ESP32 polls
 */
export async function GET(req: Request) {
  if (new URL(req.url).searchParams.get("fmt") === "text") {
    if (!authorized(req)) return new Response("!", { status: 401 });
    return new Response(servoTextLine(), {
      headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
    });
  }

  return NextResponse.json({
    now: Date.now(),
    pollMs: SERVO_POLL_MS,
    positions: SERVO_POSITIONS,
    driftWatch: SERVO_DRIFT_WATCH,
    driftWarning: SERVO_DRIFT_WARNING,
    servo: servoView(),
  });
}

/**
 * POST /api/servo  { command: "goto", deg: 240 }
 *                  { command: "stop" }
 *                  { command: "zero" }
 *                  { command: "turn", revs: 1 }
 */
export async function POST(req: Request) {
  if (!authorized(req)) {
    return NextResponse.json({ ok: false, error: "bad device token" }, { status: 401 });
  }

  let body: {
    command?: unknown;
    deg?: unknown;
    revs?: unknown;
    ms?: unknown;
    dir?: unknown;
    pauseMs?: unknown;
  };
  try {
    body = (await req.json()) as typeof body;
  } catch {
    return NextResponse.json({ ok: false, error: "body must be JSON" }, { status: 400 });
  }

  if (!isServoCommand(body.command)) {
    return NextResponse.json(
      { ok: false, error: "command must be goto, stop, zero or turn" },
      { status: 400 },
    );
  }

  let arg = 0;
  if (body.command === "goto") {
    if (typeof body.deg !== "number" || !Number.isFinite(body.deg)) {
      return NextResponse.json({ ok: false, error: "deg must be a number" }, { status: 400 });
    }
    arg = body.deg;
  }
  if (body.command === "testangle") {
    if (typeof body.deg !== "number" || !Number.isFinite(body.deg)) {
      return NextResponse.json({ ok: false, error: "deg must be a number" }, { status: 400 });
    }
    arg = body.deg;
  }
  if (body.command === "run") {
    // Signed: the sign is the direction, the magnitude is the duration. Capped
    // well under a minute because this drives a motor with nothing watching it.
    if (typeof body.ms !== "number" || !Number.isFinite(body.ms) || body.ms === 0) {
      return NextResponse.json({ ok: false, error: "ms must be a non-zero number" }, { status: 400 });
    }
    if (Math.abs(body.ms) > 30000) {
      return NextResponse.json({ ok: false, error: "ms must be under 30000" }, { status: 400 });
    }
    arg = Math.round(body.ms);
  }
  if (body.command === "spin") {
    if (body.dir !== 1 && body.dir !== -1) {
      return NextResponse.json({ ok: false, error: "dir must be 1 or -1" }, { status: 400 });
    }
    arg = body.dir;
  }
  if (body.command === "steptest") {
    const pause = typeof body.pauseMs === "number" ? body.pauseMs : 2000;
    if (!Number.isFinite(pause) || pause < 0 || pause > 30000) {
      return NextResponse.json({ ok: false, error: "pauseMs must be 0-30000" }, { status: 400 });
    }
    arg = Math.round(pause);
  }
  if (body.command === "turn") {
    // A turn is a flush, not a winder: one lap each way, and never zero laps.
    if (typeof body.revs !== "number" || !Number.isFinite(body.revs) || body.revs === 0) {
      return NextResponse.json(
        { ok: false, error: "revs must be a non-zero number" },
        { status: 400 },
      );
    }
    arg = Math.sign(body.revs);
  }

  issueServo(body.command, arg);
  return NextResponse.json({ ok: true, now: Date.now(), servo: servoView() });
}
