/*
  Pump relay test — ESP32-S3.  No Wi-Fi, no sensors, no dashboard.
  Just clicks the two relays so you can confirm wiring and polarity.

  WIRING       Relay IN1 -> GPIO 14        Relay IN2 -> GPIO 18
               Relay VCC -> 5V             Relay GND -> common with ESP32 GND

  RUN IT WITH THE PUMPS UNPLUGGED THE FIRST TIME.
  Listen for the clicks and match them to the Serial output.

    Clicks line up with "ON"      -> RELAY_ACTIVE_LOW is correct
    Clicks are inverted           -> change RELAY_ACTIVE_LOW to false
    Relays click at boot/reset    -> also wrong, flip it
    No clicks at all              -> relay board has no 5V, or GND is not shared
*/

// Most cheap relay boards energise on a LOW input. Flip if yours is inverted.
const bool RELAY_ACTIVE_LOW = true;

const int PUMP1_PIN = 14;
const int PUMP2_PIN = 18;

const unsigned long ON_MS  = 3000;   // how long each pump runs
const unsigned long OFF_MS = 2000;   // gap between them

void setRelay(int pin, bool on) {
  int level = (on == RELAY_ACTIVE_LOW) ? LOW : HIGH;
  // Written before pinMode so the pin never floats into an accidental pulse.
  digitalWrite(pin, level);
  pinMode(pin, OUTPUT);
  digitalWrite(pin, level);
}

void setup() {
  setRelay(PUMP1_PIN, false);
  setRelay(PUMP2_PIN, false);

  Serial.begin(115200);
  unsigned long t = millis();
  while (!Serial && millis() - t < 3000) delay(10);

  Serial.println();
  Serial.println("=== Pump relay test ===");
  Serial.print("Pump 1 on GPIO ");
  Serial.print(PUMP1_PIN);
  Serial.print(", Pump 2 on GPIO ");
  Serial.println(PUMP2_PIN);
  Serial.print("Board treated as active-");
  Serial.println(RELAY_ACTIVE_LOW ? "LOW" : "HIGH");
  Serial.println("Both OFF. Starting in 3s - you should hear NO clicks until then.");
  Serial.println();
  delay(3000);
}

void loop() {
  Serial.println("Pump 1 ON");
  setRelay(PUMP1_PIN, true);
  delay(ON_MS);

  Serial.println("Pump 1 OFF");
  setRelay(PUMP1_PIN, false);
  delay(OFF_MS);

  Serial.println("Pump 2 ON");
  setRelay(PUMP2_PIN, true);
  delay(ON_MS);

  Serial.println("Pump 2 OFF");
  setRelay(PUMP2_PIN, false);
  delay(OFF_MS);

  Serial.println("--- cycle done ---");
}
