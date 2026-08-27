# Jalraksha One

Live water-quality dashboard for a single ESP32-S3 field node reading **water temperature**
(DS18B20) plus **pH**, **TDS** and **turbidity** (analog probes via ADS1115). The node posts each
reading over the internet, the dashboard charts all four in real time, and two **pump relays** are
driven back the other way from buttons on the dashboard.

```
Jalraksha/
├── app/                          Android app (Kotlin + Jetpack Compose)
├── dashboard/                    Next.js app (the website)
└── firmware/jalraksha_node/      Arduino sketch for the ESP32-S3
```

Four sensors and two pumps. Flow, tank level and leakage are not wired up, so the UI does not
pretend they exist. pH and TDS are **uncalibrated** — the dashboard says so on every screen and
will show you the raw probe voltage instead of inventing a number.

---

## 1. Run the dashboard

```bash
cd dashboard
npm install
npm run dev          # http://localhost:3000
```

Pages:

| Page                 | What it shows                                                        |
| -------------------- | -------------------------------------------------------------------- |
| **Overview**         | All four latest values, composite risk, pump control, 30 min sparklines |
| **Live Monitoring**  | Full-size charts for all four, 5 min → 24 h, pump control, connectivity |
| **History & Export** | Every stored reading, band filter, CSV download                       |
| **Device**           | Node identity, uptime, pin map, thresholds, calibration, pump safety  |

No login — the dashboard is open to anyone who can reach the URL. Add auth before this holds
anything sensitive.

### Try it without hardware

```bash
npm run simulate -- --backfill 200
```

Fakes an ESP32 posting every 5 s, with occasional cloudy-water events so the status colours
actually change. To rehearse a broken probe without unplugging anything:

```bash
npm run simulate -- --fault turb      # turbidity AOUT unplugged
npm run simulate -- --fault ph        # pH AOUT unplugged, input floating
npm run simulate -- --fault tds       # TDS probe lifted out of the water
npm run simulate -- --fault temp      # DS18B20 off the 1-Wire bus
npm run simulate -- --fault ads       # the whole ADS1115 gone: three probes at once
npm run simulate -- --fault divider   # 10k/15k divider missing, ADS1115 saturating
npm run simulate -- --fault cycle     # rotates through all of them
```

The simulator also polls `/api/relays` the way the firmware does, so the pump buttons work end to
end with nothing plugged in — and turbidity climbs while a pump runs, so you can see it do something.

---

## When a sensor stops answering

A disconnected probe is never charted as a value. Each sensor is tracked separately and reported
as **not reading**, with the likely cause:

| What the device sends            | Dashboard shows | Diagnosis offered                                  |
| -------------------------------- | --------------- | -------------------------------------------------- |
| `temp_c: null` / `-127`          | Not detected    | DS18B20 off the bus — check GPIO4 and the 4.7k pull-up |
| `ph_v` below 0.08 V              | Not detected    | A0 unplugged and the ADS1115 input is floating      |
| `ph_v` above 3.4 V               | Out of range    | pH board on 5V, above the ADC's own 3.3V rail       |
| `tds_v` below 0.02 V             | Dry or unplugged| A TDS probe reads zero in air *and* when unplugged  |
| `turbidity_v` below 0.05 V       | Not detected    | AOUT unplugged, the 15k is pulling the divider to ground |
| `turbidity_v` above 5.0 V        | Out of range    | Divider missing, ADS1115 saturating                 |
| Nothing at all for 30 s / 3 min  | Degraded → Offline | Wi-Fi, power, or `SERVER_URL`                    |

That state propagates everywhere: a banner at the top of the page, a red border on the affected
card, per-sensor dots in the sidebar on every page, `n/d` in the history table, and a
a per-sensor `_status` column in the CSV so a spreadsheet can't mistake a dead
probe for a zero reading.

It also changes the verdict. The composite risk score leaves a dead sensor out rather than scoring
it as zero, and while any sensor is down the dashboard refuses to show a green "Safe" badge —
it says **Partial coverage** instead, because one working probe cannot clear the water.

---

## 2. Point the ESP32 at it

Copy `firmware/jalraksha_node/secrets.example.h` to `secrets.h` in the same folder and fill it in:

```cpp
#define JR_WIFI_SSID     "your-wifi-name"
#define JR_WIFI_PASSWORD "your-wifi-password"
#define JR_SERVER_HOST   "http://192.168.1.10:3000"
#define JR_DEVICE_TOKEN  ""
```

`secrets.h` is git-ignored. **This repository is public — put your Wi-Fi password there, not in
the sketch.** The sketch still compiles without it, using the placeholders at the top.

`JR_SERVER_HOST` must be reachable **from the ESP32**, so `localhost` will not work — use your
laptop's LAN IP while developing (`ipconfig getifaddr en0` on macOS), or the public URL once
deployed. It is the host only; the sketch appends `/api/ingest` and `/api/relays` itself.

Libraries required: **OneWire**, **DallasTemperature**, **Adafruit ADS1X15**. No JSON library is
needed — the relay poll returns plain text. Full wiring is in the file header.

The sketch samples every 2 s (still printing to Serial), uploads the average every 10 s so a
single noisy sample can't swing the dashboard, and polls the pump command every 1 s.

### Two things to check before powering up

1. **The pH board's supply.** The ADS1115 runs on 3.3 V and its inputs must not exceed that rail.
   A pH board powered from 5 V can output up to 5 V, which saturates the reading and stresses the
   ADC. Run it from 3.3 V, or divide its output the way turbidity's is divided.
2. **`RELAY_ACTIVE_LOW`** in the sketch. Most cheap relay boards energise on a LOW input; some are
   the opposite. Get it wrong and the pumps run when the dashboard says they are off. Test with
   the pump disconnected — the relay should click when you press Start, not before.

---

## 3. Get it onto the internet

The server keeps readings in memory in one Node process, so it needs a **long-lived server**, not
serverless functions. Anything that runs `npm run build && npm start` works: Render, Railway,
Fly.io, a small VPS, or a Raspberry Pi on the same network.

Quickest option for a demo — tunnel your laptop:

```bash
npx cloudflared tunnel --url http://localhost:3000
# or: npx ngrok http 3000
```

Put the URL it prints into `SERVER_URL` (with `/api/ingest` on the end) and the ESP32 can reach
you from anywhere.

> **On Vercel:** the dashboard deploys fine, but each serverless invocation gets its own memory,
> so readings will appear to come and go. Use a persistent host, or swap `src/lib/store.ts` for a
> database (Postgres, Supabase, Timescale) — the store exposes only `addReading`, `getReadings`,
> `latestReading` and `subscribe`, so it is a small change.

### Environment variables

| Variable               | Default | Meaning                                                             |
| ---------------------- | ------- | ------------------------------------------------------------------- |
| `DEVICE_TOKEN`         | unset   | If set, ingest requires a matching `x-device-token` header           |
| `JALRAKSHA_PERSIST`    | `1`     | `0` disables writing readings to disk                                |
| `JALRAKSHA_DATA_DIR`   | `./data`| Where `readings.ndjson` is kept                                      |

Set `DEVICE_TOKEN` on any public deployment, and put the same string into `DEVICE_TOKEN` in the
sketch — otherwise anyone who finds the URL can inject fake readings.

---

## 4. API

| Endpoint                       | Purpose                                             |
| ------------------------------ | --------------------------------------------------- |
| `POST /api/ingest`             | The ESP32 posts one reading                         |
| `GET /api/readings?window=…`   | History for a time window (downsampled)             |
| `GET /api/readings?since=…`    | Incremental catch-up                                |
| `GET /api/stream`              | Server-sent events, one per new reading             |
| `GET /api/status`              | Device state summary                                |
| `GET /api/export?window=…`     | CSV download                                        |
| `GET /api/relays`              | Pump state: commanded, confirmed, auto-off deadline |
| `GET /api/relays?fmt=text`     | `"10"` — one character per relay, what the node polls |
| `POST /api/relays`             | `{"id":"pump1","on":true}` or `{"allOff":true}`     |

Ingest payload:

```json
{
  "device_id": "ESP32-JR01",
  "temp_c": 26.94,
  "ph_v": 2.5108,
  "tds_v": 0.4193,
  "turbidity_v": 4.05,
  "raw": 12980,
  "relay1": 0,
  "relay2": 0,
  "rssi": -58,
  "uptime_ms": 412000
}
```

Any probe's field may be `null` (and `temp_c` may be `-127`) when it doesn't answer — the
dashboard shows a gap rather than a fake value. **Never send 0 for a dead probe**: 0 is a
measurement, and 0 ppm or 0 NTU reads as unusually clean water.

`ph_v` and `tds_v` are straight off ADS1115 A0 and A1. `turbidity_v` is A2 with the 10k/15k
divider already undone, exactly as the sketch prints it. `relay1`/`relay2` are what the node
believes its own GPIOs are doing — the response carries back what the dashboard wants them to be.

---

## 5. Calibrating the probes

All three analog probes output volts, not water units. `dashboard/src/lib/config.ts` holds every
constant, and the Device page renders whatever you set there.

```ts
export const PH_NEUTRAL_V = 2.5;        // volts in pH 7.00 buffer
export const PH_VOLTS_PER_UNIT = 0.19;  // volts per pH unit, falling with pH

export const TDS_K = 1.0;               // scale factor against a known-ppm solution

export const TURBIDITY_CLEAR_V = 4.2;   // volts in clear water  → 0 NTU
export const TURBIDITY_OPAQUE_V = 2.5;  // volts fully clouded   → TURBIDITY_MAX_NTU
export const TURBIDITY_MAX_NTU = 3000;
```

**pH** — dip in pH 7.00 buffer, read the volts on Live Monitoring, set `PH_NEUTRAL_V`. Repeat in
pH 4.00 buffer; the slope is the difference between the two voltages divided by three.
**TDS** — the published cubic, temperature-compensated from the DS18B20 (conductivity rises ~2%
per °C, so a dead temperature probe quietly degrades this one). Scale `TDS_K` against a solution
of known ppm.
**Turbidity** — put the probe in clean water and set `TURBIDITY_CLEAR_V` to what you read.

Until then, treat every derived figure as an estimate. The Live page's **Volts** switch shows the
raw probe output for all three — that is the number to read while adjusting these constants, and
the dashboard falls back to it automatically whenever a calibration goes off scale.

Bands follow **IS 10500:2012** drinking-water limits: pH 6.5–8.5, TDS 500 ppm acceptable and 2000
permissible, turbidity 5 NTU.

---

## 5b. Pump control

Two relays, on **GPIO 14** and **GPIO 18**, driven from buttons on the Overview and Live pages.

The node is behind NAT, so nothing can call *in* to it. Instead the firmware polls
`GET /api/relays?fmt=text` every second and gets back one character per relay. The reply is two
bytes, needs no JSON parser on the device, and anything that is not exactly two characters is
discarded — a captive-portal login page can never be misread as a command to start a motor.

A button shows **Starting** until the node confirms the new position in its next payload, and
**Running** only after that. Commanded and confirmed are different facts and the UI never conflates
them. The controls disable themselves whenever the node is not online, because a command you
cannot confirm is worse than no command.

**No float switch is wired, so nothing physical stops a running pump.** Four limits stand in:

| Limit | Where | What it stops |
| ----- | ----- | ------------- |
| 5 minute max run | server, on read | a forgotten button |
| 5 minute max run | firmware, own clock | a dead or unreachable server |
| 30 s comms failsafe | firmware | a pump latched on after the network drops |
| Pin driven off *before* `pinMode(OUTPUT)` | firmware | a motor kick on every reset |

Re-pressing Start does not restart the run clock, so a held or repeated command cannot extend a
pump past its limit. Wire a float switch in series with the pump before running this unattended.

---

## 6. The Android app

`app/` is the villager-facing side: seven screens built in Jetpack Compose from the
`Jalraksha Mobile.dc.html` design.

| # | Screen | What it does |
| - | ------ | ------------ |
| 01 | Sign in | Mobile number + password, or an OTP |
| 02 | Language | Eight languages; the choice applies instantly |
| 03 | Village | Binds the account to a village |
| 04 | Dashboard | The water score, today's parameters, pushed notices |
| 05 | Trends | 7D/30D/1Y windows, parameter movement, monthly safe days |
| 06 | Report | File a water complaint, and see past ones |
| 07 | Profile | Account, settings, alert toggle, sign out |

Screens 04–07 share one persistent bottom bar, held by
[MainScreen](app/src/main/java/com/example/jalraksha/ui/main/MainScreen.kt) rather than repeated
per screen — switching tabs keeps each screen's ViewModel alive.

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:installDebug         # to a connected device
```

**It runs with no backend.** Until `google-services.json` exists, `ServiceLocator` hands out
`FakeAuthRepository` and `FakeWaterRepository`, which serve the design's own numbers. Any 10-digit
number with a 6+ character password signs in, so all four screens are walkable on a bare emulator.

### Wiring up Firebase

1. Firebase console → add an Android app with package `com.example.jalraksha`.
2. Download `google-services.json` into `app/`.
3. Sync. The Gradle plugin only applies when that file is present, so nothing breaks before then,
   and `BuildConfig.HAS_FIREBASE` flips the app onto the real repositories.
4. Enable **Email/Password** and **Phone** sign-in providers.

Firebase has no phone+password provider, so `FirebaseAuthRepository` stores password accounts under
a synthetic address derived from the number (`919876543210@phone.jalraksha.app`) while the "Get an
OTP instead" path uses real phone auth. Both resolve to the same person.

### Pointing at Railway

The central dashboard's scoring API is read over HTTP:

```bash
./gradlew :app:assembleDebug -PjalrakshaApiBaseUrl=https://your-service.up.railway.app/
```

or put `jalrakshaApiBaseUrl=…` in `local.properties`. Endpoints the app expects are in
`data/remote/JalrakshaApi.kt`:

| Endpoint                          | Purpose                                             |
| --------------------------------- | --------------------------------------------------- |
| `GET  /v1/villages`               | Villages a user may bind to (screen 03)             |
| `GET  /v1/villages/{id}/report`   | Scored dashboard payload (screen 04)                |
| `GET  /v1/villages/{id}/trends`   | Scored trend window (screen 05); `?range=7D\|30D\|1Y`   |
| `GET  /v1/profile`                | The signed-in account (screen 07)                   |
| `PATCH /v1/profile/alerts`        | Turn unsafe-water alerts on or off                  |
| `GET  /v1/reports`                | Reports this user filed (screen 06)                 |
| `POST /v1/reports`                | File a new report                                   |
| `POST /v1/devices`                | Register this install's FCM token against a village |

### Notifications

The dashboard sends FCM **data** messages so the app picks the channel and the tap target itself.
Expected keys: `title`, `body`, and `channel` — `advisory` (urgent, high importance) or `updates`
(daily scores, tanker checks). `JalrakshaMessagingService.onRegistered` posts the token to
`POST /v1/devices` along with the bound village, so an advisory fans out only to the villages it
applies to.

### Languages

Eight languages: English, Hindi, Marathi, Bengali, Telugu, Tamil, Gujarati, Kannada. The choice is
made **in the app**, not taken from the device — a villager's handset is often set to a language
they did not pick and cannot read.

`ProvideAppLocale` in [MainActivity](app/src/main/java/com/example/jalraksha/MainActivity.kt)
overrides `LocalResources`, `LocalConfiguration` and `LocalContext` for the whole tree, so tapping a
tile on screen 02 re-renders every string on the next frame — no activity recreation, no flash.
Anything outside the composition (notification channel names, pushed advisories, the
`Accept-Language` header) reads `ServiceLocator.currentLanguageCode`, mirrored out of DataStore by
`JalrakshaApp`.

Numbers and elapsed times go through
[Formatters.kt](app/src/main/java/com/example/jalraksha/ui/text/Formatters.kt) so digits and
grouping follow the chosen language too.

**The backend sends keys, not sentences.** `WaterReport` carries `verdict_key: "safe"`, parameters
carry `key: "turbidity"` and `status_key: "clear"`, and the app resolves them in
[WaterStrings.kt](app/src/main/java/com/example/jalraksha/ui/text/WaterStrings.kt). Only free-text
advisories an officer typed arrive pre-translated — Railway reads `Accept-Language` for those.
Village names are the exception: they ship as a `names` map keyed by language code.

Adding a string: put it in `values/strings.xml` and all seven `values-<code>` files.
`./gradlew :app:testDebugUnitTest` fails if a key, or a `%1$s` inside one, is missing anywhere.

### Design fidelity

`ui/theme/Color.kt` and `Type.kt` hold the design's palette and type ramp verbatim; every screen
reads from them rather than hard-coding hex. Each screen has an `@Preview` at the design's 390 × 844
artboard size — open any `*Screen.kt` in Android Studio to see all four without a device.
