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

    AJ-SR04M  VCC -> 5V    GND -> common
              TRIG -> GPIO 15   (direct, 3.3V logic is enough to trigger it)
              ECHO -> [1k] -> node -> [2k] -> GND
                              node -> GPIO 17
              NOTE: TRIG moved off GPIO 16, which the servo now has.

    MG996R    SIGNAL -> GPIO 16
              POWER  -> its OWN 5V supply, NOT the ESP32's. Only GND is shared.
              A stalled MG996R pulls ~2.5 A. On a shared rail that browns out
              the ESP32 and takes every sensor down with it.
              The divider is not optional: ECHO idles at 5V and the ESP32-S3's
              pins are 3.3V. 1k/2k brings 5V down to 3.3V.

    Relay board  IN1 -> GPIO 14      Pump 1
                 IN2 -> GPIO 18      Pump 2
                 VCC -> 5V, GND -> common with the ESP32

  ---------------------------------------------------------------------------
  TWO THINGS TO CHECK BEFORE POWERING UP

  1. The pH board's supply. The ADS1115 runs on 3.3V, and its inputs must not
     go above that rail. A pH board powered from 5V can output up to 5V, which
     saturates the reading and stresses the ADC. Run the pH board from 3.3V, or
     put a divider on its AOUT the way the turbidity probe has one.

  2. Where the AJ-SR04M is mounted. It has a ~20 cm blind zone — much larger
     than the 2 cm of an HC-SR04 — and inside it the sensor returns nothing or
     nonsense. Mount it so that even a completely full tank sits more than
     20 cm below the sensor face, or the level will drop out exactly when the
     tank is fullest. Then measure the two distances the dashboard needs:
     sensor face to the full water line, and sensor face to the tank floor.
     They go in dashboard/src/lib/config.ts, not here.

  3. The servo's supply. See the MG996R note in the wiring block: it needs its
     own 5 V, with only GND tied back. This is the same failure the pumps can
     cause, but a servo stalling against a jammed load is a harder, longer hit
     than pump inrush.

  4. RELAY_ACTIVE_LOW below. Most cheap relay boards energise when the input is
     pulled LOW; some are the opposite. Get this wrong and the pumps run when
     the dashboard says they are off. Test with the pump disconnected first —
     you should hear the relay click when you press Start, not before.

  ---------------------------------------------------------------------------
  LIBRARIES (Arduino Library Manager)
    - "OneWire"            by Jim Studt / Paul Stoffregen
    - "DallasTemperature"  by Miles Burton
    - "Adafruit ADS1X15"   by Adafruit
    - "ESP32Servo"         by Kevin Harrington
  The ultrasonic needs no library at all — it is plain pulseIn() timing.
  WiFi.h / HTTPClient.h ship with the ESP32 board package. No JSON library is
  needed: the relay poll returns plain text, one character per relay.

  If Serial Monitor shows nothing: Tools -> USB CDC On Boot -> Enabled, re-upload.

  This node never stops reporting. A missing probe is uploaded as null so the
  dashboard can show WHICH sensor is down; going silent would only tell you
  that something, somewhere, is broken.
*/

#include <Wire.h>
#include <ESP32Servo.h>
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
#define JR_WIFI_SSID "Spiritual Oasis"
#endif
#ifndef JR_WIFI_PASSWORD
#define JR_WIFI_PASSWORD "9809096979"
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
const char* SERVO_PATH = "/api/servo?fmt=text";
const char* SERVO_CAL_PATH = "/api/servo/cal?fmt=text";

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

// ---- AJ-SR04M ultrasonic (tank level) ----
const int TRIG_PIN = 15;
const int ECHO_PIN = 17;

/* 25 ms of flight is about 4.3 m each way — far past any tank, and short
   enough that a burst of misses cannot stall the loop for long. */
const unsigned long PING_TIMEOUT_US = 25000UL;
/* The transducer rings after each burst. Pinging again before it settles reads
   the tail of the previous ping as a very close object. 60 ms is the figure the
   AJ-SR04M's datasheet asks for. */
const int PING_SETTLE_MS = 60;
const int PINGS_PER_SAMPLE = 5;
/* Room for every sample between two uploads (10 s / 2 s = 5), with slack. */
const int DIST_WINDOW = 24;

/* ---- MG996R servo, continuous rotation ---------------------------------
   No encoder, no feedback, no way to ask it where it is. Angles are produced
   by running the motor at full speed for a measured time and then stopping,
   so every position this node reports is dead reckoning and is labelled as
   such all the way up to the dashboard.

   Driven straight off LEDC rather than through a servo library: the ESP32
   core already has the peripheral, and one less dependency is one less thing
   to install on a fresh machine before the node will build. */
const int SERVO_PIN = 16;
const int SERVO_FREQ_HZ = 50;        // standard 20 ms servo frame

/* Driven through ESP32Servo rather than raw LEDC, and that is not a style
   choice — it is a bug fix.

   The ESP32-S3's LEDC timers are 14 bits wide (SOC_LEDC_TIMER_BIT_WIDTH), and
   this sketch previously asked ledcAttach() for 16. The core rejects anything
   over the maximum and returns false, so the pin was never attached, every
   ledcWrite() silently no-oped, and the servo received no pulses at all. On the
   original ESP32 the limit is 20 bits and the same code would have worked,
   which is exactly the kind of difference that costs an evening.

   The library picks a valid resolution itself, and it is what the standalone
   bench sketch was already proven on with this servo and this board. */
Servo servoOut;
bool servoAttached = false;

/* Pulse widths. A continuous-rotation servo reads these as SPEED, not angle.

   These two numbers and the timing table below are ONE calibration: the
   milliseconds were measured while driving at this exact speed. Change the
   offset and every timing underneath it is wrong — a faster pulse covers the
   same angle in less time, so the old milliseconds overshoot.

   1500 +/- 300 is what the bench sketch used when the table was measured.

   NEUTRAL is worth trimming on its own: a CR MG996R often creeps at exactly
   1500 us, and a servo that creeps while it believes it is stopped destroys
   the dead reckoning silently. Nudge it a few us until the horn is still. */
/* These start at the bench figures and are then overwritten by whatever the
   dashboard's calibration page holds. The server owns them, not this flash:
   reflashing the node must not lose an afternoon of bench work, and a
   replacement board should pick up the same numbers the moment it boots. */
int servoNeutralUs = 1500;
int servoSpeedOffsetUs = 300;
inline int servoForwardUs() { return servoNeutralUs + servoSpeedOffsetUs; }
inline int servoReverseUs() { return servoNeutralUs - servoSpeedOffsetUs; }

/* Calibration, and it must match dashboard/src/lib/config.ts. Milliseconds of
   full-speed travel per sweep, measured on the bench. Not proportional: the
   per-120 segments are 715, 745 and 840 ms, because the rail sags as the move
   goes on. Interpolation between the anchors is therefore piecewise. */
const int SERVO_ANCHOR_COUNT = 4;
const float SERVO_ANCHOR_DEG[SERVO_ANCHOR_COUNT] = { 0, 120, 240, 360 };
unsigned long servoFwdMs[SERVO_ANCHOR_COUNT] = { 0, 715, 1460, 2300 };
unsigned long servoRevMs[SERVO_ANCHOR_COUNT] = { 0, 647, 1320, 2080 };

/* The dashboard only measures a full turn in reverse, so the two intermediate
   reverse figures are the forward ones scaled by that ratio. Recomputed
   whenever a new calibration arrives. */
void rebuildReverseTable(unsigned long tRev) {
  float k = servoFwdMs[3] > 0 ? (float)tRev / (float)servoFwdMs[3] : 1.0f;
  servoRevMs[0] = 0;
  servoRevMs[1] = (unsigned long)(servoFwdMs[1] * k);
  servoRevMs[2] = (unsigned long)(servoFwdMs[2] * k);
  servoRevMs[3] = tRev;
}

/* Nothing should ever run the motor longer than a full turn plus a margin. If
   a computed duration exceeds this the command is refused rather than trusted,
   because the failure mode is a servo that never stops. */
const unsigned long SERVO_MAX_MOVE_MS = 2600;
/* A raw bench run is allowed longer than a positioning move — measuring a
   revolution means driving for several of them — but still not forever. */
const unsigned long SERVO_MAX_RUN_MS = 30000;
const unsigned long SERVO_CAL_POLL_MS = 15000;

const unsigned long SERVO_POLL_MS = 1000;

// ---- Relays ----
const int RELAY_COUNT = 2;
const int RELAY_PINS[RELAY_COUNT] = { 14, 18 };
bool relayOn[RELAY_COUNT] = { false, false };
unsigned long relaySince[RELAY_COUNT] = { 0, 0 };

// ---- what is actually present right now ----
bool adsPresent = false;
bool dsPresent = false;
bool sonarPresent = false;

/* ---- servo state, all of it dead reckoning ---- */
float servoDeg = 0.0f;          // where we believe the horn is, 0-359.9
bool servoMoving = false;
/* Starts TRUE, and that is not pessimism. A reset wipes the dead reckoning but
   not the horn: after any restart the servo is physically wherever it was left,
   which the node has no way to discover. Reporting a confident 0 deg would be
   inventing a measurement. It stays uncertain until someone re-zeroes against
   a physical mark. */
bool servoUncertain = true;
int servoMovesSinceZero = 0;
long servoSeq = 0;              // last command sequence acted on
unsigned long servoMoveStart = 0;
unsigned long servoMoveMs = 0;
float servoMoveFrom = 0.0f;
float servoMoveSweep = 0.0f;
int servoMoveDir = 1;
unsigned long lastServoPoll = 0;
/* Set when a move ends, so the next loop uploads immediately instead of
   sitting on a stale angle for the rest of the post interval. A 2 s move
   followed by 8 s of silence makes the dashboard look frozen. */
bool servoReportDue = false;

/* ---- bench modes, for the calibration page ----
   These drive the motor for a time instead of to an angle, which is what
   measuring a servo actually requires. While any of them is running the angle
   is meaningless, so they all end by marking the position uncertain. */
bool servoFreeSpinning = false;         // running until told to stop
unsigned long servoSpinStart = 0;
unsigned long servoLastSpinMs = 0;      // what the last free spin measured
bool servoRawRun = false;               // running for a fixed time
unsigned long servoRawStart = 0;
unsigned long servoRawMs = 0;

/* Step test: 0 -> 120 -> 240 -> 360, pausing at each. Three identical 120
   degree legs, so the sweep is one constant rather than a table of targets. */
const float SERVO_SEQ_STEP_DEG = 120.0f;
const int SERVO_SEQ_LEGS = 3;
bool seqActive = false;
bool seqPausing = false;
int seqIndex = 0;
unsigned long seqPauseStart = 0;
unsigned long seqPauseMs = 2000;

long servoCalRev = -1;                  // calibration revision last applied
unsigned long lastCalPoll = 0;

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

/* Distances are kept as individual samples rather than a running sum, because
   this one is reduced with a median instead of a mean. See medianOf(). */
float distSamples[DIST_WINDOW];
int distCount = 0;

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
float pingOnceCm();
float readDistanceCm();
float medianOf(float* v, int n);
void servoWriteUs(int us);
void servoHalt(const char* why);
unsigned long servoMsForSweep(float sweepDeg, int dir);
void servoStartMove(float sweepDeg, int dir, const char* why);
void servoGoTo(float targetDeg);
void serviceServo();
void pollServo();
void pollServoCal();
void serviceBench();
void startRawRun(long signedMs);
void startFreeSpin(int dir);
float normDeg(float d);
void rebuildReverseTable(unsigned long tRev);

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

/* ======================= SERVO ======================= */

float normDeg(float d) {
  while (d < 0) d += 360.0f;
  while (d >= 360.0f) d -= 360.0f;
  return d;
}

/* Writes a pulse width, or does nothing if the servo never attached.

   The no-op case is reported rather than hidden: an attach that fails silently
   is what made the servo look dead with no error anywhere to explain it. */
void servoWriteUs(int us) {
  if (!servoAttached) return;
  servoOut.writeMicroseconds(us);
}

/* Stops the motor wherever it is.

   If this is called mid-move the angle becomes an interpolation of an
   interrupted run, which is a materially weaker claim than a completed move —
   so it is marked uncertain rather than silently kept as if it were solid. */
void servoHalt(const char* why) {
  if (servoMoving) {
    unsigned long elapsed = millis() - servoMoveStart;
    float fraction = servoMoveMs > 0 ? (float)elapsed / (float)servoMoveMs : 0.0f;
    if (fraction > 1.0f) fraction = 1.0f;
    servoDeg = normDeg(servoMoveFrom + servoMoveDir * servoMoveSweep * fraction);
    servoUncertain = true;
    servoMovesSinceZero++;
    servoMoving = false;
    servoReportDue = true;

    Serial.print("Servo: STOPPED mid-move at ~");
    Serial.print(servoDeg, 1);
    Serial.print(" deg  [");
    Serial.print(why);
    Serial.println("] - angle is an estimate now, re-zero when you can");
  }
  servoWriteUs(servoNeutralUs);
}

/* Milliseconds to sweep this many degrees, interpolated piecewise between the
   calibration anchors. Piecewise and not a single ms-per-degree constant
   because the servo slows as a move goes on: one constant puts a 180 degree
   move out by roughly 10 degrees. */
unsigned long servoMsForSweep(float sweepDeg, int dir) {
  const unsigned long* table = (dir > 0) ? servoFwdMs : servoRevMs;
  float s = sweepDeg;
  if (s < 0) s = 0;
  if (s > 360.0f) s = 360.0f;

  for (int i = 1; i < SERVO_ANCHOR_COUNT; i++) {
    float a0 = SERVO_ANCHOR_DEG[i - 1];
    float a1 = SERVO_ANCHOR_DEG[i];
    if (s <= a1) {
      float f = (a1 == a0) ? 0.0f : (s - a0) / (a1 - a0);
      return (unsigned long)(table[i - 1] + f * (float)(table[i] - table[i - 1]));
    }
  }
  return table[SERVO_ANCHOR_COUNT - 1];
}

void servoStartMove(float sweepDeg, int dir, const char* why) {
  if (sweepDeg <= 0.05f) return;  // already there

  unsigned long ms = servoMsForSweep(sweepDeg, dir);
  if (ms == 0) return;
  if (ms > SERVO_MAX_MOVE_MS) {
    // Refusing beats trusting: the failure this guards against is a motor that
    // is told to run and never told to stop.
    Serial.print("Servo: refused a ");
    Serial.print(ms);
    Serial.print(" ms move, over the ");
    Serial.print(SERVO_MAX_MOVE_MS);
    Serial.println(" ms ceiling");
    return;
  }

  servoMoveFrom = servoDeg;
  servoMoveSweep = sweepDeg;
  servoMoveDir = dir;
  servoMoveMs = ms;
  servoMoveStart = millis();
  servoMoving = true;

  servoWriteUs(dir > 0 ? servoForwardUs() : servoReverseUs());

  Serial.print("Servo: ");
  Serial.print(servoDeg, 1);
  Serial.print(" -> ");
  Serial.print(normDeg(servoDeg + dir * sweepDeg), 1);
  Serial.print(" deg, ");
  Serial.print(dir > 0 ? "forward" : "reverse");
  Serial.print(" ");
  Serial.print(sweepDeg, 1);
  Serial.print(" deg in ");
  Serial.print(ms);
  Serial.print(" ms  [");
  Serial.print(why);
  Serial.println("]");
}

/* Shortest path by TIME, not by arc.

   Reverse runs about 9.6% faster than forward on this servo, so the shorter
   way round is not always the quicker one — and the move that finishes sooner
   is also the one with less time to drift. */
void servoGoTo(float targetDeg) {
  float target = normDeg(targetDeg);
  float fwd = normDeg(target - servoDeg);
  float rev = normDeg(servoDeg - target);

  if (fwd < 0.05f) {
    Serial.println("Servo: already there");
    return;
  }

  unsigned long fwdMs = servoMsForSweep(fwd, 1);
  unsigned long revMs = servoMsForSweep(rev, -1);

  if (revMs < fwdMs) servoStartMove(rev, -1, "dashboard");
  else servoStartMove(fwd, 1, "dashboard");
}

/* Ends a move once its clock runs out.

   Called every pass of loop() and never blocks. A blocking 2.3 s move would
   also block enforcePumpLimits(), and a pump safety limit that only runs when
   the servo is idle is not a safety limit. */
void serviceServo() {
  if (!servoMoving) return;

  unsigned long elapsed = millis() - servoMoveStart;
  if (elapsed < servoMoveMs) return;

  servoWriteUs(servoNeutralUs);
  servoDeg = normDeg(servoMoveFrom + servoMoveDir * servoMoveSweep);
  servoMoving = false;
  servoMovesSinceZero++;
  servoReportDue = true;

  Serial.print("Servo: arrived at ~");
  Serial.print(servoDeg, 1);
  Serial.print(" deg (believed, ");
  Serial.print(servoMovesSinceZero);
  Serial.println(" moves since zero)");
}

/* ---- bench modes -------------------------------------------------------
   Driving for a time rather than to an angle. Used only by the calibration
   page, and every one of them leaves the position uncertain, because that is
   the truth: after free-spinning the servo five turns by eye, the node has no
   idea where the horn is. */

void startRawRun(long signedMs) {
  unsigned long ms = (unsigned long)(signedMs < 0 ? -signedMs : signedMs);
  if (ms == 0 || ms > SERVO_MAX_RUN_MS) return;
  servoHalt("superseded by a bench run");
  servoRawRun = true;
  servoRawStart = millis();
  servoRawMs = ms;
  servoUncertain = true;
  servoWriteUs(signedMs < 0 ? servoReverseUs() : servoForwardUs());
  Serial.print("Bench: running ");
  Serial.print(signedMs < 0 ? "reverse " : "forward ");
  Serial.print(ms);
  Serial.println(" ms");
}

void startFreeSpin(int dir) {
  servoHalt("superseded by a free spin");
  servoFreeSpinning = true;
  servoSpinStart = millis();
  servoUncertain = true;
  servoWriteUs(dir < 0 ? servoReverseUs() : servoForwardUs());
  Serial.println("Bench: free spinning - press Stop & measure when you have counted the turns");
}

/* Ends whichever bench mode is running, and steps the sequence test along.
   Non-blocking, same as serviceServo(). */
void serviceBench() {
  if (servoRawRun && millis() - servoRawStart >= servoRawMs) {
    servoRawRun = false;
    servoWriteUs(servoNeutralUs);
    servoReportDue = true;
    Serial.println("Bench: run finished");
  }

  // The step test advances only once the previous leg has actually landed.
  if (seqActive && !servoMoving && !seqPausing) {
    seqIndex++;
    if (seqIndex >= SERVO_SEQ_LEGS) {
      seqActive = false;
      // 360 is the same physical spot as the 0 mark, so the lap ends at zero.
      // Whatever it visibly misses the mark by IS the accumulated error — the
      // whole point of the test — so it is reported, not quietly corrected.
      servoDeg = 0.0f;
      servoReportDue = true;
      Serial.println("Bench: step test done - should be back on the 0 mark");
    } else {
      seqPausing = true;
      seqPauseStart = millis();
    }
  }
  if (seqActive && seqPausing && millis() - seqPauseStart >= seqPauseMs) {
    seqPausing = false;
    servoStartMove(SERVO_SEQ_STEP_DEG, 1, "step test");
  }
}

/* Fetches the calibration the dashboard holds: "rev,stop,speed,t120,t240,t360,tRev".
   Applied only when the revision changes, so this is cheap to poll. */
void pollServoCal() {
  if (WiFi.status() != WL_CONNECTED) return;

  char url[160];
  if (!buildUrl(url, sizeof(url), SERVO_CAL_PATH)) return;

  WiFiClient client;
  HTTPClient http;
  if (!http.begin(client, url)) return;
  http.setTimeout(3000);
  if (strlen(DEVICE_TOKEN) > 0) http.addHeader("x-device-token", DEVICE_TOKEN);

  if (http.GET() == 200) {
    String body = http.getString();
    body.trim();
    long rev = -1;
    int stopUs = 0, speedUs = 0;
    unsigned long a = 0, b = 0, c = 0, r = 0;
    if (sscanf(body.c_str(), "%ld,%d,%d,%lu,%lu,%lu,%lu",
               &rev, &stopUs, &speedUs, &a, &b, &c, &r) == 7 &&
        rev >= 0 && a > 0 && a < b && b < c && r > 0 &&
        stopUs >= 1400 && stopUs <= 1600 && speedUs >= 50 && speedUs <= 500) {
      if (rev != servoCalRev) {
        servoCalRev = rev;
        servoNeutralUs = stopUs;
        servoSpeedOffsetUs = speedUs;
        servoFwdMs[1] = a;
        servoFwdMs[2] = b;
        servoFwdMs[3] = c;
        rebuildReverseTable(r);
        if (!servoMoving && !servoRawRun && !servoFreeSpinning) servoWriteUs(servoNeutralUs);
        Serial.print("Calibration rev ");
        Serial.print(rev);
        Serial.print(": stop ");
        Serial.print(stopUs);
        Serial.print("us, speed +/-");
        Serial.print(speedUs);
        Serial.print("us, ");
        Serial.print(a); Serial.print("/");
        Serial.print(b); Serial.print("/");
        Serial.print(c); Serial.print(" fwd, ");
        Serial.print(r); Serial.println(" rev");
      }
    }
  }
  http.end();
}

/* Asks the dashboard what the servo should be doing.

   The reply is one short line, "<seq>,<letter>,<arg>" — no JSON parser needed,
   same as the relay poll. The seq is what makes a 1 Hz poll safe: the node acts
   only when it changes, so re-reading the same line a thousand times cannot
   re-run the same move a thousand times. Anything that does not parse cleanly
   is discarded, so an error page or a captive portal can never be read as a
   command to run a motor. */
void pollServo() {
  if (WiFi.status() != WL_CONNECTED) return;

  char url[160];
  if (!buildUrl(url, sizeof(url), SERVO_PATH)) return;

  WiFiClient client;
  HTTPClient http;
  if (!http.begin(client, url)) return;
  http.setTimeout(3000);
  if (strlen(DEVICE_TOKEN) > 0) http.addHeader("x-device-token", DEVICE_TOKEN);

  int code = http.GET();
  if (code == 200) {
    String body = http.getString();
    body.trim();

    long seq = -1;
    char cmd = '?';
    float arg = 0;
    if (sscanf(body.c_str(), "%ld,%c,%f", &seq, &cmd, &arg) == 3 && seq >= 0) {
      lastServerContact = millis();

      if (seq != servoSeq) {
        servoSeq = seq;
        switch (cmd) {
          case 'G':
            if (servoMoving) servoHalt("superseded by a new target");
            servoGoTo(arg);
            break;
          case 'S':
            // Stop is also how a free spin is measured: the node reports how
            // long it ran, because network latency would corrupt anything the
            // browser tried to time itself.
            if (servoFreeSpinning) {
              servoLastSpinMs = millis() - servoSpinStart;
              Serial.print("Bench: free spin measured ");
              Serial.print(servoLastSpinMs);
              Serial.println(" ms");
            }
            servoFreeSpinning = false;
            servoRawRun = false;
            seqActive = false;
            seqPausing = false;
            servoHalt("dashboard stop");
            servoWriteUs(servoNeutralUs);
            servoReportDue = true;
            break;

          case 'R':
            startRawRun((long)arg);
            break;

          case 'P':
            startFreeSpin(arg < 0 ? -1 : 1);
            break;

          case 'A':
            // Test one calibration point. The mark is assumed to be at 0, so
            // the position is declared zero first and the move measured from
            // there — which is exactly how the timings were calibrated.
            if (servoMoving) servoHalt("superseded by an angle test");
            servoDeg = 0.0f;
            servoUncertain = false;
            servoStartMove(normDeg(arg) == 0 ? 360.0f : normDeg(arg), 1, "angle test");
            break;

          case 'Q':
            if (servoMoving) servoHalt("superseded by the step test");
            seqPauseMs = (unsigned long)(arg < 0 ? 0 : (arg > 30000 ? 30000 : arg));
            servoDeg = 0.0f;
            servoUncertain = false;
            seqActive = true;
            seqIndex = 0;
            seqPausing = false;
            servoStartMove(SERVO_SEQ_STEP_DEG, 1, "step test");
            break;
          case 'Z':
            // Re-zero. Declares the current physical position to be 0 without
            // moving anything, and clears the accumulated doubt with it —
            // which is the only thing that ever resets the drift.
            if (servoMoving) servoHalt("re-zeroed mid-move");
            servoDeg = 0.0f;
            servoMovesSinceZero = 0;
            servoUncertain = false;
            servoReportDue = true;
            Serial.println("Servo: re-zeroed here, drift count cleared");
            break;
          case 'T':
            if (servoMoving) servoHalt("superseded by a turn");
            servoStartMove(360.0f, arg < 0 ? -1 : 1, "full turn");
            break;
          default:
            Serial.print("Servo poll: unknown command '");
            Serial.print(cmd);
            Serial.println("' - ignored");
            break;
        }
      }
    } else {
      Serial.print("Servo poll: unparseable body \"");
      Serial.print(body);
      Serial.println("\" - ignored");
    }
  } else if (code == 401) {
    Serial.println("Servo poll: 401 - DEVICE_TOKEN does not match the server's.");
  }
  http.end();
}

/* ======================= TANK LEVEL ======================= */

/* Median of a small set, sorted in place.

   Deliberately a median and not a mean. An ultrasonic reliably returns the odd
   wildly wrong distance — an echo off a tank wall, a ladder rung, or the
   ripple the pump itself makes — and one outlier is enough to drag a mean by
   several centimetres, which shows up on the dashboard as the tank level
   jumping a few percent for no reason. A median discards it outright.

   Insertion sort because n is five. */
float medianOf(float* v, int n) {
  for (int i = 1; i < n; i++) {
    float key = v[i];
    int j = i - 1;
    while (j >= 0 && v[j] > key) {
      v[j + 1] = v[j];
      j--;
    }
    v[j + 1] = key;
  }
  return (n % 2) ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2.0f;
}

/* One ping. Returns a negative number when nothing came back in time.

   -1 rather than 0, because 0 cm is a distance: treating "no echo" as zero
   would report a tank filled to the sensor face, which is the single most
   alarming reading this node can produce. */
float pingOnceCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  unsigned long us = pulseIn(ECHO_PIN, HIGH, PING_TIMEOUT_US);
  if (us == 0) return -1.0f;
  // Sound travels ~343 m/s, and the pulse makes the trip twice: us / 58 is the
  // one-way distance in centimetres.
  return us / 58.0f;
}

/* A burst of pings reduced to one distance, or -1 if nothing answered.

   pulseIn() blocks, so this is the slowest thing in the loop. Bailing after two
   consecutive misses keeps a disconnected sensor cheap (~170 ms) instead of
   standing in the full burst (~425 ms) every two seconds — and everything that
   makes a pump safe, the run limit and the comms failsafe, is checked in that
   same loop. */
float readDistanceCm() {
  float samples[PINGS_PER_SAMPLE];
  int got = 0;
  int misses = 0;

  for (int i = 0; i < PINGS_PER_SAMPLE; i++) {
    if (i > 0) delay(PING_SETTLE_MS);
    float cm = pingOnceCm();
    if (cm < 0) {
      if (++misses >= 2) break;
      continue;
    }
    misses = 0;
    samples[got++] = cm;
  }

  if (got == 0) return -1.0f;
  return medianOf(samples, got);
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
  bool ads_was = adsPresent, ds_was = dsPresent, sonar_was = sonarPresent;

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

  // "Present" here means it answered, which for an ultrasonic is the only
  // test there is: it has no ID to read back and no bus to enumerate.
  float probe = readDistanceCm();
  sonarPresent = probe > 0;
  if (verbose || sonarPresent != sonar_was) {
    Serial.print("AJ-SR04M : ");
    if (sonarPresent) {
      Serial.print("answering, ");
      Serial.print(probe, 1);
      Serial.println(" cm to the surface");
    } else {
      Serial.println("NO ECHO");
      Serial.println("           -> check TRIG on GPIO16 and ECHO through the 1k/2k divider");
      Serial.println("              to GPIO17, that it has 5V, and the jumper on the board:");
      Serial.println("              in UART mode it never answers Trig/Echo timing at all.");
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

  Serial.print("Servo    : GPIO ");
  Serial.print(SERVO_PIN);
  if (!servoAttached) {
    Serial.println("  ** ATTACH FAILED - no pulses, the servo cannot move **");
    Serial.println("           All 4 LEDC timers are in use, or the pin cannot do PWM.");
  } else {
    Serial.print(", drive ");
    Serial.print(servoForwardUs());
    Serial.print("/");
    Serial.print(servoReverseUs());
    Serial.println(" us, stopped, believed at 0 deg (nothing measures this)");

    /* A short twitch, before Wi-Fi. Two things are worth proving on their own,
       because together they are indistinguishable from each other: that the
       signal path works at all, and that the dashboard can reach the node. If
       the horn does not move here, nothing on the dashboard will fix it. */
    Serial.println("           self-test: brief twitch...");
    servoWriteUs(servoForwardUs());
    delay(180);
    servoWriteUs(servoReverseUs());
    delay(180);
    servoWriteUs(servoNeutralUs);
    Serial.println("           if the horn did not move, it is wiring or power, not the network");
  }

  Serial.print("Pumps    : GPIO ");
  Serial.print(RELAY_PINS[0]);
  Serial.print(" and GPIO ");
  Serial.print(RELAY_PINS[1]);
  Serial.print(", active-");
  Serial.print(RELAY_ACTIVE_LOW ? "LOW" : "HIGH");
  Serial.println(", both off");

  /* Servo to neutral immediately, for the same reason the relays go first: on
     a continuous-rotation servo a floating signal pin during boot can be read
     as "run", and a motor that starts itself on every reset is the kind of
     fault you chase for a day. */
  ESP32PWM::allocateTimer(0);
  servoOut.setPeriodHertz(SERVO_FREQ_HZ);
  servoAttached = servoOut.attach(SERVO_PIN, 500, 2500) != 0;
  servoWriteUs(servoNeutralUs);

  // Trig must idle low, or the first ping reads whatever the pin was doing.
  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  pinMode(ECHO_PIN, INPUT);

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

  // --- Tank level ---
  // Read before the ADS1115 block, because that block returns early when the
  // ADC is missing — and the ultrasonic is on its own pins, so it has no
  // reason to go quiet just because the I2C probes did.
  float distCm = readDistanceCm();
  bool distOk = distCm > 0;
  sonarPresent = distOk;
  if (distOk && distCount < DIST_WINDOW) distSamples[distCount++] = distCm;

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

  Serial.print("  |  Servo ");
  Serial.print(servoDeg, 1);
  Serial.print(servoMoving ? " deg>" : (servoUncertain ? " deg~" : " deg "));

  Serial.print(" |  Tank ");
  if (!distOk) {
    Serial.print("no echo");
  } else {
    Serial.print(distCm, 1);
    Serial.print(" cm");
    // The blind zone is the failure people hit first, and it looks like a
    // working sensor until you notice the number stopped moving.
    if (distCm < 20.0f) Serial.print(" <-in the ~20cm blind zone!");
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
  bool haveDist = distCount > 0;

  float avgTemp = haveTemp ? (tempSum / tempCount) : 0.0f;
  float avgPh = havePh ? (phSum / phCount) : 0.0f;
  float avgTds = haveTds ? (tdsSum / tdsCount) : 0.0f;
  float avgTurb = haveTurb ? (turbSum / turbCount) : 0.0f;
  int avgRaw = haveTurb ? (int)(turbRawSum / turbCount) : 0;
  // Median of the window, not a mean — same reason as within a single burst.
  float medDist = haveDist ? medianOf(distSamples, distCount) : 0.0f;

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
  distCount = 0;

  connectWiFi();
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Upload skipped: no WiFi");
    return;
  }

  // A probe that is not answering is sent as null, so the dashboard can name
  // which sensor is down instead of guessing from silence. Never send 0 for a
  // dead probe: 0 is a value, and 0 ppm or 0 NTU reads as very clean water.
  char tempField[24], phField[24], tdsField[24], turbField[24], distField[24];
  if (haveTemp) snprintf(tempField, sizeof(tempField), "%.2f", avgTemp);
  else strcpy(tempField, "null");
  if (havePh) snprintf(phField, sizeof(phField), "%.4f", avgPh);
  else strcpy(phField, "null");
  if (haveTds) snprintf(tdsField, sizeof(tdsField), "%.4f", avgTds);
  else strcpy(tdsField, "null");
  if (haveTurb) snprintf(turbField, sizeof(turbField), "%.3f", avgTurb);
  else strcpy(turbField, "null");
  // Raw centimetres to the water surface. The tank's own geometry — how far
  // down "full" and "empty" are — lives in the dashboard's config, so the tank
  // can be re-measured or the sensor remounted without reflashing this node.
  if (haveDist) snprintf(distField, sizeof(distField), "%.1f", medDist);
  else strcpy(distField, "null");

  // Interpolated while a move is running, so the dashboard sees the horn cross
  // the dial rather than teleport between two settled angles.
  float reportDeg = servoDeg;
  if (servoMoving && servoMoveMs > 0) {
    float f = (float)(millis() - servoMoveStart) / (float)servoMoveMs;
    if (f > 1.0f) f = 1.0f;
    reportDeg = normDeg(servoMoveFrom + servoMoveDir * servoMoveSweep * f);
  }

  /* A servo that never attached has no position worth reporting. Sent as null
     rather than 0.0 for the same reason a dead probe is: 0 is a value, and the
     dashboard would draw a confident needle for a motor that cannot turn. */
  char servoField[24];
  if (servoAttached) snprintf(servoField, sizeof(servoField), "%.1f", reportDeg);
  else strcpy(servoField, "null");

  char body[700];
  snprintf(body, sizeof(body),
           "{\"device_id\":\"%s\",\"temp_c\":%s,\"ph_v\":%s,\"tds_v\":%s,"
           "\"turbidity_v\":%s,\"distance_cm\":%s,\"raw\":%d,"
           "\"relay1\":%d,\"relay2\":%d,"
           "\"servo_deg\":%s,\"servo_moving\":%d,\"servo_moves\":%d,"
           "\"servo_uncertain\":%d,\"servo_ack\":%ld,\"servo_spin_ms\":%lu,"
           "\"rssi\":%d,\"uptime_ms\":%lu,\"reset_reason\":\"%s\",\"heap\":%lu}",
           DEVICE_ID, tempField, phField, tdsField, turbField, distField, avgRaw,
           relayOn[0] ? 1 : 0, relayOn[1] ? 1 : 0,
           servoField, servoMoving ? 1 : 0, servoMovesSinceZero,
           servoUncertain ? 1 : 0, servoSeq, servoLastSpinMs,
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

  // Before the network calls, and never blocking: a move that overruns because
  // a poll was slow is a move that lands in the wrong place.
  serviceServo();
  serviceBench();

  if (nowMs - lastRelayPoll >= RELAY_POLL_MS) {
    lastRelayPoll = nowMs;
    pollRelays();
  }

  if (nowMs - lastServoPoll >= SERVO_POLL_MS) {
    lastServoPoll = nowMs;
    pollServo();
  }

  if (lastCalPoll == 0 || nowMs - lastCalPoll >= SERVO_CAL_POLL_MS) {
    lastCalPoll = nowMs;
    pollServoCal();
  }

  /* A finished move uploads straight away rather than waiting out the rest of
     the post interval. Otherwise a 2 s move is followed by 8 s of the
     dashboard showing the old angle, which reads as a dead feed. */
  if (servoReportDue && !servoMoving) {
    servoReportDue = false;
    lastPost = nowMs;
    uploadReading();
  }

  if (nowMs - lastSample >= SAMPLE_INTERVAL_MS) {
    lastSample = nowMs;
    sampleSensors();
  }

  // Pick up a probe that was plugged in (or fell out) after boot.
  if (nowMs - lastRescan >= RESCAN_INTERVAL_MS) {
    lastRescan = nowMs;
    if (!adsPresent || !dsPresent || !sonarPresent) scanSensors(false);
  }

  if (nowMs - lastPost >= POST_INTERVAL_MS) {
    lastPost = nowMs;
    uploadReading();
  }

  delay(20);
}

/*
  What to expect:
  - Serial prints a live five-sensor line every 2 s, and one POST line every 10 s.
  - The dashboard's Live Monitoring page updates within a second of each POST.
  - "-> 200 {"ok":true,...}" means it landed.
  - Pressing Start on the dashboard clicks the relay within about a second, and
    the button only says "Running" once this node has confirmed it in a payload.
  - A missing probe still uploads, as null, and the dashboard names it as
    "not detected" rather than showing the node as offline.
  - Servo angle is marked ~ until you re-zero it, and > while it is moving.
    After any reset it is ~ by definition: the node cannot know where the horn
    physically is, only how long it has run the motor since you last told it.
  - Tank distance falls as the tank fills. If it reads "no echo" with the sensor
    wired, run firmware/ultrasonic_test first: it does nothing but ping, so it
    tells you whether the problem is the sensor or everything around it.
*/
