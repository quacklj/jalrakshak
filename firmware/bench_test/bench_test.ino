#include <FastLED.h>

#define LED_PIN     27
#define NUM_LEDS    300
#define BRIGHTNESS  150
#define LED_TYPE    WS2812B
#define COLOR_ORDER GRB

CRGB leds[NUM_LEDS];

int position = 0;

void setup() {
  FastLED.addLeds<LED_TYPE, LED_PIN, COLOR_ORDER>(leds, NUM_LEDS);
  FastLED.setBrightness(BRIGHTNESS);
  FastLED.clear();
  FastLED.show();
}

void loop() {

  // Turn everything off
  fill_solid(leds, NUM_LEDS, CRGB::Black);

  // Draw the rainbow train
  for (int i = 0; i < NUM_LEDS; i++) {

    // Rainbow color based on position
    leds[i] = CHSV(
      (position + i * 255 / NUM_LEDS) & 255,
      255,
      255
    );
  }

  FastLED.show();

  // Move the rainbow
  position++;

  if (position >= 256) {
    position = 0;
  }

  delay(20);
}
