import { NextResponse } from "next/server";
import { DEFAULT_DEVICE_ID, isTurbidityPlausible, voltsToNtu } from "@/lib/config";
import { addReading } from "@/lib/store";
import type { IngestPayload, Reading } from "@/lib/types";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * The ESP32 posts here. The dashboard itself is unauthenticated for now, but
 * this endpoint is writable by anyone who finds the URL — set DEVICE_TOKEN in
 * the environment and the matching token in the firmware to lock it down.
 */
function authorized(req: Request): boolean {
  const expected = process.env.DEVICE_TOKEN;
  if (!expected) return true;
  const header = req.headers.get("x-device-token");
  const query = new URL(req.url).searchParams.get("token");
  return header === expected || query === expected;
}

const num = (v: unknown): number | null =>
  typeof v === "number" && Number.isFinite(v) ? v : null;

export async function POST(req: Request) {
  if (!authorized(req)) {
    return NextResponse.json({ ok: false, error: "bad device token" }, { status: 401 });
  }

  let body: IngestPayload;
  try {
    body = (await req.json()) as IngestPayload;
  } catch {
    return NextResponse.json({ ok: false, error: "body must be JSON" }, { status: 400 });
  }

  const turbidityV = num(body.turbidity_v);

  // A payload carrying neither sensor is a malformed request, not a reading.
  if (turbidityV === null && body.temp_c === undefined) {
    return NextResponse.json(
      { ok: false, error: "send at least one of temp_c or turbidity_v" },
      { status: 400 },
    );
  }

  // The firmware sends -127 (DEVICE_DISCONNECTED_C) when no probe answers.
  const rawTemp = num(body.temp_c);
  const tempC = rawTemp === null || rawTemp <= -100 ? null : rawTemp;

  // Keep the measured voltage either way — it is the diagnostic when the probe
  // is miswired — but refuse to turn an impossible voltage into an NTU figure.
  const plausible = turbidityV !== null && isTurbidityPlausible(turbidityV);

  const reading: Reading = {
    t: Date.now(),
    deviceId: (body.device_id || DEFAULT_DEVICE_ID).slice(0, 40),
    tempC,
    turbidityV: turbidityV === null ? null : Math.round(turbidityV * 1000) / 1000,
    turbidityNtu: plausible ? voltsToNtu(turbidityV) : null,
    raw: num(body.raw) ?? undefined,
    rssi: num(body.rssi) ?? undefined,
    uptimeMs: num(body.uptime_ms) ?? undefined,
    resetReason:
      typeof body.reset_reason === "string" ? body.reset_reason.slice(0, 32) : undefined,
    heap: num(body.heap) ?? undefined,
  };

  addReading(reading);
  return NextResponse.json({
    ok: true,
    t: reading.t,
    ntu: reading.turbidityNtu,
    sensors: {
      temperature: reading.tempC === null ? "not detected" : "ok",
      turbidity: reading.turbidityNtu === null ? "not detected" : "ok",
    },
  });
}

export async function GET() {
  return NextResponse.json({
    ok: true,
    hint:
      "POST JSON here: { device_id, temp_c, turbidity_v, raw, rssi, uptime_ms }. " +
      "Send temp_c as null (or -127) and turbidity_v as null when a probe is not answering.",
    tokenRequired: Boolean(process.env.DEVICE_TOKEN),
  });
}
