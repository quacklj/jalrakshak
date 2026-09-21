/*
  AJ-SR04M Ultrasonic Test — ESP32-S3
  ------------------------------------
  Wiring:
    AJ-SR04M VCC  -> 5V
    AJ-SR04M GND  -> common GND
    AJ-SR04M TRIG -> GPIO 16   (direct, no divider needed)
    AJ-SR04M ECHO -> [1k] -> node -> [2k] -> GND
                              node -> GPIO 17

  No extra library needed - this uses plain pulseIn() timing, same as any
  standard HC-SR04-style sensor (AJ-SR04M is electrically compatible in
  its default Trig/Echo mode).

  If you get no readings at all (or constant 0 / very odd numbers), check
  the small jumper on the board - AJ-SR04M can be switched into a UART
  mode that doesn't work with this Trig/Echo code.

  Same pins as the real node, so whatever you confirm here carries straight
  over to firmware/jalraksha_node.
*/

#define TRIG_PIN 16
#define ECHO_PIN 17

void setup() {
  Serial.begin(115200);
  while (!Serial) delay(10);

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);

  Serial.println();
  Serial.println("=== Ultrasonic (AJ-SR04M) test starting ===");
  Serial.println("Reading distance every 500ms...");
  Serial.println();
}

void loop() {
  // --- Send a 10us trigger pulse ---
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  // --- Measure how long Echo stays HIGH (round-trip time) ---
  // 30000us timeout ~= 5m range, well beyond what these sensors do anyway
  long duration = pulseIn(ECHO_PIN, HIGH, 30000);

  if (duration == 0) {
    Serial.println("No echo received - check wiring or the sensor's mode jumper.");
  } else {
    // Speed of sound ~343 m/s -> distance(cm) = duration(us) / 58, round trip
    float distanceCm = duration / 58.0;
    float distanceIn = distanceCm / 2.54;

    Serial.print("Distance: ");
    Serial.print(distanceCm, 1);
    Serial.print(" cm   (");
    Serial.print(distanceIn, 1);
    Serial.print(" in)   raw echo time: ");
    Serial.print(duration);
    Serial.println(" us");

    // The blind zone is the one number worth checking against while you decide
    // where to mount this. See the note below.
    if (distanceCm < 20.0) {
      Serial.println("   ^ inside the ~20cm blind zone - treat this number as meaningless.");
    }
  }

  delay(500);
}

/*
  What to expect while testing:
  - Point it at a flat surface (a wall, a book, your hand) a known distance
    away and confirm the number roughly matches reality.
  - Soft, angled or sound-absorbing surfaces can give unreliable or missing
    readings - that's normal ultrasonic sensor behavior, not necessarily a
    wiring problem.
  - CLOSE RANGE IS DIFFERENT ON THIS SENSOR. An HC-SR04 reads from about 2cm;
    the AJ-SR04M has a blind zone of roughly 20cm and returns nothing, or a
    wrong number, inside it. Hold it against your hand and you will not see
    "2 cm" - you will see junk. Check your own board's spec sheet, since the
    figure varies between 20 and 25cm across versions.
  - Once mounted above Tank 1, "distance" here is distance to the water
    surface - lower number as the tank fills, higher number as it drains.

  MOUNTING, AND THE TWO NUMBERS THE DASHBOARD NEEDS
  - Mount the sensor so a completely full tank is still more than the blind
    zone below it. Get this wrong and the level reads fine until the tank
    fills, then drops out exactly when you most want it.
  - Then measure, from the sensor face:
        full  -> the water line when the tank is full
        empty -> the tank floor
    Those two go into TANK_FULL_DISTANCE_CM and TANK_EMPTY_DISTANCE_CM in
    dashboard/src/lib/config.ts. The node only ever sends centimetres; the
    dashboard turns them into a percentage, so re-measuring the tank or
    remounting the sensor never means reflashing the ESP32.
*/
