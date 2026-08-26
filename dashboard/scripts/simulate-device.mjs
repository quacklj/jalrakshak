/**
 * Fake ESP32, for demoing the dashboard without hardware on the bench.
 *
 *   node scripts/simulate-device.mjs
 *   node scripts/simulate-device.mjs --url http://localhost:3000/api/ingest --interval 2000
 *   node scripts/simulate-device.mjs --backfill 240   # seed 240 past readings first
 *
 * Walks temperature and turbidity around plausible values and occasionally
 * pushes turbidity into the warning band so the status colours are exercised.
 *
 * --fault lets you rehearse a broken sensor without unplugging anything:
 *   --fault temp      DS18B20 unplugged (temp_c null)
 *   --fault turb      turbidity AOUT unplugged (voltage collapses to ~0)
 *   --fault divider   10k/15k divider missing (ADS1115 saturates high)
 *   --fault both      neither probe answering
 *   --fault cycle     rotates none → temp → turb → both every 15 samples
 */

const args = Object.fromEntries(
  process.argv.slice(2).flatMap((a, i, arr) =>
    a.startsWith("--") ? [[a.slice(2), arr[i + 1]?.startsWith("--") ? true : arr[i + 1]]] : [],
  ),
);

const URL_ = args.url || "http://localhost:3000/api/ingest";
const INTERVAL = Number(args.interval || 5000);
const TOKEN = args.token || process.env.DEVICE_TOKEN || "";
const DEVICE_ID = args.device || "ESP32-SIM01";
const FAULT = args.fault === true ? "cycle" : args.fault || "none";

const CYCLE = ["none", "temp", "turb", "both"];
function faultNow(tick) {
  return FAULT === "cycle" ? CYCLE[Math.floor(tick / 15) % CYCLE.length] : FAULT;
}

// Voltages sit in the realistic band for an uncalibrated probe: ~4.2 V in clean
// water, dipping a little as sediment passes. A few tens of mV is already
// hundreds of NTU, which is exactly why the probe needs calibrating.
let temp = 24.5;
let volts = 4.198;
let tick = 0;

function step() {
  tick++;
  temp += (24.5 - temp) * 0.08 + (Math.random() - 0.5) * 0.35;

  // Every ~40 samples, simulate a slug of cloudy water passing the probe.
  const event = tick % 40 > 33;
  const target = event ? 4.15 : 4.198;
  volts += (target - volts) * 0.25 + (Math.random() - 0.5) * 0.004;

  const fault = faultNow(tick);
  const tempDead = fault === "temp" || fault === "both";
  const turbDead = fault === "turb" || fault === "both";

  // What the hardware actually does when it fails: the DS18B20 reports
  // DEVICE_DISCONNECTED_C, and a floating AOUT gets pulled to ground by the 15k.
  const outVolts = turbDead
    ? 0.004 + Math.random() * 0.01
    : fault === "divider"
      ? 6.82 // ADS1115 railed at 4.096 V ÷ 0.6 divider ratio
      : volts;

  return {
    device_id: DEVICE_ID,
    temp_c: tempDead ? -127 : Math.round(temp * 100) / 100,
    turbidity_v: Math.round(outVolts * 1000) / 1000,
    rssi: -50 - Math.round(Math.random() * 20),
    uptime_ms: tick * INTERVAL,
  };
}

async function post(body) {
  const res = await fetch(URL_, {
    method: "POST",
    headers: { "content-type": "application/json", ...(TOKEN ? { "x-device-token": TOKEN } : {}) },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
  return res.json();
}

const backfill = Number(args.backfill || 0);
for (let i = 0; i < backfill; i++) {
  await post(step());
}
if (backfill) console.log(`seeded ${backfill} readings`);

console.log(
  `simulating ${DEVICE_ID} -> ${URL_} every ${INTERVAL}ms, fault=${FAULT} (ctrl-c to stop)`,
);
while (true) {
  const body = step();
  try {
    const out = await post(body);
    const t = out.sensors.temperature === "ok" ? `${body.temp_c} °C` : "temp NOT DETECTED";
    const u = out.sensors.turbidity === "ok" ? `${out.ntu} NTU` : "turbidity NOT DETECTED";
    console.log(`${t} · ${body.turbidity_v} V -> ${u}`);
  } catch (err) {
    console.error("post failed:", err.message);
  }
  await new Promise((r) => setTimeout(r, INTERVAL));
}
