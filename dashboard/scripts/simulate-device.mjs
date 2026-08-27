/**
 * Fake ESP32, for demoing the dashboard without hardware on the bench.
 *
 *   node scripts/simulate-device.mjs
 *   node scripts/simulate-device.mjs --url http://localhost:3000 --interval 2000
 *   node scripts/simulate-device.mjs --backfill 240   # seed 240 past readings first
 *
 * Walks all four probes around plausible values and occasionally pushes one
 * into the warning band so the status colours are exercised. It also polls
 * /api/relays exactly the way the firmware does, so the pump buttons can be
 * driven end to end with nothing plugged in.
 *
 * --fault lets you rehearse a broken sensor without unplugging anything:
 *   --fault temp      DS18B20 unplugged (temp_c null)
 *   --fault ph        pH AOUT unplugged (voltage floats near 0)
 *   --fault tds       TDS probe lifted out of the water (~0 V)
 *   --fault turb      turbidity AOUT unplugged (voltage collapses to ~0)
 *   --fault divider   10k/15k divider missing (ADS1115 saturates high)
 *   --fault ads       the whole ADS1115 is gone: pH, TDS and turbidity all null
 *   --fault all       nothing answering
 *   --fault cycle     rotates through them, changing every 15 samples
 */

const args = Object.fromEntries(
  process.argv.slice(2).flatMap((a, i, arr) =>
    a.startsWith("--") ? [[a.slice(2), arr[i + 1]?.startsWith("--") ? true : arr[i + 1]]] : [],
  ),
);

// Accept either a bare host or a full ingest URL, since both get pasted in.
const RAW = args.url || "http://localhost:3000";
const HOST = RAW.replace(/\/api\/ingest\/?$/, "").replace(/\/$/, "");
const INGEST = `${HOST}/api/ingest`;
const RELAYS = `${HOST}/api/relays?fmt=text`;

const INTERVAL = Number(args.interval || 5000);
const TOKEN = args.token || process.env.DEVICE_TOKEN || "";
const DEVICE_ID = args.device || "ESP32-SIM01";
const FAULT = args.fault === true ? "cycle" : args.fault || "none";

const CYCLE = ["none", "temp", "ph", "tds", "turb", "ads", "all"];
function faultNow(tick) {
  return FAULT === "cycle" ? CYCLE[Math.floor(tick / 15) % CYCLE.length] : FAULT;
}

// Voltages sit in the realistic band for uncalibrated probes: turbidity ~4.2 V
// in clean water, pH ~2.5 V at neutral, TDS a few hundred mV in tap water.
let temp = 24.5;
let phV = 2.498;
let tdsV = 0.42;
let turbV = 4.198;
let tick = 0;

// What the node believes its own relays are doing. The dashboard's buttons
// move these, exactly as they would move the real GPIOs.
const relays = [false, false];

function step() {
  tick++;
  temp += (24.5 - temp) * 0.08 + (Math.random() - 0.5) * 0.35;
  phV += (2.498 - phV) * 0.1 + (Math.random() - 0.5) * 0.012;

  // Running a pump stirs sediment and pulls in fresh water: turbidity climbs
  // while it runs, TDS drifts. Makes the buttons visibly do something.
  const pumping = relays.some(Boolean);
  tdsV += ((pumping ? 0.36 : 0.42) - tdsV) * 0.1 + (Math.random() - 0.5) * 0.01;

  const event = tick % 40 > 33;
  const target = pumping ? 4.05 : event ? 4.15 : 4.198;
  turbV += (target - turbV) * 0.25 + (Math.random() - 0.5) * 0.004;

  const fault = faultNow(tick);
  const dead = (k) => fault === k || fault === "all" || (fault === "ads" && k !== "temp");
  const floating = () => 0.004 + Math.random() * 0.01;

  return {
    device_id: DEVICE_ID,
    // What the hardware actually does when it fails: the DS18B20 reports
    // DEVICE_DISCONNECTED_C, and a floating analog input drifts near ground.
    temp_c: dead("temp") ? -127 : Math.round(temp * 100) / 100,
    ph_v: fault === "ads" ? null : dead("ph") ? round(floating(), 4) : round(phV, 4),
    tds_v: fault === "ads" ? null : dead("tds") ? round(floating(), 4) : round(tdsV, 4),
    turbidity_v:
      fault === "ads"
        ? null
        : dead("turb")
          ? round(floating(), 3)
          : fault === "divider"
            ? 6.82 // ADS1115 railed at 4.096 V ÷ 0.6 divider ratio
            : round(turbV, 3),
    relay1: relays[0] ? 1 : 0,
    relay2: relays[1] ? 1 : 0,
    rssi: -50 - Math.round(Math.random() * 20),
    uptime_ms: tick * INTERVAL,
  };
}

const round = (v, d) => Math.round(v * 10 ** d) / 10 ** d;
const auth = TOKEN ? { "x-device-token": TOKEN } : {};

async function post(body) {
  const res = await fetch(INGEST, {
    method: "POST",
    headers: { "content-type": "application/json", ...auth },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
  return res.json();
}

/** The firmware's relay poll: one character per relay, '1' or '0'. */
async function pollRelays() {
  try {
    const res = await fetch(RELAYS, { headers: auth });
    if (!res.ok) return;
    const body = (await res.text()).trim();
    if (body.length !== relays.length) return;
    for (let i = 0; i < relays.length; i++) {
      const want = body[i] === "1";
      if (relays[i] !== want) console.log(`  pump ${i + 1} -> ${want ? "ON" : "OFF"}`);
      relays[i] = want;
    }
  } catch {
    /* the real node just retries next second too */
  }
}

const backfill = Number(args.backfill || 0);
for (let i = 0; i < backfill; i++) {
  await post(step());
}
if (backfill) console.log(`seeded ${backfill} readings`);

console.log(`simulating ${DEVICE_ID} -> ${INGEST} every ${INTERVAL}ms, fault=${FAULT}`);
console.log(`polling ${RELAYS} every 1s (ctrl-c to stop)`);
setInterval(pollRelays, 1000);

while (true) {
  const body = step();
  try {
    const out = await post(body);
    const f = (ok, text) => (ok === "ok" ? text : "NOT DETECTED");
    console.log(
      [
        f(out.sensors.temperature, `${body.temp_c} °C`),
        f(out.sensors.ph, `pH ${out.ph}`),
        f(out.sensors.tds, `${out.tds} ppm`),
        f(out.sensors.turbidity, `${out.ntu} NTU`),
        `pumps ${relays.map((r) => (r ? "1" : "0")).join("")}`,
      ].join(" · "),
    );
  } catch (err) {
    console.error("post failed:", err.message);
  }
  await new Promise((r) => setTimeout(r, INTERVAL));
}
