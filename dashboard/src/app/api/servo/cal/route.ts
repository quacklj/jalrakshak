import { NextResponse } from "next/server";
import { SERVO_CAL_DEFAULTS, calTextLine, getCal, getCalRev, resetCal, setCal } from "@/lib/servoCal";

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
 * GET /api/servo/cal            → JSON for the calibration page
 * GET /api/servo/cal?fmt=text   → "rev,stop,speed,t120,t240,t360,tRev" for the node
 */
export async function GET(req: Request) {
  if (new URL(req.url).searchParams.get("fmt") === "text") {
    if (!authorized(req)) return new Response("!", { status: 401 });
    return new Response(calTextLine(), {
      headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
    });
  }
  return NextResponse.json({ cal: getCal(), rev: getCalRev(), defaults: SERVO_CAL_DEFAULTS });
}

/**
 * POST /api/servo/cal  { t120: 715, ... }   → save (partial is fine)
 * POST /api/servo/cal  { reset: true }
 */
export async function POST(req: Request) {
  if (!authorized(req)) {
    return NextResponse.json({ ok: false, error: "bad device token" }, { status: 401 });
  }

  let body: Record<string, unknown>;
  try {
    body = (await req.json()) as Record<string, unknown>;
  } catch {
    return NextResponse.json({ ok: false, error: "body must be JSON" }, { status: 400 });
  }

  if (body.reset === true) {
    return NextResponse.json({ ok: true, cal: resetCal(), rev: getCalRev() });
  }

  const pick = (k: string) => (typeof body[k] === "number" ? (body[k] as number) : undefined);
  const { cal, error } = setCal({
    stopPulse: pick("stopPulse"),
    speedOffset: pick("speedOffset"),
    t120: pick("t120"),
    t240: pick("t240"),
    t360: pick("t360"),
    tRev: pick("tRev"),
  });

  if (error) return NextResponse.json({ ok: false, error, cal }, { status: 400 });
  return NextResponse.json({ ok: true, cal, rev: getCalRev() });
}
