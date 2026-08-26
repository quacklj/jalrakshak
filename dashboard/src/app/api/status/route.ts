import { NextResponse } from "next/server";
import { DEFAULT_DEVICE_ID, SENSOR_ORDER } from "@/lib/config";
import { deviceStateFor, sensorHealth } from "@/lib/derive";
import { getReadings, latestReading, readingCount } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Small, cheap summary for the sidebar chrome. */
export async function GET() {
  const latest = latestReading();
  const now = Date.now();
  const lastMinute = getReadings({ since: now - 60_000 }).length;
  const state = deviceStateFor(latest, now);

  // Health needs a little history so it can report when each sensor last worked.
  const recent = getReadings({ limit: 200 });
  const sensors = Object.fromEntries(
    SENSOR_ORDER.map((key) => {
      const h = sensorHealth(recent, key, state);
      return [key, { ok: h.ok, code: h.code, label: h.label, lastGood: h.lastGood }];
    }),
  );

  return NextResponse.json({
    now,
    deviceId: latest?.deviceId ?? DEFAULT_DEVICE_ID,
    state,
    lastSeen: latest?.t ?? null,
    perMinute: lastMinute,
    total: readingCount(),
    sensors,
    sensorsOk: SENSOR_ORDER.filter((k) => sensors[k].ok).length,
  });
}
