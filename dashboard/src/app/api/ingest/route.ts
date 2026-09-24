import { NextResponse } from "next/server";
import {
  DEFAULT_DEVICE_ID,
  RELAYS,
  distanceToLevel,
  servoNormDeg,
  isTurbidityPlausible,
  voltsToNtu,
  voltsToPh,
  voltsToTds,
} from "@/lib/config";
import { logReading } from "@/lib/log";
import { desiredStates } from "@/lib/relays";
import { addReading } from "@/lib/store";
import type { IngestPayload, Reading, RelayId } from "@/lib/types";

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

const round = (v: number | null, places: number): number | null =>
  v === null ? null : Math.round(v * 10 ** places) / 10 ** places;

/** The firmware sends 0/1; a JSON client might send a real boolean. */
const bool = (v: unknown): boolean | undefined => {
  if (typeof v === "boolean") return v;
  if (typeof v === "number") return v !== 0;
  return undefined;
};

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

  const phV = num(body.ph_v);
  const tdsV = num(body.tds_v);
  const turbidityV = num(body.turbidity_v);
  const distanceCm = num(body.distance_cm);
  const servoDegIn = num(body.servo_deg);

  // A payload carrying no sensor at all is a malformed request, not a reading.
  if (
    phV === null &&
    tdsV === null &&
    turbidityV === null &&
    distanceCm === null &&
    body.temp_c === undefined
  ) {
    return NextResponse.json(
      {
        ok: false,
        error: "send at least one of temp_c, ph_v, tds_v, turbidity_v or distance_cm",
      },
      { status: 400 },
    );
  }

  // The firmware sends -127 (DEVICE_DISCONNECTED_C) when no probe answers.
  const rawTemp = num(body.temp_c);
  const tempC = rawTemp === null || rawTemp <= -100 ? null : rawTemp;

  // Keep every measured voltage either way — it is the diagnostic when a probe
  // is miswired — but refuse to turn an impossible voltage into a water figure.
  const plausibleTurb = turbidityV !== null && isTurbidityPlausible(turbidityV);

  const relays: Partial<Record<RelayId, boolean>> = {};
  const reported = [bool(body.relay1), bool(body.relay2)];
  RELAYS.forEach((spec, i) => {
    if (reported[i] !== undefined) relays[spec.id] = reported[i];
  });

  const reading: Reading = {
    t: Date.now(),
    deviceId: (body.device_id || DEFAULT_DEVICE_ID).slice(0, 40),
    tempC,
    phV: round(phV, 4),
    ph: phV === null ? null : voltsToPh(phV),
    tdsV: round(tdsV, 4),
    // TDS is conductivity, and conductivity moves with temperature — so this
    // one number depends on the DS18B20 as well as its own probe.
    tds: tdsV === null ? null : voltsToTds(tdsV, tempC),
    turbidityV: round(turbidityV, 3),
    turbidityNtu: plausibleTurb ? voltsToNtu(turbidityV) : null,
    // Same split as the probes: keep the measured centimetres whatever they
    // say, because an implausible distance is the diagnostic for a badly
    // mounted sensor — but only turn a distance the sensor can actually
    // resolve into a tank level.
    distanceCm: round(distanceCm, 1),
    levelPct: distanceCm === null ? null : distanceToLevel(distanceCm),
    raw: num(body.raw) ?? undefined,
    rssi: num(body.rssi) ?? undefined,
    uptimeMs: num(body.uptime_ms) ?? undefined,
    resetReason:
      typeof body.reset_reason === "string" ? body.reset_reason.slice(0, 32) : undefined,
    heap: num(body.heap) ?? undefined,
    relays: Object.keys(relays).length ? relays : undefined,

    // Everything the node believes about the servo. Passed through untouched:
    // the node ran the motor and timed it, so its belief is the only one with
    // any grounding. The dashboard's own idea of the angle is a prediction.
    servoDeg: servoDegIn === null ? null : servoNormDeg(servoDegIn),
    servoMoving: bool(body.servo_moving),
    servoMovesSinceZero: num(body.servo_moves) ?? undefined,
    servoUncertain: bool(body.servo_uncertain),
    servoAckSeq: num(body.servo_ack) ?? undefined,
    servoSpinMs: num(body.servo_spin_ms) ?? undefined,
  };

  addReading(reading);
  logReading(reading);

  // The relay command rides back on the POST response as well as living on
  // /api/relays. A node whose fast poll is failing still converges here, one
  // upload interval behind, instead of holding a stale position forever.
  return NextResponse.json({
    ok: true,
    t: reading.t,
    ntu: reading.turbidityNtu,
    ph: reading.ph,
    tds: reading.tds,
    level: reading.levelPct,
    relays: desiredStates().map((on) => (on ? 1 : 0)),
    sensors: {
      temperature: reading.tempC === null ? "not detected" : "ok",
      ph: reading.ph === null ? "not detected" : "ok",
      tds: reading.tds === null ? "not detected" : "ok",
      turbidity: reading.turbidityNtu === null ? "not detected" : "ok",
      level: reading.levelPct === null ? "not detected" : "ok",
    },
    servo: reading.servoDeg,
  });
}

export async function GET() {
  return NextResponse.json({
    ok: true,
    hint:
      "POST JSON here: { device_id, temp_c, ph_v, tds_v, turbidity_v, distance_cm, raw, " +
      "rssi, uptime_ms, relay1, relay2, servo_deg, servo_moving, servo_moves, " +
      "servo_uncertain, servo_ack }. distance_cm is the ultrasonic's distance DOWN TO " +
      "the water surface; the tank level is derived from it here. Send a probe's field as " +
      "null when it is not answering — never as 0.",
    tokenRequired: Boolean(process.env.DEVICE_TOKEN),
  });
}
