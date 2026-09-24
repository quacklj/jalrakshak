/*
  ESP32 WiFi Servo Controller - MG996R 360 (continuous rotation)
  ---------------------------------------------------------------
  Time-based positioning, calibrated at 2080 ms per revolution (+ direction).

  Default timing (0 -> angle, + direction):
      120 deg =  693 ms
      240 deg = 1387 ms
      360 deg = 2080 ms
  Each point can be fine-tuned from the web page and is saved in flash.
  Any other angle is worked out from these three calibration points.

  Library: "ESP32Servo" by Kevin Harrington
  Phone: WiFi "ESP32-Servo" (pass 12345678) -> http://192.168.4.1

  Wiring:
    Signal (orange) -> GPIO 13
    Red  (+)        -> external 5-6V, 2A+
    Brown (-)       -> external supply (-) AND ESP32 GND
*/

#include <WiFi.h>
#include <WebServer.h>
#include <ESP32Servo.h>
#include <Preferences.h>

const char* AP_SSID     = "ESP32-Servo";
const char* AP_PASSWORD = "12345678";
const int   SERVO_PIN   = 16;

// Bump this number to force the timing defaults below into flash again
const int CAL_VERSION = 2;

// ---------- Timing defaults (from your 2080 ms test) ----------
const unsigned long DEF_T120 = 693;
const unsigned long DEF_T240 = 1387;
const unsigned long DEF_T360 = 2080;
const unsigned long DEF_TREV = 2080;   // full turn in - direction (test and adjust)

WebServer   server(80);
Servo       myServo;
Preferences prefs;

// ---------- Calibration ----------
int stopPulse   = 1500;
int speedOffset = 300;
unsigned long t120 = DEF_T120, t240 = DEF_T240, t360 = DEF_T360;
unsigned long tRev = DEF_TREV;

// ---------- Movement state ----------
float currentAngle = 0, targetAngle = 0, moveStartAngle = 0;
bool  moving = false;
int   moveDir = 0;
unsigned long moveStart = 0, moveDuration = 0;

// ---------- Calibration runs ----------
bool spinning = false;  unsigned long spinStart = 0;
bool testing  = false;  unsigned long testStart = 0, testMs = 0;

// ---------- Step test: 0 -> 120 -> 240 -> 360 with pauses ----------
const float SEQ_TARGETS[] = {120, 240, 360};
const int   SEQ_COUNT = 3;
bool seqActive = false, seqPausing = false;
int  seqIndex = 0;
unsigned long seqPauseStart = 0, seqPauseMs = 2000;

// ---------------------------------------------------------------
void drive(int dir) {
  if (dir > 0)      myServo.writeMicroseconds(stopPulse + speedOffset);
  else if (dir < 0) myServo.writeMicroseconds(stopPulse - speedOffset);
  else              myServo.writeMicroseconds(stopPulse);
}

// ms needed to go from 0 to angle a (+ direction), using the 3 calibration points
float timeAt(float a) {
  a = constrain(a, 0, 360);
  if (a <= 120) return a / 120.0 * t120;
  if (a <= 240) return t120 + (a - 120) / 120.0 * (t240 - t120);
  return t240 + (a - 240) / 120.0 * (t360 - t240);
}

// inverse: angle reached after t ms from 0 (+ direction)
float angleAt(float t) {
  if (t <= 0)    return 0;
  if (t <= t120) return t / t120 * 120.0;
  if (t <= t240) return 120 + (t - t120) / (float)(t240 - t120) * 120.0;
  if (t <= t360) return 240 + (t - t240) / (float)(t360 - t240) * 120.0;
  return 360;
}

// - direction runs at a slightly different speed; scale by its full-turn time
float revScale() { return (float)tRev / (float)t360; }

float estimateAngle() {
  if (!moving) return currentAngle;
  unsigned long elapsed = millis() - moveStart;
  if (elapsed > moveDuration) elapsed = moveDuration;
  float a;
  if (moveDir > 0) a = angleAt(timeAt(moveStartAngle) + elapsed);
  else             a = angleAt(timeAt(moveStartAngle) - elapsed / revScale());
  return constrain(a, 0, 360);
}

void haltAll() {
  if (moving) { currentAngle = estimateAngle(); moving = false; }
  spinning = false;
  testing = false;
  seqActive = false;
  seqPausing = false;
  drive(0);
}

// start a move without cancelling a running step test
void startMove(float target) {
  target = constrain(target, 0, 360);
  targetAngle = target;
  float diff = target - currentAngle;
  if (fabs(diff) < 0.5) return;

  moveDir = diff > 0 ? 1 : -1;
  if (moveDir > 0) moveDuration = (unsigned long)(timeAt(target) - timeAt(currentAngle));
  else             moveDuration = (unsigned long)((timeAt(currentAngle) - timeAt(target)) * revScale());

  moveStartAngle = currentAngle;
  moveStart = millis();
  moving = true;
  drive(moveDir);
  Serial.printf("Move %.0f -> %.0f  (%lu ms)\n", currentAngle, target, moveDuration);
}

void moveTo(float target) { haltAll(); startMove(target); }

// ---------------------------------------------------------------
void saveCal() {
  prefs.begin("servo", false);
  prefs.putInt("ver", CAL_VERSION);
  prefs.putInt("stop", stopPulse);
  prefs.putInt("speed", speedOffset);
  prefs.putULong("t120", t120);
  prefs.putULong("t240", t240);
  prefs.putULong("t360", t360);
  prefs.putULong("rev", tRev);
  prefs.end();
}

void loadCal() {
  prefs.begin("servo", true);
  int ver     = prefs.getInt("ver", 0);
  stopPulse   = prefs.getInt("stop", 1500);     // keeps your earlier stop calibration
  speedOffset = prefs.getInt("speed", 300);     // keeps your earlier speed
  t120 = prefs.getULong("t120", DEF_T120);
  t240 = prefs.getULong("t240", DEF_T240);
  t360 = prefs.getULong("t360", DEF_T360);
  tRev = prefs.getULong("rev",  DEF_TREV);
  prefs.end();

  if (ver != CAL_VERSION) {                     // first boot with this version
    t120 = DEF_T120; t240 = DEF_T240; t360 = DEF_T360; tRev = DEF_TREV;
    saveCal();
    Serial.println("Timing defaults (2080 ms/rev) written to flash");
  }
}

// ------------------------- Web page -------------------------
const char PAGE[] PROGMEM = R"rawliteral(
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ESP32 Servo</title>
<style>
 body{font-family:Arial,sans-serif;background:#1e1e2e;color:#fff;margin:0;padding:12px;text-align:center}
 .card{background:#2a2a3d;border-radius:12px;padding:14px;margin:12px auto;max-width:480px}
 h3{margin:4px 0 10px}
 .big{font-size:48px;font-weight:bold;color:#4fc3f7}
 .small{color:#aaa;font-size:14px}
 input[type=range]{width:95%;height:36px}
 input[type=number]{width:90px;font-size:18px;padding:6px;border-radius:6px;border:none;text-align:center}
 button{font-size:16px;padding:10px 12px;margin:4px;border:none;border-radius:8px;background:#4fc3f7;color:#000}
 button.red{background:#ff5252;color:#fff} button.green{background:#66bb6a} button.big2{font-size:20px;padding:14px 18px}
 button:active{opacity:.6}
 .dial{width:160px;height:160px;border-radius:50%;border:5px solid #4fc3f7;margin:8px auto;position:relative}
 .needle{position:absolute;width:4px;height:72px;background:#ff5252;left:78px;top:8px;transform-origin:2px 72px}
 .step{text-align:left;border-top:1px solid #444;padding-top:10px;margin-top:10px}
 .row{display:flex;align-items:center;justify-content:space-between;margin:6px 0}
 .row b{width:60px}
 #msg{color:#ffb74d;min-height:18px}
</style></head><body>

<div class="card">
 <h3>MG996R 360 Control</h3>
 <div class="dial"><div class="needle" id="needle"></div></div>
 <div class="big"><span id="cur">0</span>&deg;</div>
 <div class="small">Target: <span id="tgt">0</span>&deg; &nbsp;|&nbsp; <span id="st">idle</span></div>
 <div>
  <button class="big2" onclick="go(0)">0&deg;</button>
  <button class="big2" onclick="go(120)">120&deg;</button>
  <button class="big2" onclick="go(240)">240&deg;</button>
  <button class="big2" onclick="go(360)">360&deg;</button>
 </div>
 <input type="range" id="slider" min="0" max="360" value="0" onchange="go(this.value)">
 <div>
  <input type="number" id="num" min="0" max="360" value="0">
  <button onclick="go(g('num').value)">Go</button>
 </div>
 <div>
  <button class="red" onclick="api('/stop')">Pause</button>
  <button class="green" onclick="api('/resume')">Resume</button>
  <button onclick="api('/zero')">Set here = 0&deg;</button>
 </div>
</div>

<div class="card">
 <h3>Angle timing (+ direction)</h3>
 <div class="small" style="text-align:left">Put the mark at 0 and press <b>Set here = 0&deg;</b>.
 Then press Test. Too far &rarr; lower the ms. Too short &rarr; raise it.
 Test always starts from the 0 mark.</div>

 <div class="row"><b>120&deg;</b><input type="number" id="t120"> ms
  <button onclick="testAngle(120)">Test</button></div>
 <div class="row"><b>240&deg;</b><input type="number" id="t240"> ms
  <button onclick="testAngle(240)">Test</button></div>
 <div class="row"><b>360&deg;</b><input type="number" id="t360"> ms
  <button onclick="testAngle(360)">Test</button></div>

 <div class="step"><b>Step test</b> 0&rarr;120&rarr;240&rarr;360 with a pause at each<br>
  Pause: <input type="number" id="pause" value="2000"> ms
  <button class="green" onclick="saveCal().then(()=>api('/seq?pause='+g('pause').value))">Run step test</button>
 </div>

 <div class="step"><b>Reverse ( &minus; ) direction</b><br>
  Full turn: <input type="number" id="rev"> ms
  <button onclick="saveCal().then(()=>api('/test?dir=-1&ms='+g('rev').value))">Test 1 rev &minus;</button>
 </div>

 <div id="msg"></div>
 <button class="green" onclick="saveCal()">Save timing</button>
 <button onclick="if(confirm('Reset timing to 693 / 1387 / 2080 ms?'))api('/reset').then(()=>{loaded=false})">Reset to 2080 defaults</button>
</div>

<div class="card">
 <h3>Motor calibration</h3>
 <div class="step"><b>Stop pulse</b> <span class="small">(servo must stand completely still)</span><br>
  <input type="range" id="stopP" min="1400" max="1600" oninput="g('stopV').innerText=this.value" onchange="saveCal()">
  <div>Stop pulse: <b id="stopV">1500</b> us</div>
 </div>
 <div class="step"><b>Speed offset</b> <span class="small">(changing this changes all timings!)</span><br>
  <input type="number" id="speed"> us <button onclick="saveCal()">Save</button>
 </div>
 <div class="step"><b>Measure revolution</b> <span class="small">(stopwatch, counts N turns)</span><br>
  N: <input type="number" id="revs" value="5">
  <button onclick="api('/spin?dir=1')">Spin +</button>
  <button onclick="api('/spin?dir=-1')">Spin &minus;</button>
  <button class="red" onclick="measure()">Stop &amp; measure</button>
  <div>Measured: <b id="meas">-</b> ms per rev</div>
  <button onclick="useFwd()">Use for + (rescales 120/240/360)</button>
  <button onclick="useRev()">Use for &minus;</button>
 </div>
</div>

<script>
 let loaded=false, lastMeas=0;
 function g(id){return document.getElementById(id)}
 function api(u){return fetch(u).then(r=>r.text())}
 function msg(t){g('msg').innerText=t;setTimeout(()=>g('msg').innerText='',3000)}
 function go(a){a=Math.max(0,Math.min(360,+a||0));g('slider').value=a;g('num').value=a;api('/set?angle='+a)}
 function saveCal(){
  return api('/cal?stop='+g('stopP').value+'&speed='+g('speed').value+
   '&t120='+g('t120').value+'&t240='+g('t240').value+'&t360='+g('t360').value+
   '&rev='+g('rev').value).then(t=>{msg(t);return t});
 }
 function testAngle(a){ saveCal().then(t=>{ if(t=='Saved') api('/testangle?a='+a) }) }
 function measure(){
  api('/stop').then(t=>{ let ms=+t,n=+g('revs').value||1;
   if(ms>0){lastMeas=Math.round(ms/n);g('meas').innerText=lastMeas;} });
 }
 function useFwd(){
  if(lastMeas<=0)return;
  let k=lastMeas/(+g('t360').value);
  g('t120').value=Math.round(g('t120').value*k);
  g('t240').value=Math.round(g('t240').value*k);
  g('t360').value=lastMeas; saveCal();
 }
 function useRev(){ if(lastMeas>0){g('rev').value=lastMeas;saveCal();} }
 function refresh(){
  fetch('/status').then(r=>r.json()).then(s=>{
   g('cur').innerText=s.angle.toFixed(0);
   g('tgt').innerText=s.target.toFixed(0);
   g('st').innerText=s.state;
   g('needle').style.transform='rotate('+s.angle+'deg)';
   if(!loaded){
    g('stopP').value=s.stop;g('stopV').innerText=s.stop;g('speed').value=s.speed;
    g('t120').value=s.t120;g('t240').value=s.t240;g('t360').value=s.t360;g('rev').value=s.rev;
    g('slider').value=s.target;g('num').value=s.target.toFixed(0);
    loaded=true;
   }
  }).catch(e=>{});
 }
 setInterval(refresh,300); refresh();
</script>
</body></html>
)rawliteral";

// ------------------------- Handlers -------------------------
void handleRoot() { server.send_P(200, "text/html", PAGE); }

void handleSet() {
  if (!server.hasArg("angle")) { server.send(400, "text/plain", "Missing angle"); return; }
  moveTo(server.arg("angle").toFloat());
  server.send(200, "text/plain", "OK");
}

void handleStop() {
  unsigned long spinMs = spinning ? millis() - spinStart : 0;
  haltAll();
  server.send(200, "text/plain", String(spinMs));
}

void handleResume() { moveTo(targetAngle); server.send(200, "text/plain", "OK"); }

void handleZero() {
  haltAll();
  currentAngle = 0; targetAngle = 0;
  server.send(200, "text/plain", "OK");
}

void handleSpin() {
  int dir = server.arg("dir").toInt() >= 0 ? 1 : -1;
  haltAll();
  spinning = true; spinStart = millis();
  drive(dir);
  server.send(200, "text/plain", "OK");
}

void handleTest() {
  int dir = server.arg("dir").toInt() >= 0 ? 1 : -1;
  unsigned long ms = server.arg("ms").toInt();
  haltAll();
  if (ms > 0 && ms < 60000) {
    testing = true; testStart = millis(); testMs = ms;
    drive(dir);
  }
  server.send(200, "text/plain", "OK");
}

// Test one calibration point: assumes the mark is at 0, runs to that angle
void handleTestAngle() {
  float a = server.arg("a").toFloat();
  haltAll();
  currentAngle = 0;
  startMove(a);
  server.send(200, "text/plain", "OK");
}

void handleSeq() {
  if (server.hasArg("pause")) seqPauseMs = constrain(server.arg("pause").toInt(), 0, 30000);
  haltAll();
  currentAngle = 0;          // assumes the mark is at 0
  seqActive = true;
  seqIndex = 0;
  seqPausing = false;
  startMove(SEQ_TARGETS[0]);
  server.send(200, "text/plain", "OK");
}

void handleCal() {
  haltAll();
  int newStop = server.hasArg("stop") ? server.arg("stop").toInt() : stopPulse;
  int newSpeed = server.hasArg("speed") ? server.arg("speed").toInt() : speedOffset;
  unsigned long n120 = server.hasArg("t120") ? server.arg("t120").toInt() : t120;
  unsigned long n240 = server.hasArg("t240") ? server.arg("t240").toInt() : t240;
  unsigned long n360 = server.hasArg("t360") ? server.arg("t360").toInt() : t360;
  unsigned long nRev = server.hasArg("rev")  ? server.arg("rev").toInt()  : tRev;

  if (!(n120 > 0 && n120 < n240 && n240 < n360 && nRev > 0)) {
    server.send(200, "text/plain", "Not saved: times must increase 120 < 240 < 360");
    return;
  }
  stopPulse = constrain(newStop, 1000, 2000);
  speedOffset = constrain(newSpeed, -1000, 1000);
  t120 = n120; t240 = n240; t360 = n360; tRev = nRev;
  saveCal();
  drive(0);
  Serial.printf("Saved: stop=%d speed=%d  120=%lu 240=%lu 360=%lu rev=%lu\n",
                stopPulse, speedOffset, t120, t240, t360, tRev);
  server.send(200, "text/plain", "Saved");
}

void handleReset() {
  haltAll();
  t120 = DEF_T120; t240 = DEF_T240; t360 = DEF_T360; tRev = DEF_TREV;
  saveCal();
  server.send(200, "text/plain", "Reset");
}

void handleStatus() {
  String state = "idle";
  if (seqActive && seqPausing) state = "step test: paused at " + String((int)currentAngle) + "\xC2\xB0";
  else if (seqActive)          state = "step test: moving";
  else if (moving)             state = "moving";
  else if (spinning)           state = "spinning (measuring)";
  else if (testing)            state = "testing";
  else if (fabs(targetAngle - currentAngle) > 0.5) state = "paused";

  String json = "{";
  json += "\"angle\":"   + String(estimateAngle(), 1);
  json += ",\"target\":" + String(targetAngle, 1);
  json += ",\"state\":\"" + state + "\"";
  json += ",\"stop\":"   + String(stopPulse);
  json += ",\"speed\":"  + String(speedOffset);
  json += ",\"t120\":"   + String(t120);
  json += ",\"t240\":"   + String(t240);
  json += ",\"t360\":"   + String(t360);
  json += ",\"rev\":"    + String(tRev);
  json += "}";
  server.send(200, "application/json", json);
}

// ------------------------- Setup / Loop -------------------------
void setup() {
  Serial.begin(115200);
  loadCal();

  ESP32PWM::allocateTimer(0);
  myServo.setPeriodHertz(50);
  myServo.attach(SERVO_PIN, 500, 2500);
  drive(0);

  WiFi.softAP(AP_SSID, AP_PASSWORD);
  Serial.print("\nConnect to WiFi: "); Serial.println(AP_SSID);
  Serial.print("Open: http://");      Serial.println(WiFi.softAPIP());
  Serial.printf("Timing: 120=%lu 240=%lu 360=%lu rev=%lu ms\n", t120, t240, t360, tRev);

  server.on("/",          handleRoot);
  server.on("/set",       handleSet);
  server.on("/stop",      handleStop);
  server.on("/resume",    handleResume);
  server.on("/zero",      handleZero);
  server.on("/spin",      handleSpin);
  server.on("/test",      handleTest);
  server.on("/testangle", handleTestAngle);
  server.on("/seq",       handleSeq);
  server.on("/cal",       handleCal);
  server.on("/reset",     handleReset);
  server.on("/status",    handleStatus);
  server.begin();
}

void loop() {
  server.handleClient();

  // finish a timed move
  if (moving && millis() - moveStart >= moveDuration) {
    drive(0);
    currentAngle = targetAngle;
    moving = false;
    Serial.printf("Reached %.0f\n", currentAngle);
  }

  // finish a fixed-time test run
  if (testing && millis() - testStart >= testMs) {
    drive(0);
    testing = false;
  }

  // step test: 0 -> 120 -> pause -> 240 -> pause -> 360
  if (seqActive && !moving && !seqPausing) {
    seqIndex++;
    if (seqIndex >= SEQ_COUNT) {
      seqActive = false;
      currentAngle = 0;          // 360 is the same spot as the 0 mark
      targetAngle = 0;
      Serial.println("Step test done - back at 0 mark");
    } else {
      seqPausing = true;
      seqPauseStart = millis();
    }
  }
  if (seqActive && seqPausing && millis() - seqPauseStart >= seqPauseMs) {
    seqPausing = false;
    startMove(SEQ_TARGETS[seqIndex]);
  }
}
