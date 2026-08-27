import { NextResponse } from "next/server";
import { PUMP_MAX_RUN_MS, PUMP_POLL_MS, RELAYS } from "@/lib/config";
import { allOff, desiredStates, isRelayId, relayViews, setRelay } from "@/lib/relays";

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
 * GET /api/relays            → full state for the dashboard
 * GET /api/relays?fmt=text   → "10" — what the ESP32 polls
 *
 * The text form exists because the firmware has no JSON parser and does not
 * need one: one character per relay in RELAYS order, '1' on and '0' off. It
 * also keeps the poll small enough to run every second without noticeably
 * loading the node or the network.
 */
export async function GET(req: Request) {
  const fmt = new URL(req.url).searchParams.get("fmt");

  if (fmt === "text") {
    if (!authorized(req)) return new Response("!", { status: 401 });
    return new Response(desiredStates().map((on) => (on ? "1" : "0")).join(""), {
      headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
    });
  }

  return NextResponse.json({
    now: Date.now(),
    maxRunMs: PUMP_MAX_RUN_MS,
    pollMs: PUMP_POLL_MS,
    relays: relayViews(),
  });
}

/**
 * POST /api/relays  { id: "pump1", on: true }
 * POST /api/relays  { allOff: true }
 */
export async function POST(req: Request) {
  if (!authorized(req)) {
    return NextResponse.json({ ok: false, error: "bad device token" }, { status: 401 });
  }

  let body: { id?: unknown; on?: unknown; allOff?: unknown };
  try {
    body = (await req.json()) as typeof body;
  } catch {
    return NextResponse.json({ ok: false, error: "body must be JSON" }, { status: 400 });
  }

  if (body.allOff === true) {
    allOff();
    return NextResponse.json({ ok: true, now: Date.now(), relays: relayViews() });
  }

  if (!isRelayId(body.id)) {
    return NextResponse.json(
      { ok: false, error: `id must be one of ${RELAYS.map((r) => r.id).join(", ")}` },
      { status: 400 },
    );
  }
  if (typeof body.on !== "boolean") {
    return NextResponse.json({ ok: false, error: "on must be true or false" }, { status: 400 });
  }

  setRelay(body.id, body.on);
  return NextResponse.json({ ok: true, now: Date.now(), relays: relayViews() });
}
