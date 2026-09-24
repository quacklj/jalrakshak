/**
 * Fake ESP32, for demoing the dashboard without hardware on the bench.
 *
 *   node scripts/simulate-device.mjs
 *   node scripts/simulate-device.mjs --url http://localhost:3000 --interval 2000
 *   node scripts/simulate-device.mjs --backfill 240   # seed 240 past readings first
 *
 * Walks all five sensors around plausible values and occasionally pushes one
 * into the warning band so the status colours are exercised. It also polls
 * /api/relays exactly the way the firmware does, so the pump buttons can be
 * driven end to end with nothing plugged in — and pump 1 fills the simulated
 * tank while pump 2 drains it, so the level chart visibly answers the buttons.
 *
 * --fault lets you rehearse a broken sensor without unplugging anything:
 *   --fault temp      DS18B20 unplugged (temp_c null)
 *   --fault ph        pH AOUT unplugged (voltage floats near 0)
 *   --fault tds       TDS probe lifted out of the water (~0 V)
 *   --fault turb      turbidity AOUT unplugged (voltage collapses to ~0)
 *   --fault divider   10k/15k divider missing (ADS1115 saturates high)
 *   --fault ads       the whole ADS1115 is gone: pH, TDS and turbidity all null
 *   --fault level     ultrasonic returns no echo at all (distance_cm null)
 *   --fault blind     water risen into the sensor's ~20cm blind zone
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
const SERVO = `${HOST}/api/servo?fmt=text`;

const INTERVAL = Number(args.interval || 5000);
const TOKEN = args.token || process.env.DEVICE_TOKEN || "";
const DEVICE_ID = args.device || "ESP32-SIM01";
const FAULT = args.fault === true ? "cycle" : args.fault || "none";

const CYCLE = ["none", "temp", "ph", "tds", "turb", "level", "blind", "ads", "all"];
function faultNow(tick) {
  return FAULT === "cycle" ? CYCLE[Math.floor(tick / 15) % CYCLE.length] : FAULT;
}

// Voltages sit in the realistic band for uncalibrated probes: turbidity ~4.2 V
// in clean water, pH ~2.5 V at neutral, TDS a few hundred mV in tap water.
let temp = 24.5;
let phV = 2.498;
let tdsV = 0.42;
let turbV = 4.198;
// Distance from the sensor down to the water, cm. Starts mid-tank against the
// 30cm-full / 120cm-empty geometry in lib/config.ts.
let distCm = 62.0;
let tick = 0;

// What the node believes its own relays are doing. The dashboard's buttons
// move these, exactly as they would move the real GPIOs.
const relays = [false, false];

/* The servo, dead reckoned exactly the way the firmware does it — including
   starting out uncertain, because a node that just booted genuinely does not
   know where the horn is. */
const FWD_MS = [0, 715, 1460, 2300];
const REV_MS = [0, 647, 1320, 2080];
const ANCHORS = [0, 120, 240, 360];
const servo = { deg: 0, moving: false, uncertain: true, moves: 0, seq: 0, spinMs: 0 };
let servoMove = null; // { from, sweep, dir, ms, startedAt }
let servoSpin = null; // free spin: { startedAt }
let servoRaw = null;  // fixed-time bench run: { startedAt, ms }
let stepTest = null;  // { leg, pauseMs, pausingSince }

const normDeg = (d) => ((d % 360) + 360) % 360;

function msForSweep(sweep, dir) {
  const table = dir > 0 ? FWD_MS : REV_MS;
  const s = Math.max(0, Math.min(360, sweep));
  for (let i = 1; i < ANCHORS.length; i++) {
    if (s <= ANCHORS[i]) {
      const f = (s - ANCHORS[i - 1]) / (ANCHORS[i] - ANCHORS[i - 1]);
      return Math.round(table[i - 1] + f * (table[i] - table[i - 1]));
    }
  }
  return table[table.length - 1];
}

function startMove(sweep, dir) {
  if (sweep <= 0.05) return;
  const ms = msForSweep(sweep, dir);
  if (ms === 0) return;
  servoMove = { from: servo.deg, sweep, dir, ms, startedAt: Date.now() };
  servo.moving = true;
  console.log(`  servo ${servo.deg.toFixed(0)}° ${dir > 0 ? "fwd" : "rev"} ${sweep.toFixed(0)}° in ${ms}ms`);
}

function haltServo(interrupted) {
  if (servoMove) {
    const f = Math.min(1, (Date.now() - servoMove.startedAt) / servoMove.ms);
    servo.deg = normDeg(servoMove.from + servoMove.dir * servoMove.sweep * f);
    servo.moves++;
    if (interrupted) servo.uncertain = true;
  }
  servoMove = null;
  servo.moving = false;
}

function serviceServo() {
  if (servoRaw && Date.now() - servoRaw.startedAt >= servoRaw.ms) {
    servoRaw = null;
    console.log("  bench run finished");
  }

  if (servoMove && Date.now() - servoMove.startedAt >= servoMove.ms) {
    servo.deg = normDeg(servoMove.from + servoMove.dir * servoMove.sweep);
    servo.moves++;
    servoMove = null;
    servo.moving = false;
    console.log(`  servo arrived ~${servo.deg.toFixed(1)}° (${servo.moves} moves since zero)`);
  }

  // Step test advances only once the previous leg has landed.
  if (stepTest && !servo.moving && stepTest.pausingSince === null) {
    stepTest.leg++;
    if (stepTest.leg >= 3) {
      stepTest = null;
      servo.deg = 0; // 360 is the same spot as the 0 mark
      console.log("  step test done - back on the 0 mark");
    } else {
      stepTest.pausingSince = Date.now();
    }
  }
  if (
    stepTest &&
    stepTest.pausingSince !== null &&
    Date.now() - stepTest.pausingSince >= stepTest.pauseMs
  ) {
    stepTest.pausingSince = null;
    startMove(120, 1);
  }
}

/** The firmware's servo poll: "<seq>,<letter>,<arg>", acted on only when seq changes. */
async function pollServo() {
  serviceServo();
  try {
    const res = await fetch(SERVO, { headers: auth });
    if (!res.ok) return;
    const m = (await res.text()).trim().match(/^(\d+),([GSZTRPAQ]),(-?\d+)$/);
    if (!m) return;
    const [, seqRaw, cmd, argRaw] = m;
    const seq = Number(seqRaw);
    const arg = Number(argRaw);
    if (seq === servo.seq) return;
    servo.seq = seq;

    if (cmd === "G") {
      haltServo(true);
      const fwd = normDeg(arg - servo.deg);
      const rev = normDeg(servo.deg - arg);
      if (fwd < 0.05) return;
      if (msForSweep(rev, -1) < msForSweep(fwd, 1)) startMove(rev, -1);
      else startMove(fwd, 1);
    } else if (cmd === "S") {
      if (servoSpin) {
        // The node times its own spin; the browser only reads the answer back.
        servo.spinMs = Date.now() - servoSpin.startedAt;
        console.log(`  free spin measured ${servo.spinMs} ms`);
        servoSpin = null;
      }
      servoRaw = null;
      stepTest = null;
      haltServo(true);
      console.log("  servo STOPPED");
    } else if (cmd === "R") {
      haltServo(true);
      servoRaw = { startedAt: Date.now(), ms: Math.abs(arg) };
      servo.uncertain = true;
      console.log(`  bench run ${arg < 0 ? "reverse" : "forward"} ${Math.abs(arg)}ms`);
    } else if (cmd === "P") {
      haltServo(true);
      servoSpin = { startedAt: Date.now() };
      servo.uncertain = true;
      console.log("  free spinning");
    } else if (cmd === "A") {
      haltServo(true);
      servo.deg = 0;
      servo.uncertain = false;
      startMove(normDeg(arg) === 0 ? 360 : normDeg(arg), 1);
    } else if (cmd === "Q") {
      haltServo(true);
      servo.deg = 0;
      servo.uncertain = false;
      stepTest = { leg: 0, pauseMs: Math.max(0, Math.min(30000, arg)), pausingSince: null };
      startMove(120, 1);
    } else if (cmd === "Z") {
      haltServo(true);
      servo.deg = 0;
      servo.moves = 0;
      servo.uncertain = false;
      console.log("  servo re-zeroed");
    } else if (cmd === "T") {
      haltServo(true);
      startMove(360, arg < 0 ? -1 : 1);
    }
  } catch (err) {
    // A fetch failure is expected and the real node just retries next second.
    // Anything else is a bug in here, and silence is how it stays one.
    if (!(err instanceof TypeError && String(err.message).includes("fetch"))) {
      console.error("pollServo:", err.message);
    }
  }
}

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

  // The two relays are unlabelled on the real board; the simulator picks a
  // direction for each so the level chart responds to the dashboard buttons.
  // Pump 1 fills (water rises, distance falls), pump 2 drains.
  if (relays[0]) distCm -= 1.8;
  if (relays[1]) distCm += 1.8;
  if (!relays[0] && !relays[1]) distCm += 0.05; // slow draw-off by the village
  // Ultrasonic readings are noisy even in still water — a few mm of jitter.
  distCm = clamp(distCm + (Math.random() - 0.5) * 0.4, 24, 130);

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
    distance_cm:
      dead("level") || fault === "all"
        ? null
        : fault === "blind"
          ? round(8 + Math.random() * 3, 1) // risen into the blind zone
          : round(distCm, 1),
    servo_deg: Number(
      (servoMove
        ? normDeg(
            servoMove.from +
              servoMove.dir *
                servoMove.sweep *
                Math.min(1, (Date.now() - servoMove.startedAt) / servoMove.ms),
          )
        : servo.deg
      ).toFixed(1),
    ),
    servo_moving: servo.moving ? 1 : 0,
    servo_moves: servo.moves,
    servo_uncertain: servo.uncertain ? 1 : 0,
    servo_ack: servo.seq,
    servo_spin_ms: servo.spinMs,
    relay1: relays[0] ? 1 : 0,
    relay2: relays[1] ? 1 : 0,
    rssi: -50 - Math.round(Math.random() * 20),
    uptime_ms: tick * INTERVAL,
  };
}

const round = (v, d) => Math.round(v * 10 ** d) / 10 ** d;
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
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
setInterval(pollServo, 300);   // faster than the node, so short moves are visible

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
        f(out.sensors.level, `tank ${out.level}% (${body.distance_cm}cm)`),
        `pumps ${relays.map((r) => (r ? "1" : "0")).join("")}`,
        `servo ${servo.moving ? ">" : servo.uncertain ? "~" : ""}${body.servo_deg}°`,
      ].join(" · "),
    );
  } catch (err) {
    console.error("post failed:", err.message);
  }
  await new Promise((r) => setTimeout(r, INTERVAL));
}
