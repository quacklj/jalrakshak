/*
  Jalraksha One — ESP32-S3 field node
  DS18B20 (temperature) + turbidity probe via ADS1115, streamed to the dashboard
  ---------------------------------------------------------------------------
  Wiring (unchanged from the bench test):
    DS18B20 VCC   -> 3.3V
    DS18B20 GND   -> common GND
    DS18B20 DATA  -> GPIO 4  (+ 4.7k pull-up between DATA and 3.3V)

    ADS1115 VDD   -> 3.3V
    ADS1115 GND   -> common GND
    ADS1115 SCL   -> GPIO 9
    ADS1115 SDA   -> GPIO 8
    ADS1115 ADDR  -> GND        (I2C address 0x48)

    Turbidity VCC  -> 5V
    Turbidity GND  -> common GND
    Turbidity AOUT -> [10k] -> node -> [15k] -> GND
                                node -> ADS1115 A2

  Libraries (Arduino Library Manager):
    - "OneWire"            by Jim Studt / Paul Stoffregen
    - "DallasTemperature"  by Miles Burton
    - "Adafruit ADS1X15"
  WiFi.h / HTTPClient.h ship with the ESP32 board package.

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
const char* WIFI_SSID     = "your-wifi-name";
const char* WIFI_PASSWORD = "your-wifi-password";

// Must be reachable FROM THE ESP32, so never "localhost".
// Find your laptop's address with:  ipconfig getifaddr en0
const char* SERVER_URL = "http://192.168.1.10:3000/api/ingest";

const char* DEVICE_ID = "ESP32-JR01";

// Leave empty unless you set DEVICE_TOKEN in the dashboard's environment.
const char* DEVICE_TOKEN = "";

const unsigned long POST_INTERVAL_MS = 10000;  // how often a reading is uploaded
/* =============================================================== */

const unsigned long SAMPLE_INTERVAL_MS = 2000;   // how often the sensors are read
const unsigned long RESCAN_INTERVAL_MS = 30000;  // how often a missing probe is retried

// ---- DS18B20 ----
#define ONE_WIRE_PIN 4
OneWire oneWire(ONE_WIRE_PIN);
DallasTemperature tempSensor(&oneWire);

// ---- ADS1115 / turbidity ----
Adafruit_ADS1115 ads;
const int TURBIDITY_CHANNEL = 2;                   // A2
const float DIVIDER_RATIO = 15.0 / (10.0 + 15.0);  // 10k/15k divider = 0.6

// ---- what is actually present right now ----
bool adsPresent = false;
bool dsPresent = false;

// ---- accumulators, averaged between uploads ----
float tempSum = 0.0f;
int   tempCount = 0;
float voltSum = 0.0f;
int   voltCount = 0;
int32_t rawSum = 0;
float voltMin = 99.0f;
float voltMax = -99.0f;

unsigned long lastSample = 0;
unsigned long lastPost = 0;
unsigned long lastRescan = 0;

// Explicit prototypes — the IDE usually generates these, but not reliably for
// functions used above their definition.
void diagnoseOneWire();
void scanSensors(bool verbose);
void reportPost(HTTPClient& http, int code, const char* body);
void diagnoseAds();

// Why the chip last restarted. Uploaded with every reading, because on the S3
// the USB serial port dies with each reset — so the dashboard is often the only
// place you can actually read this.
const char* RESET_REASON = "unknown";

const char* resetReasonName() {
  switch (esp_reset_reason()) {
    case ESP_RST_POWERON:   return "power-on";
    case ESP_RST_EXT:       return "external-reset";
    case ESP_RST_SW:        return "software";
    case ESP_RST_PANIC:     return "crash-panic";
    case ESP_RST_INT_WDT:   return "interrupt-watchdog";
    case ESP_RST_TASK_WDT:  return "task-watchdog";
    case ESP_RST_WDT:       return "watchdog";
    case ESP_RST_DEEPSLEEP: return "deep-sleep";
    case ESP_RST_BROWNOUT:  return "brownout";
    default:                return "unknown";
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

/* Reads all four ADS1115 inputs and reports how quiet each one is.
   The chip multiplexes one sampling capacitor across every channel, so an
   unconnected input left floating can bleed charge into the channel you care
   about. A2 is where the probe lives; A0/A1/A3 should read near zero and be
   steady. If they wander, tie them to GND. */
void diagnoseAds() {
  if (!adsPresent) return;
  Serial.println("ADS1115 channel survey (8 samples each):");
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
    Serial.print(ch == TURBIDITY_CHANNEL ? " (probe) " : "         ");
    Serial.print("mean ");
    Serial.print(sum / 8, 4);
    Serial.print(" V   ripple ");
    Serial.print(hi - lo, 4);
    Serial.print(" V");
    if (ch != TURBIDITY_CHANNEL && (hi - lo > 0.02 || fabs(sum / 8) > 0.05)) {
      Serial.print("   <- floating, tie A");
      Serial.print(ch);
      Serial.print(" to GND");
    }
    Serial.println();
  }
  Serial.println("   Ripple above ~0.01 V on the probe channel: add a 0.1uF cap");
  Serial.println("   from A2 to GND, and one across the ADS1115's VDD/GND pins.");
}

/* Probes the I2C bus and the 1-Wire bus. Safe to call repeatedly — a probe
   plugged in after boot is picked up on the next rescan. */
void scanSensors(bool verbose) {
  bool ads_was = adsPresent, ds_was = dsPresent;

  adsPresent = ads.begin(0x48);
  if (adsPresent) ads.setGain(GAIN_ONE);  // +/-4.096V, matches the divided signal

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
  bool presence = oneWire.reset();          // 1 = something answered
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
    Serial.println("   Try a different USB DATA cable (thin/charge-only cables are the usual");
    Serial.println("   culprit), a port directly on the computer rather than a hub, and give");
    Serial.println("   the 5V turbidity module its own supply with GND tied to the ESP32.");
  } else if (strcmp(RESET_REASON, "crash-panic") == 0) {
    Serial.println("   The sketch crashed. The backtrace above this line names the fault.");
  }

  Wire.begin(8, 9);   // SDA = GPIO8, SCL = GPIO9
  scanSensors(true);
  diagnoseAds();

  connectWiFi();

  Serial.print("Uploading to: ");
  Serial.println(SERVER_URL);
  if (strstr(SERVER_URL, "192.168.1.10") != NULL) {
    Serial.println("WARNING: SERVER_URL still looks like the example address.");
    Serial.println("         Set it to your dashboard machine's real IP.");
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

  // --- Turbidity ---
  int16_t raw = 0;
  float adsVoltage = 0, turbidityVoltage = 0;
  if (adsPresent) {
    raw = ads.readADC_SingleEnded(TURBIDITY_CHANNEL);
    adsVoltage = ads.computeVolts(raw);              // what the ADS1115 actually sees
    turbidityVoltage = adsVoltage / DIVIDER_RATIO;   // sensor's real output, divider undone
    voltSum += turbidityVoltage;
    rawSum += raw;
    voltCount++;
    if (turbidityVoltage < voltMin) voltMin = turbidityVoltage;
    if (turbidityVoltage > voltMax) voltMax = turbidityVoltage;
  }

  // --- Print combined reading ---
  Serial.print("Temp: ");
  if (!tempOk) {
    Serial.print("no sensor detected");
  } else {
    Serial.print(tempC, 2);
    Serial.print(" C");
  }

  Serial.print("   |   Turbidity ");
  if (!adsPresent) {
    Serial.println("no ADS1115");
  } else {
    Serial.print("raw: ");
    Serial.print(raw);
    Serial.print("   ADS: ");
    Serial.print(adsVoltage, 3);
    Serial.print(" V   Sensor: ");
    Serial.print(turbidityVoltage, 3);
    Serial.print(" V");
    // A healthy probe sits near 4.2 V in clear water. Persistently low means
    // supply or wiring far more often than it means genuinely opaque water.
    if (turbidityVoltage < 2.0) {
      Serial.print("  <- LOW: is the probe on 5V (not 3.3V) and is AOUT on A2?");
    }
    Serial.println();
  }
}

void uploadReading() {
  bool haveTemp = tempCount > 0;
  bool haveTurb = voltCount > 0;

  float avgTemp  = haveTemp ? (tempSum / tempCount) : 0.0f;
  float avgVolts = haveTurb ? (voltSum / voltCount) : 0.0f;
  int   avgRaw   = haveTurb ? (int)(rawSum / voltCount) : 0;

  // A working probe swings hard when you lift it out of the water. Reporting
  // the spread over each window makes that test readable without a multimeter.
  if (haveTurb) {
    Serial.print("Turbidity window: min ");
    Serial.print(voltMin, 3);
    Serial.print(" V  max ");
    Serial.print(voltMax, 3);
    Serial.print(" V  swing ");
    Serial.print(voltMax - voltMin, 3);
    Serial.println(" V");
    if (avgVolts < 2.0) {
      Serial.println("   Clear water should read ~4.2 V. Reading this low usually means the");
      Serial.println("   module is on 3.3V instead of 5V, so its IR LED is barely lit.");
      Serial.println("   Test: lift the probe into open air. A working sensor jumps toward 4 V.");
      Serial.println("   If it barely moves, it is power or the probe head, not the water.");
    }
  }

  // Reset accumulators regardless of upload success — never send stale averages.
  tempSum = 0; tempCount = 0;
  voltSum = 0; voltCount = 0; rawSum = 0;
  voltMin = 99.0f; voltMax = -99.0f;

  connectWiFi();
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Upload skipped: no WiFi");
    return;
  }

  // A probe that is not answering is sent as null, so the dashboard can name
  // which sensor is down instead of guessing from silence.
  char tempField[24];
  char turbField[24];
  if (haveTemp) snprintf(tempField, sizeof(tempField), "%.2f", avgTemp);
  else          strcpy(tempField, "null");
  if (haveTurb) snprintf(turbField, sizeof(turbField), "%.3f", avgVolts);
  else          strcpy(turbField, "null");

  char body[320];
  snprintf(body, sizeof(body),
           "{\"device_id\":\"%s\",\"temp_c\":%s,\"turbidity_v\":%s,\"raw\":%d,"
           "\"rssi\":%d,\"uptime_ms\":%lu,\"reset_reason\":\"%s\",\"heap\":%lu}",
           DEVICE_ID, tempField, turbField, avgRaw, WiFi.RSSI(), millis(),
           RESET_REASON, (unsigned long)ESP.getFreeHeap());

  HTTPClient http;
  bool secure = (strncmp(SERVER_URL, "https://", 8) == 0);

  // Only build the client actually needed. A WiFiClientSecure allocates its TLS
  // context on construction, so making one on every plain-HTTP post churned the
  // heap for nothing.
  bool started;
  if (secure) {
    WiFiClientSecure tlsClient;
    // No cert bundle on the device — fine for a hackathon deployment, but pin
    // a certificate with tlsClient.setCACert() before anyone calls this production.
    tlsClient.setInsecure();
    started = http.begin(tlsClient, SERVER_URL);
    if (started) {
      http.setTimeout(8000);
      http.addHeader("Content-Type", "application/json");
      if (strlen(DEVICE_TOKEN) > 0) http.addHeader("x-device-token", DEVICE_TOKEN);
      int code = http.POST((uint8_t*)body, strlen(body));
      reportPost(http, code, body);
      http.end();
    } else {
      Serial.println("Upload failed: SERVER_URL is not a valid URL");
    }
    return;
  }

  WiFiClient plainClient;
  started = http.begin(plainClient, SERVER_URL);

  if (!started) {
    Serial.println("Upload failed: SERVER_URL is not a valid URL");
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
    if (code == 401) Serial.println("      DEVICE_TOKEN does not match the server's.");
    if (code == 404) Serial.println("      URL must end in /api/ingest");
  } else {
    Serial.println(http.errorToString(code));
    Serial.println("      The ESP32 has WiFi but cannot reach the dashboard. Check that:");
    Serial.println("      - SERVER_URL has your computer's CURRENT IP (it changes)");
    Serial.println("      - 'npm run dev' is running on that computer");
    Serial.println("      - both are on the same WiFi, and it is not a guest network");
  }
}

void loop() {
  unsigned long nowMs = millis();

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
  - Serial prints a live reading every 2 s, and one POST line every 10 s.
  - The dashboard's Live Monitoring page updates within a second of each POST.
  - "-> 200 {"ok":true,...}" means it landed.
  - A missing probe still uploads, as null, and the dashboard names it as
    "not detected" rather than showing the node as offline.
*/
