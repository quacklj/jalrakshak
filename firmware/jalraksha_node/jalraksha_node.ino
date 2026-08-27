/*
  Jalraksha One — ESP32-S3 field node
  Four probes streamed to the dashboard, two pump relays driven from it
  ---------------------------------------------------------------------------
  WIRING
    ADS1115  VDD -> 3.3V   GND -> common   SDA -> GPIO 8   SCL -> GPIO 9
             ADDR -> GND   (I2C address 0x48)

      pH        AOUT ------------------------------------> ADS1115 A0  (direct)
      TDS       AOUT ------------------------------------> ADS1115 A1  (direct)
      Turbidity AOUT -> [10k] -> node -> [15k] -> GND
                                node -----------------> ADS1115 A2

    DS18B20   VCC -> 3.3V   GND -> common
              DATA -> GPIO 4   (+ 4.7k pull-up between DATA and 3.3V)

    Relay board  IN1 -> GPIO 14      Pump 1
                 IN2 -> GPIO 18      Pump 2
                 VCC -> 5V, GND -> common with the ESP32

  ---------------------------------------------------------------------------
  TWO THINGS TO CHECK BEFORE POWERING UP

  1. The pH board's supply. The ADS1115 runs on 3.3V, and its inputs must not
     go above that rail. A pH board powered from 5V can output up to 5V, which
     saturates the reading and stresses the ADC. Run the pH board from 3.3V, or
     put a divider on its AOUT the way the turbidity probe has one.

  2. RELAY_ACTIVE_LOW below. Most cheap relay boards energise when the input is
     pulled LOW; some are the opposite. Get this wrong and the pumps run when
     the dashboard says they are off. Test with the pump disconnected first —
     you should hear the relay click when you press Start, not before.

  ---------------------------------------------------------------------------
  LIBRARIES (Arduino Library Manager)
    - "OneWire"            by Jim Studt / Paul Stoffregen
    - "DallasTemperature"  by Miles Burton
    - "Adafruit ADS1X15"   by Adafruit
  WiFi.h / HTTPClient.h ship with the ESP32 board package. No JSON library is
  needed: the relay poll returns plain text, one character per relay.

  If Serial Monitor shows nothing: Tools -> USB CDC On Boot -> Enabled, re-upload.

  This node never stops reporting. A missing probe is uploaded as null so the
  dashboard can show WHICH sensor is down; going silent would only tell you
  that something, somewhere, is broken.
*/

#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <Adafruit_ADS1X15.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <esp_system.h>

/* ======================= EDIT THIS BLOCK ======================= */
/*
  Put your real credentials in `secrets.h` (copy secrets.example.h) rather than
  here. That file is git-ignored, so your Wi-Fi password never reaches GitHub.
  These placeholders are only the fallback that keeps a fresh clone compiling.
*/
#if __has_include("secrets.h")
#include "secrets.h"
#endif

#ifndef JR_WIFI_SSID
#define JR_WIFI_SSID "your-wifi-name"
#endif
#ifndef JR_WIFI_PASSWORD
#define JR_WIFI_PASSWORD "your-wifi-password"
#endif
#ifndef JR_SERVER_HOST
// Must be reachable FROM THE ESP32, so never "localhost".
// Find your laptop's address with:  ipconfig getifaddr en0
#define JR_SERVER_HOST "http://192.168.1.10:3000"
#endif
#ifndef JR_DEVICE_TOKEN
#define JR_DEVICE_TOKEN ""
#endif

const char* WIFI_SSID = JR_WIFI_SSID;
const char* WIFI_PASSWORD = JR_WIFI_PASSWORD;

// Everything hangs off one host so there is a single address to change.
const char* SERVER_HOST = JR_SERVER_HOST;
const char* INGEST_PATH = "/api/ingest";
const char* RELAY_PATH = "/api/relays?fmt=text";

const char* DEVICE_ID = "ESP32-JR01";
const char* DEVICE_TOKEN = JR_DEVICE_TOKEN;

// Set false if your relay board energises on a HIGH input. See the note above.
const bool RELAY_ACTIVE_LOW = true;

const unsigned long POST_INTERVAL_MS = 10000;  // how often a reading is uploaded
/* =============================================================== */

const unsigned long SAMPLE_INTERVAL_MS = 2000;   // how often the sensors are read
const unsigned long RESCAN_INTERVAL_MS = 30000;  // how often a missing probe is retried
const unsigned long RELAY_POLL_MS = 1000;        // how often the pump command is fetched

/* ---- Pump safety ----------------------------------------------------------
   No float switch is wired, so nothing physical stops a running pump: it can
   overflow a tank or run itself dry. These two limits are the whole safety
   story, and they are enforced here as well as on the server so that a dead
   dashboard, a crashed browser or a dropped network cannot leave a motor on.
   --------------------------------------------------------------------------- */
const unsigned long PUMP_MAX_RUN_MS = 5UL * 60UL * 1000UL;  // hard stop per run
const unsigned long PUMP_COMMS_FAILSAFE_MS = 30000UL;       // silence -> all off

// ---- DS18B20 ----
#define ONE_WIRE_PIN 4
OneWire oneWire(ONE_WIRE_PIN);
DallasTemperature tempSensor(&oneWire);

// ---- ADS1115 channels ----
Adafruit_ADS1115 ads;
const int PH_CHANNEL = 0;
const int TDS_CHANNEL = 1;
const int TURBIDITY_CHANNEL = 2;
const float DIVIDER_RATIO = 15.0 / (10.0 + 15.0);  // 10k/15k divider = 0.6

// ---- Relays ----
const int RELAY_COUNT = 2;
const int RELAY_PINS[RELAY_COUNT] = { 14, 18 };
bool relayOn[RELAY_COUNT] = { false, false };
unsigned long relaySince[RELAY_COUNT] = { 0, 0 };

// ---- what is actually present right now ----
bool adsPresent = false;
bool dsPresent = false;

// ---- accumulators, averaged between uploads ----
float tempSum = 0.0f;
int tempCount = 0;
float phSum = 0.0f;
int phCount = 0;
float tdsSum = 0.0f;
int tdsCount = 0;
float turbSum = 0.0f;
int turbCount = 0;
int32_t turbRawSum = 0;
float turbMin = 99.0f;
float turbMax = -99.0f;

unsigned long lastSample = 0;
unsigned long lastPost = 0;
unsigned long lastRescan = 0;
unsigned long lastRelayPoll = 0;
unsigned long lastServerContact = 0;

// Explicit prototypes — the IDE usually generates these, but not reliably for
// functions used above their definition.
void diagnoseOneWire();
void scanSensors(bool verbose);
void reportPost(HTTPClient& http, int code, const char* body);
void diagnoseAds();
void applyRelay(int index, bool on, const char* why);
void allRelaysOff(const char* why);
void pollRelays();
void enforcePumpLimits();
bool buildUrl(char* out, size_t len, const char* path);

// Why the chip last restarted. Uploaded with every reading, because on the S3
// the USB serial port dies with each reset — so the dashboard is often the only
// place you can actually read this.
const char* RESET_REASON = "unknown";

const char* resetReasonName() {
  switch (esp_reset_reason()) {
    case ESP_RST_POWERON: return "power-on";
    case ESP_RST_EXT: return "external-reset";
    case ESP_RST_SW: return "software";
    case ESP_RST_PANIC: return "crash-panic";
    case ESP_RST_INT_WDT: return "interrupt-watchdog";
    case ESP_RST_TASK_WDT: return "task-watchdog";
    case ESP_RST_WDT: return "watchdog";
    case ESP_RST_DEEPSLEEP: return "deep-sleep";
    case ESP_RST_BROWNOUT: return "brownout";
    default: return "unknown";
  }
}

const char* wifiStatusName(wl_status_t s) {
  switch (s) {
    case WL_NO_SSID_AVAIL: return "network not found - check the SSID spelling";
    case WL_CONNECT_FAILED: return "rejected - check the password";
    case WL_CONNECTION_LOST: return "connection lost";
    case WL_DISCONNECTED: return "disconnected";
    case WL_IDLE_STATUS: return "idle";
    default: return "not connected";
  }
}

void connectWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;

  Serial.print("WiFi: connecting to \"");
  Serial.print(WIFI_SSID);
  Serial.println("\"");
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) {
    delay(400);
    Serial.print(".");
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("WiFi: connected, this device is ");
    Serial.print(WiFi.localIP());
    Serial.print("  gateway ");
    Serial.println(WiFi.gatewayIP());
  } else {
    Serial.print("WiFi: FAILED - ");
    Serial.println(wifiStatusName(WiFi.status()));
    Serial.println("      2.4GHz only. The ESP32 cannot see 5GHz networks.");
  }
}

/* Joins SERVER_HOST and a path. Returns false rather than emitting a truncated
   URL, because a half-formed address fails in a way that looks like a network
   fault and wastes an hour. */
bool buildUrl(char* out, size_t len, const char* path) {
  int n = snprintf(out, len, "%s%s", SERVER_HOST, path);
  return n > 0 && (size_t)n < len;
}

/* ======================= RELAYS ======================= */

/* Drives one relay and remembers when it changed.

   The pin is written BEFORE it is made an output, so the ESP32's boot-time
   floating pin cannot pulse a pump for the microseconds between pinMode() and
   the first digitalWrite(). On an active-low board that pulse is a real motor
   kick every time the chip resets. */
void applyRelay(int index, bool on, const char* why) {
  if (index < 0 || index >= RELAY_COUNT) return;
  int level = (on == RELAY_ACTIVE_LOW) ? LOW : HIGH;
  digitalWrite(RELAY_PINS[index], level);
  pinMode(RELAY_PINS[index], OUTPUT);
  digitalWrite(RELAY_PINS[index], level);

  if (relayOn[index] != on) {
    relayOn[index] = on;
    relaySince[index] = millis();
    Serial.print("Pump ");
    Serial.print(index + 1);
    Serial.print(" (GPIO ");
    Serial.print(RELAY_PINS[index]);
    Serial.print(") -> ");
    Serial.print(on ? "ON" : "OFF");
    Serial.print("   [");
    Serial.print(why);
    Serial.println("]");
  }
}

void allRelaysOff(const char* why) {
  for (int i = 0; i < RELAY_COUNT; i++) applyRelay(i, false, why);
}

/* Asks the dashboard what the pumps should be doing.

   The response is one character per relay, '1' or '0' — no JSON parser needed,
   and small enough to poll every second without loading the node. Anything
   that is not exactly RELAY_COUNT characters is treated as a failed poll and
   ignored, so a captive-portal login page or an error body can never be
   misread as a command to start a motor. */
void pollRelays() {
  if (WiFi.status() != WL_CONNECTED) return;

  char url[160];
  if (!buildUrl(url, sizeof(url), RELAY_PATH)) return;

  WiFiClient client;
  HTTPClient http;
  if (!http.begin(client, url)) return;
  http.setTimeout(3000);
  if (strlen(DEVICE_TOKEN) > 0) http.addHeader("x-device-token", DEVICE_TOKEN);

  int code = http.GET();
  if (code == 200) {
    String body = http.getString();
    body.trim();
    if (body.length() == RELAY_COUNT) {
      lastServerContact = millis();
      for (int i = 0; i < RELAY_COUNT; i++) {
        applyRelay(i, body[i] == '1', "dashboard");
      }
    } else {
      Serial.print("Relay poll: unexpected body \"");
      Serial.print(body);
      Serial.println("\" - ignored");
    }
  } else if (code == 401) {
    Serial.println("Relay poll: 401 - DEVICE_TOKEN does not match the server's.");
  }
  http.end();
}

/* The two limits that stand in for a float switch.

   Both are checked here rather than trusted to the server, because the whole
   point is to survive the server going away. millis() overflow is handled by
   unsigned subtraction, which wraps correctly. */
void enforcePumpLimits() {
  unsigned long nowMs = millis();

  for (int i = 0; i < RELAY_COUNT; i++) {
    if (relayOn[i] && nowMs - relaySince[i] >= PUMP_MAX_RUN_MS) {
      applyRelay(i, false, "max run time reached");
      Serial.println("      A pump hit its run limit. Nothing physical stops it otherwise -");
      Serial.println("      wire a float switch in series before running this unattended.");
    }
  }

  bool anyOn = false;
  for (int i = 0; i < RELAY_COUNT; i++) anyOn = anyOn || relayOn[i];
  if (!anyOn) return;

  // lastServerContact is 0 until the first successful poll, so a node that has
  // never reached the dashboard cannot hold a pump on either.
  if (nowMs - lastServerContact >= PUMP_COMMS_FAILSAFE_MS) {
    allRelaysOff("lost contact with the dashboard");
    Serial.println("      Losing the dashboard while a pump runs is exactly when you least");
    Serial.println("      want it latched on, so both relays are now off.");
  }
}

/* ======================= SENSORS ======================= */

/* Reads all four ADS1115 inputs and reports how quiet each one is.
   The chip multiplexes one sampling capacitor across every channel, so an
   unconnected input left floating can bleed charge into the channels you care
   about. A0/A1/A2 carry probes; A3 is spare and should read near zero and be
   steady. If it wanders, tie it to GND. */
void diagnoseAds() {
  if (!adsPresent) return;
  Serial.println("ADS1115 channel survey (8 samples each):");
  const char* names[4] = { "(pH)   ", "(TDS)  ", "(turb) ", "(spare)" };
  for (int ch = 0; ch < 4; ch++) {
    float lo = 99, hi = -99, sum = 0;
    for (int i = 0; i < 8; i++) {
      float v = ads.computeVolts(ads.readADC_SingleEnded(ch));
      if (v < lo) lo = v;
      if (v > hi) hi = v;
      sum += v;
      delay(12);
    }
    Serial.print("   A");
    Serial.print(ch);
    Serial.print(" ");
    Serial.print(names[ch]);
    Serial.print(" mean ");
    Serial.print(sum / 8, 4);
    Serial.print(" V   ripple ");
    Serial.print(hi - lo, 4);
    Serial.print(" V");
    if (ch == 3 && (hi - lo > 0.02 || fabs(sum / 8) > 0.05)) {
      Serial.print("   <- floating, tie A3 to GND");
    }
    Serial.println();
  }
  Serial.println("   Ripple above ~0.01 V on a probe channel: add a 0.1uF cap from");
  Serial.println("   that input to GND, and one across the ADS1115's VDD/GND pins.");
}

/* Probes the I2C bus and the 1-Wire bus. Safe to call repeatedly — a probe
   plugged in after boot is picked up on the next rescan. */
void scanSensors(bool verbose) {
  bool ads_was = adsPresent, ds_was = dsPresent;

  adsPresent = ads.begin(0x48);
  if (adsPresent) ads.setGain(GAIN_ONE);  // +/-4.096V, headroom for all three probes

  tempSensor.begin();
  dsPresent = tempSensor.getDeviceCount() > 0;

  if (verbose || adsPresent != ads_was) {
    Serial.print("ADS1115  : ");
    Serial.println(adsPresent ? "found at 0x48"
                              : "NOT FOUND - check VDD/GND, SDA GPIO8, SCL GPIO9, ADDR to GND");
  }
  if (verbose || dsPresent != ds_was) {
    Serial.print("DS18B20  : ");
    if (dsPresent) {
      Serial.println("found on GPIO4");
    } else {
      Serial.println("NOT FOUND");
      diagnoseOneWire();
    }
  }
}

/* Tells the three "no DS18B20" causes apart by looking at the bus itself.
   The line idles high through the pull-up; a device answers a reset with a
   presence pulse, so the pin level plus that answer localises the fault. */
void diagnoseOneWire() {
  bool presence = oneWire.reset();  // 1 = something answered
  pinMode(ONE_WIRE_PIN, INPUT);
  bool idleHigh = digitalRead(ONE_WIRE_PIN);

  Serial.print("           bus idles ");
  Serial.print(idleHigh ? "HIGH" : "LOW");
  Serial.print(", presence pulse ");
  Serial.println(presence ? "yes" : "no");

  if (!idleHigh) {
    Serial.println("           -> line is held low. Most likely the 4.7k pull-up to 3.3V is");
    Serial.println("              missing, or DATA is shorted to GND, or VCC/GND are swapped");
    Serial.println("              at the sensor (check if it is getting warm - unplug if so).");
  } else if (!presence) {
    Serial.println("           -> pull-up looks right but nothing answered. DATA may be on the");
    Serial.println("              wrong pin (expecting GPIO4), or the sensor has no 3.3V.");
  } else {
    Serial.println("           -> a device answered but did not enumerate. Suspect a bad crimp");
    Serial.println("              or a counterfeit sensor.");
  }
}

void setup() {
  // Relays first, before anything can take time. Both pumps must be off and
  // stay off across a reset, whatever caused it.
  for (int i = 0; i < RELAY_COUNT; i++) {
    int offLevel = RELAY_ACTIVE_LOW ? HIGH : LOW;
    digitalWrite(RELAY_PINS[i], offLevel);
    pinMode(RELAY_PINS[i], OUTPUT);
    digitalWrite(RELAY_PINS[i], offLevel);
    relayOn[i] = false;
    relaySince[i] = millis();
  }

  Serial.begin(115200);
  // Wait for the USB CDC port, but never forever — the node has to boot and run
  // on its own when no laptop is attached.
  unsigned long serialWait = millis();
  while (!Serial && millis() - serialWait < 3000) delay(10);

  Serial.println();
  Serial.println("=== Jalraksha node ===");

  RESET_REASON = resetReasonName();
  Serial.print("Last reset: ");
  Serial.println(RESET_REASON);
  if (strcmp(RESET_REASON, "brownout") == 0) {
    Serial.println("   BROWNOUT - the 3.3V rail collapsed, almost always the power supply.");
    Serial.println("   With relays on the board, suspect the pump's inrush current pulling the");
    Serial.println("   shared 5V down. Give the relay board and pumps their own supply, with");
    Serial.println("   only GND tied to the ESP32.");
  } else if (strcmp(RESET_REASON, "crash-panic") == 0) {
    Serial.println("   The sketch crashed. The backtrace above this line names the fault.");
  }

  Serial.print("Pumps    : GPIO ");
  Serial.print(RELAY_PINS[0]);
  Serial.print(" and GPIO ");
  Serial.print(RELAY_PINS[1]);
  Serial.print(", active-");
  Serial.print(RELAY_ACTIVE_LOW ? "LOW" : "HIGH");
  Serial.println(", both off");

  Wire.begin(8, 9);  // SDA = GPIO8, SCL = GPIO9
  scanSensors(true);
  diagnoseAds();

  connectWiFi();

  Serial.print("Uploading to: ");
  Serial.print(SERVER_HOST);
  Serial.println(INGEST_PATH);
  if (strstr(SERVER_HOST, "192.168.1.10:") != NULL) {
    Serial.println("WARNING: SERVER_HOST still looks like the example address.");
    Serial.println("         Set it to your dashboard machine's real IP in secrets.h.");
  }
  Serial.println("======================");
  Serial.println();
}

void sampleSensors() {
  // --- Temperature ---
  float tempC = DEVICE_DISCONNECTED_C;
  if (dsPresent) {
    tempSensor.requestTemperatures();
    tempC = tempSensor.getTempCByIndex(0);
  }
  bool tempOk = dsPresent && tempC != DEVICE_DISCONNECTED_C;
  if (tempOk) {
    tempSum += tempC;
    tempCount++;
  }

  // --- Analog probes ---
  float phVoltage = 0, tdsVoltage = 0, turbVoltage = 0, turbAdsVoltage = 0;
  int16_t turbRaw = 0;
  if (adsPresent) {
    phVoltage = ads.computeVolts(ads.readADC_SingleEnded(PH_CHANNEL));
    tdsVoltage = ads.computeVolts(ads.readADC_SingleEnded(TDS_CHANNEL));

    turbRaw = ads.readADC_SingleEnded(TURBIDITY_CHANNEL);
    turbAdsVoltage = ads.computeVolts(turbRaw);       // what the ADS1115 sees
    turbVoltage = turbAdsVoltage / DIVIDER_RATIO;     // sensor output, divider undone

    phSum += phVoltage;
    phCount++;
    tdsSum += tdsVoltage;
    tdsCount++;
    turbSum += turbVoltage;
    turbRawSum += turbRaw;
    turbCount++;
    if (turbVoltage < turbMin) turbMin = turbVoltage;
    if (turbVoltage > turbMax) turbMax = turbVoltage;
  }

  // --- Print combined reading ---
  Serial.print("Temp ");
  if (!tempOk) {
    Serial.print("  n/d  ");
  } else {
    Serial.print(tempC, 2);
    Serial.print(" C");
  }

  if (!adsPresent) {
    Serial.println("   |   no ADS1115 - pH, TDS and turbidity all unavailable");
    return;
  }

  Serial.print("  |  pH ");
  Serial.print(phVoltage, 3);
  Serial.print(" V");
  // A wired pH board idles near 2.5 V in neutral water. Pinned near zero is a
  // disconnected AOUT far more often than it is genuinely alkaline water.
  if (phVoltage < 0.08) Serial.print(" <-A0 floating?");
  if (phVoltage > 3.4) Serial.print(" <-over 3.3V rail!");

  Serial.print("  |  TDS ");
  Serial.print(tdsVoltage, 3);
  Serial.print(" V");
  if (tdsVoltage < 0.02) Serial.print(" <-probe in air?");

  Serial.print("  |  Turb ");
  Serial.print(turbVoltage, 3);
  Serial.print(" V (raw ");
  Serial.print(turbRaw);
  Serial.print(")");
  // A healthy probe sits near 4.2 V in clear water. Persistently low means
  // supply or wiring far more often than it means genuinely opaque water.
  if (turbVoltage < 2.0) Serial.print(" <-LOW: on 5V? AOUT on A2?");
  Serial.println();
}

void uploadReading() {
  bool haveTemp = tempCount > 0;
  bool havePh = phCount > 0;
  bool haveTds = tdsCount > 0;
  bool haveTurb = turbCount > 0;

  float avgTemp = haveTemp ? (tempSum / tempCount) : 0.0f;
  float avgPh = havePh ? (phSum / phCount) : 0.0f;
  float avgTds = haveTds ? (tdsSum / tdsCount) : 0.0f;
  float avgTurb = haveTurb ? (turbSum / turbCount) : 0.0f;
  int avgRaw = haveTurb ? (int)(turbRawSum / turbCount) : 0;

  // A working probe swings hard when you lift it out of the water. Reporting
  // the spread over each window makes that test readable without a multimeter.
  if (haveTurb) {
    Serial.print("Turbidity window: min ");
    Serial.print(turbMin, 3);
    Serial.print(" V  max ");
    Serial.print(turbMax, 3);
    Serial.print(" V  swing ");
    Serial.print(turbMax - turbMin, 3);
    Serial.println(" V");
    if (avgTurb < 2.0) {
      Serial.println("   Clear water should read ~4.2 V. Reading this low usually means the");
      Serial.println("   module is on 3.3V instead of 5V, so its IR LED is barely lit.");
      Serial.println("   Test: lift the probe into open air. A working sensor jumps toward 4 V.");
      Serial.println("   If it barely moves, it is power or the probe head, not the water.");
    }
  }

  // Reset accumulators regardless of upload success — never send stale averages.
  tempSum = 0;
  tempCount = 0;
  phSum = 0;
  phCount = 0;
  tdsSum = 0;
  tdsCount = 0;
  turbSum = 0;
  turbCount = 0;
  turbRawSum = 0;
  turbMin = 99.0f;
  turbMax = -99.0f;

  connectWiFi();
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Upload skipped: no WiFi");
    return;
  }

  // A probe that is not answering is sent as null, so the dashboard can name
  // which sensor is down instead of guessing from silence. Never send 0 for a
  // dead probe: 0 is a value, and 0 ppm or 0 NTU reads as very clean water.
  char tempField[24], phField[24], tdsField[24], turbField[24];
  if (haveTemp) snprintf(tempField, sizeof(tempField), "%.2f", avgTemp);
  else strcpy(tempField, "null");
  if (havePh) snprintf(phField, sizeof(phField), "%.4f", avgPh);
  else strcpy(phField, "null");
  if (haveTds) snprintf(tdsField, sizeof(tdsField), "%.4f", avgTds);
  else strcpy(tdsField, "null");
  if (haveTurb) snprintf(turbField, sizeof(turbField), "%.3f", avgTurb);
  else strcpy(turbField, "null");

  char body[420];
  snprintf(body, sizeof(body),
           "{\"device_id\":\"%s\",\"temp_c\":%s,\"ph_v\":%s,\"tds_v\":%s,"
           "\"turbidity_v\":%s,\"raw\":%d,\"relay1\":%d,\"relay2\":%d,"
           "\"rssi\":%d,\"uptime_ms\":%lu,\"reset_reason\":\"%s\",\"heap\":%lu}",
           DEVICE_ID, tempField, phField, tdsField, turbField, avgRaw,
           relayOn[0] ? 1 : 0, relayOn[1] ? 1 : 0,
           WiFi.RSSI(), millis(), RESET_REASON, (unsigned long)ESP.getFreeHeap());

  char url[160];
  if (!buildUrl(url, sizeof(url), INGEST_PATH)) {
    Serial.println("Upload failed: SERVER_HOST is too long to build a URL from");
    return;
  }

  HTTPClient http;
  bool secure = (strncmp(url, "https://", 8) == 0);

  // Only build the client actually needed. A WiFiClientSecure allocates its TLS
  // context on construction, so making one on every plain-HTTP post churned the
  // heap for nothing.
  if (secure) {
    WiFiClientSecure tlsClient;
    // No cert bundle on the device — fine for a hackathon deployment, but pin
    // a certificate with tlsClient.setCACert() before anyone calls this production.
    tlsClient.setInsecure();
    if (http.begin(tlsClient, url)) {
      http.setTimeout(8000);
      http.addHeader("Content-Type", "application/json");
      if (strlen(DEVICE_TOKEN) > 0) http.addHeader("x-device-token", DEVICE_TOKEN);
      int code = http.POST((uint8_t*)body, strlen(body));
      reportPost(http, code, body);
      http.end();
    } else {
      Serial.println("Upload failed: SERVER_HOST is not a valid URL");
    }
    return;
  }

  WiFiClient plainClient;
  if (!http.begin(plainClient, url)) {
    Serial.println("Upload failed: SERVER_HOST is not a valid URL");
    return;
  }

  http.setTimeout(8000);
  http.addHeader("Content-Type", "application/json");
  if (strlen(DEVICE_TOKEN) > 0) {
    http.addHeader("x-device-token", DEVICE_TOKEN);
  }

  int code = http.POST((uint8_t*)body, strlen(body));
  reportPost(http, code, body);
  http.end();
}

void reportPost(HTTPClient& http, int code, const char* body) {
  Serial.print("POST ");
  Serial.print(body);
  Serial.print("  -> ");
  if (code > 0) {
    Serial.print(code);
    Serial.print(" ");
    Serial.println(http.getString());
    // A 2xx means the dashboard is alive, which is what the pump failsafe
    // watches. Without this the failsafe would trip during a slow relay poll
    // even though the node is plainly still talking to the server.
    if (code >= 200 && code < 300) lastServerContact = millis();
    if (code == 401) Serial.println("      DEVICE_TOKEN does not match the server's.");
    if (code == 404) Serial.println("      URL must end in /api/ingest");
  } else {
    Serial.println(http.errorToString(code));
    Serial.println("      The ESP32 has WiFi but cannot reach the dashboard. Check that:");
    Serial.println("      - SERVER_HOST has your computer's CURRENT IP (it changes)");
    Serial.println("      - 'npm run dev' is running on that computer");
    Serial.println("      - both are on the same WiFi, and it is not a guest network");
  }
}

void loop() {
  unsigned long nowMs = millis();

  // Checked every pass, before anything that can block. A pump limit that only
  // runs after a successful network call is not a safety limit.
  enforcePumpLimits();

  if (nowMs - lastRelayPoll >= RELAY_POLL_MS) {
    lastRelayPoll = nowMs;
    pollRelays();
  }

  if (nowMs - lastSample >= SAMPLE_INTERVAL_MS) {
    lastSample = nowMs;
    sampleSensors();
  }

  // Pick up a probe that was plugged in (or fell out) after boot.
  if (nowMs - lastRescan >= RESCAN_INTERVAL_MS) {
    lastRescan = nowMs;
    if (!adsPresent || !dsPresent) scanSensors(false);
  }

  if (nowMs - lastPost >= POST_INTERVAL_MS) {
    lastPost = nowMs;
    uploadReading();
  }

  delay(20);
}

/*
  What to expect:
  - Serial prints a live four-sensor line every 2 s, and one POST line every 10 s.
  - The dashboard's Live Monitoring page updates within a second of each POST.
  - "-> 200 {"ok":true,...}" means it landed.
  - Pressing Start on the dashboard clicks the relay within about a second, and
    the button only says "Running" once this node has confirmed it in a payload.
  - A missing probe still uploads, as null, and the dashboard names it as
    "not detected" rather than showing the node as offline.
*/
