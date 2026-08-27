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
    "timestamp_iso,epoch_ms,device_id," +
    "temperature_c,temperature_status," +
    "ph_v,ph,ph_status," +
    "tds_v,tds_ppm,tds_status," +
    "turbidity_v,turbidity_ntu,turbidity_status," +
    "pump1,pump2,rssi_dbm";
  const status = (v: number | null) => (v === null ? "not_detected" : "ok");
  const relay = (v: boolean | undefined) => (v === undefined ? "" : v ? "on" : "off");
  const body = rows
    .map((r) =>
      [
        new Date(r.t).toISOString(),
        r.t,
        r.deviceId,
        r.tempC ?? "",
        status(r.tempC),
        r.phV ?? "",
        r.ph ?? "",
        status(r.ph),
        r.tdsV ?? "",
        r.tds ?? "",
        status(r.tds),
        r.turbidityV ?? "",
        r.turbidityNtu ?? "",
        status(r.turbidityNtu),
        relay(r.relays?.pump1),
        relay(r.relays?.pump2),
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
