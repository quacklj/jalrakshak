import { getReadings } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** GET /api/export?window=86400000 → readings.csv */
export async function GET(req: Request) {
  const windowMs = Number(new URL(req.url).searchParams.get("window") || 0);
  let rows = getReadings();
  if (windowMs > 0) {
    const cutoff = Date.now() - windowMs;
    rows = rows.filter((r) => r.t >= cutoff);
  }

  // Faults are exported explicitly: a blank value plus a status column, so a
  // reader can't mistake "sensor was down" for "sensor read zero".
  const head =
    "timestamp_iso,epoch_ms,device_id,temperature_c,temperature_status," +
    "turbidity_v,turbidity_ntu,turbidity_status,rssi_dbm";
  const body = rows
    .map((r) =>
      [
        new Date(r.t).toISOString(),
        r.t,
        r.deviceId,
        r.tempC ?? "",
        r.tempC === null ? "not_detected" : "ok",
        r.turbidityV ?? "",
        r.turbidityNtu ?? "",
        r.turbidityNtu === null ? "not_detected" : "ok",
        r.rssi ?? "",
      ].join(","),
    )
    .join("\n");

  const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-");
  return new Response(`${head}\n${body}\n`, {
    headers: {
      "content-type": "text/csv; charset=utf-8",
      "content-disposition": `attachment; filename="jalraksha-readings-${stamp}.csv"`,
    },
  });
}
