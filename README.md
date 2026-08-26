# Jalraksha One

Live water-quality dashboard for a single ESP32-S3 field node reading **water temperature**
(DS18B20) and **turbidity** (analog probe via ADS1115). The node posts each reading over the
internet; the dashboard charts both sensors in real time.

```
Jalraksha/
├── app/                          Android app (Kotlin + Jetpack Compose)
├── dashboard/                    Next.js app (the website)
└── firmware/jalraksha_node/      Arduino sketch for the ESP32-S3
```

Two sensors only, by design — pH, TDS, flow, level and leakage are not wired up yet, so the UI
does not pretend they exist.

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
| **Overview**         | Latest temperature, turbidity, composite risk, last 30 min sparklines |
| **Live Monitoring**  | Full-size charts for both sensors, 5 min → 24 h, device connectivity  |
| **History & Export** | Every stored reading, band filter, CSV download                       |
| **Device**           | Node identity, uptime, thresholds, turbidity calibration, ingest docs |

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
npm run simulate -- --fault temp      # DS18B20 off the 1-Wire bus
npm run simulate -- --fault divider   # 10k/15k divider missing, ADS1115 saturating
npm run simulate -- --fault cycle     # rotates through all of them
```

---

## When a sensor stops answering

A disconnected probe is never charted as a value. Each sensor is tracked separately and reported
as **not reading**, with the likely cause:

| What the device sends            | Dashboard shows | Diagnosis offered                                  |
| -------------------------------- | --------------- | -------------------------------------------------- |
| `temp_c: null` / `-127`          | Not detected    | DS18B20 off the bus — check GPIO4 and the 4.7k pull-up |
| `turbidity_v` below 0.3 V        | Not detected    | AOUT unplugged, the 15k is pulling the divider to ground |
| `turbidity_v` above 5.0 V        | Out of range    | Divider missing, ADS1115 saturating                 |
| Nothing at all for 30 s / 3 min  | Degraded → Offline | Wi-Fi, power, or `SERVER_URL`                    |

That state propagates everywhere: a banner at the top of the page, a red border on the affected
card, per-sensor dots in the sidebar on every page, `n/d` in the history table, and a
`temperature_status` / `turbidity_status` column in the CSV so a spreadsheet can't mistake a dead
probe for a zero reading.

It also changes the verdict. The composite risk score leaves a dead sensor out rather than scoring
it as zero, and while any sensor is down the dashboard refuses to show a green "Safe" badge —
it says **Partial coverage** instead, because one working probe cannot clear the water.

---

## 2. Point the ESP32 at it

Open `firmware/jalraksha_node/jalraksha_node.ino` and edit the block at the top:

```cpp
const char* WIFI_SSID     = "your-wifi-name";
const char* WIFI_PASSWORD = "your-wifi-password";
const char* SERVER_URL    = "http://192.168.1.10:3000/api/ingest";
const char* DEVICE_ID     = "ESP32-JR01";
```

`SERVER_URL` must be reachable **from the ESP32**, so `localhost` will not work — use your
laptop's LAN IP while developing (`ipconfig getifaddr en0` on macOS), or the public URL once
deployed.

Libraries required: **OneWire**, **DallasTemperature**, **Adafruit ADS1X15**. Wiring is unchanged
from the bench sketch and documented in the file header.

The sketch samples every 2 s (still printing to Serial, as before) and uploads the average every
10 s so a single noisy sample can't swing the dashboard.

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

Ingest payload:

```json
{
  "device_id": "ESP32-JR01",
  "temp_c": 26.94,
  "turbidity_v": 4.05,
  "raw": 12980,
  "rssi": -58,
  "uptime_ms": 412000
}
```

`temp_c` may be `null` (or `-127`) when the DS18B20 doesn't answer — the dashboard shows a gap in
the chart rather than a fake value. `turbidity_v` is the probe's own output in volts, with the
10k/15k divider already undone, exactly as the sketch prints it.

---

## 5. Calibrating turbidity

The probe outputs volts, not NTU. `dashboard/src/lib/config.ts` maps them linearly:

```ts
export const TURBIDITY_CLEAR_V = 4.2;   // volts in clear water  → 0 NTU
export const TURBIDITY_OPAQUE_V = 2.5;  // volts fully clouded   → TURBIDITY_MAX_NTU
export const TURBIDITY_MAX_NTU = 3000;
```

Put the probe in clean water, read the voltage on the Live Monitoring page, and set
`TURBIDITY_CLEAR_V` to it. Until you calibrate against a formazin standard, treat NTU as an
estimate — the voltage trace next to it is the honest measurement.

Safe/watch/warning bands for both sensors live in the same file, and the Device page renders
whatever you set there.

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
