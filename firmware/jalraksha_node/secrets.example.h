/*
  Copy this file to `secrets.h` in the same folder and fill it in.

  `secrets.h` is git-ignored. Keeping your Wi-Fi password out of the sketch is
  what lets this repository stay public — the ESP32 is on your home network, and
  a pushed password is a pushed password even after you delete the commit.

  The sketch compiles without secrets.h, using the placeholders at the top of
  jalraksha_node.ino, so a fresh clone still builds.
*/
#pragma once

#define JR_WIFI_SSID     "your-wifi-name"
#define JR_WIFI_PASSWORD "your-wifi-password"

// Must be reachable FROM THE ESP32, so never "localhost".
// Find your laptop's address with:  ipconfig getifaddr en0
#define JR_SERVER_HOST   "http://192.168.1.10:3000"

// Leave empty unless you set DEVICE_TOKEN in the dashboard's environment.
#define JR_DEVICE_TOKEN  ""
