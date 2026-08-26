import { NextResponse } from "next/server";
import { downsample, getReadings, readingCount } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * GET /api/readings?window=3600000&points=400
 * GET /api/readings?since=<epoch ms>   ← incremental catch-up for the client
 */
export async function GET(req: Request) {
  const q = new URL(req.url).searchParams;
  const since = q.get("since");
  const windowMs = Number(q.get("window") || 0);
  const points = Math.min(4000, Math.max(2, Number(q.get("points") || 600)));

  if (since) {
    const rows = getReadings({ since: Number(since) });
    return NextResponse.json({ readings: rows, total: readingCount(), now: Date.now() });
  }

  let rows = getReadings();
  if (windowMs > 0) {
    const cutoff = Date.now() - windowMs;
    rows = rows.filter((r) => r.t >= cutoff);
  }
  return NextResponse.json({
    readings: downsample(rows, points),
    total: readingCount(),
    windowTotal: rows.length,
    now: Date.now(),
  });
}
