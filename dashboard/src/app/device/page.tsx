"use client";

import ConnectionBanner from "@/components/ConnectionBanner";
import IngestHelp from "@/components/IngestHelp";
import SensorStatus from "@/components/SensorStatus";
import { useLive } from "@/components/useLive";
import { deviceDot, deviceLabel } from "@/lib/bandStyle";
import {
  PH_NEUTRAL_V,
  PH_VOLTS_PER_UNIT,
  PUMP_COMMS_FAILSAFE_MS,
  PUMP_MAX_RUN_MS,
  RELAYS,
  SENSORS,
  SENSOR_ORDER,
  TDS_K,
  TURBIDITY_CLEAR_V,
  TURBIDITY_MAX_NTU,
  TURBIDITY_OPAQUE_V,
} from "@/lib/config";
import {
  ageLabel,
  connectivityTimeline,
  deviceStateFor,
  medianPeriodMs,
  rebootStats,
  uptimePct,
} from "@/lib/derive";

export default function DevicePage() {
  const { readings, now } = useLive(24 * 60 * 60 * 1000, 2000);
  const latest = readings.length ? readings[readings.length - 1] : null;
  const clock = now;
  const state = deviceStateFor(latest, clock);
  const timeline = connectivityTimeline(readings, clock);
  const period = medianPeriodMs(readings);
  const reboots = rebootStats(readings);

  const facts: [string, string][] = [
    ["Device ID", latest?.deviceId ?? "—"],
    ["Status", deviceLabel[state]],
    ["Last payload", latest ? ageLabel(latest.t, clock) : "never"],
    ["Reporting cadence", period ? `${Math.round(period / 1000)} s` : "—"],
    ["Uptime (hours observed)", `${uptimePct(timeline)}%`],
    ["Restarts in window", reboots.count ? `${reboots.count}` : "none"],
    ["Last reset reason", reboots.lastReason ?? "not reported"],
    ["Wi-Fi RSSI", latest?.rssi != null ? `${latest.rssi} dBm` : "not reported"],
    ["Free heap", latest?.heap != null ? `${(latest.heap / 1024).toFixed(0)} kB` : "not reported"],
    [
      "Device uptime",
      latest?.uptimeMs != null ? `${Math.floor(latest.uptimeMs / 60000)} min` : "not reported",
    ],
    ["Stored readings", String(readings.length)],
  ];

  return (
    <div className="page-inner">
      <div className="h2">Device</div>
      <div className="subtle" style={{ marginTop: 8, marginBottom: 20 }}>
        ESP32-S3 field node · DS18B20 + pH, TDS and turbidity over ADS1115 · 2 pump relays
      </div>

      <ConnectionBanner readings={readings} deviceState={state} now={clock} />

      {reboots.count > 2 && (
        <div
          style={{
            display: "flex",
            gap: 12,
            alignItems: "flex-start",
            background: "var(--critical-bg)",
            border: "1px solid transparent",
            borderRadius: "var(--radius-md)",
            padding: "13px 16px",
            marginBottom: 16,
          }}
        >
          <span style={{ color: "var(--critical)", fontSize: 15, lineHeight: 1.2 }}>⟳</span>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: "var(--critical)" }}>
              Node restarted {reboots.count} times in this window
            </div>
            <div style={{ fontSize: 12, color: "var(--ink-2)", marginTop: 3, lineHeight: 1.55 }}>
              {reboots.medianUptimeS !== null &&
                `It survives about ${reboots.medianUptimeS}s between resets. `}
              {reboots.lastReason === "brownout"
                ? "The firmware reports brownout: the 3.3V rail is collapsing, which is a power-supply problem — try another USB data cable and a port directly on the computer."
                : reboots.lastReason
                  ? `Firmware reports the last reset as "${reboots.lastReason}".`
                  : "Re-upload the sketch to have the board report why it is resetting."}{" "}
              Each reset also re-enumerates the USB port, which is why the Serial Monitor keeps
              going blank.
            </div>
          </div>
        </div>
      )}

      <div style={{ marginBottom: 16 }}>
        <SensorStatus
          readings={readings}
          deviceState={state}
          now={clock}
          title="Sensor connection"
        />
      </div>

      <div
        className="grid"
        style={{ gridTemplateColumns: "minmax(0,1fr) minmax(0,1fr)", alignItems: "start" }}
      >
        <div className="card card-pad">
          <div className="row" style={{ marginBottom: 16 }}>
            <span
              style={{
                width: 10,
                height: 10,
                borderRadius: "50%",
                background: deviceDot[state],
              }}
            />
            <div className="mono" style={{ fontSize: 15, fontWeight: 500 }}>
              {latest?.deviceId ?? "no device yet"}
            </div>
          </div>
          <table>
            <tbody>
              {facts.map(([k, v]) => (
                <tr key={k}>
                  <td style={{ color: "var(--muted)", padding: "9px 0" }}>{k}</td>
                  <td className="mono num" style={{ padding: "9px 0", fontWeight: 500 }}>
                    {v}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="card card-pad">
          <div className="eyebrow" style={{ marginBottom: 14 }}>
            Connectivity · last 24h
          </div>
          <div style={{ display: "flex", gap: 3, height: 44 }}>
            {timeline.map((b, i) => (
              <div
                key={i}
                title={b.label}
                style={{
                  flex: 1,
                  borderRadius: 2,
                  background:
                    b.state === "online"
                      ? "var(--safe)"
                      : b.state === "degraded"
                        ? "var(--watch)"
                        : "var(--surface)",
                  border: b.state === "offline" ? "1px solid var(--border)" : "none",
                }}
              />
            ))}
          </div>
          <div
            className="mono"
            style={{
              display: "flex",
              justifyContent: "space-between",
              fontSize: 10,
              color: "var(--muted-2)",
              marginTop: 7,
            }}
          >
            <span>-24h</span>
            <span>now</span>
          </div>
          <div style={{ display: "flex", gap: 14, fontSize: 10.5, color: "var(--muted)", marginTop: 14 }}>
            {[
              ["Online", "var(--safe)"],
              ["Partial", "var(--watch)"],
              ["No data", "var(--surface)"],
            ].map(([label, color]) => (
              <span key={label} className="row" style={{ gap: 5 }}>
                <span
                  style={{
                    width: 9,
                    height: 9,
                    borderRadius: 2,
                    background: color,
                    border: color === "var(--surface)" ? "1px solid var(--border)" : "none",
                  }}
                />
                {label}
              </span>
            ))}
          </div>
        </div>
      </div>

      <div className="eyebrow" style={{ margin: "28px 0 12px" }}>
        Pin map
      </div>
      <div className="card" style={{ overflow: "hidden" }}>
        <div className="scroll-x">
          <table style={{ minWidth: 560 }}>
            <thead>
              <tr>
                <th>Signal</th>
                <th>Connection</th>
                <th>Notes</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td style={{ fontWeight: 600 }}>DS18B20</td>
                <td className="mono">GPIO 4</td>
                <td style={{ color: "var(--muted)" }}>1-Wire · needs a 4.7k pull-up to 3.3V</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600 }}>ADS1115</td>
                <td className="mono">SDA 8 · SCL 9 · 0x48</td>
                <td style={{ color: "var(--muted)" }}>ADDR tied to GND</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600 }}>pH AOUT</td>
                <td className="mono">ADS1115 A0</td>
                <td style={{ color: "var(--muted)" }}>direct, no divider</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600 }}>TDS AOUT</td>
                <td className="mono">ADS1115 A1</td>
                <td style={{ color: "var(--muted)" }}>direct, no divider</td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600 }}>Turbidity AOUT</td>
                <td className="mono">ADS1115 A2</td>
                <td style={{ color: "var(--muted)" }}>via 10k/15k divider (ratio 0.6)</td>
              </tr>
              {RELAYS.map((r) => (
                <tr key={r.id}>
                  <td style={{ fontWeight: 600 }}>{r.name} relay</td>
                  <td className="mono">GPIO {r.pin}</td>
                  <td style={{ color: "var(--muted)" }}>
                    active-low module · off at boot and on reset
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="eyebrow" style={{ margin: "28px 0 12px" }}>
        Sensor thresholds
      </div>
      <div className="card" style={{ overflow: "hidden" }}>
        <div className="scroll-x">
          <table style={{ minWidth: 620 }}>
            <thead>
              <tr>
                <th>Sensor</th>
                <th>Source</th>
                <th className="num">Safe</th>
                <th className="num">Watch to</th>
                <th className="num">Warning to</th>
                <th>Unit</th>
              </tr>
            </thead>
            <tbody>
              {SENSOR_ORDER.map((key) => {
                const s = SENSORS[key];
                return (
                  <tr key={key}>
                    <td style={{ fontWeight: 600 }}>{s.name}</td>
                    <td style={{ color: "var(--muted)" }}>{s.blurb}</td>
                    <td className="mono num">
                      {s.safe[0]}–{s.safe[1]}
                    </td>
                    <td className="mono num">
                      {s.watch[0]}–{s.watch[1]}
                    </td>
                    <td className="mono num">
                      {s.warning[0]}–{s.warning[1]}
                    </td>
                    <td style={{ color: "var(--muted-2)" }}>{s.unit}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
      <div style={{ fontSize: 11.5, color: "var(--muted-2)", marginTop: 9, lineHeight: 1.55 }}>
        Anything outside the warning range counts as critical. Edit these in{" "}
        <code className="inline">src/lib/config.ts</code>.
      </div>

      <div className="eyebrow" style={{ margin: "28px 0 12px" }}>
        Probe calibration
      </div>
      <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fit,minmax(280px,1fr))" }}>
        <div className="card card-pad">
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>pH</div>
          <div style={{ fontSize: 12.5, color: "var(--ink-2)", lineHeight: 1.65 }}>
            Two anchors: <strong>{PH_NEUTRAL_V} V</strong> is treated as pH 7.00 and one pH unit is
            worth <strong>{PH_VOLTS_PER_UNIT} V</strong>, falling as the water gets alkaline. Dip
            the probe in pH 7.00 buffer, read the volts on Live Monitoring, and put that in{" "}
            <code className="inline">PH_NEUTRAL_V</code>. Repeat in pH 4.00 buffer for the slope:
            it is the difference between the two voltages divided by three.
          </div>
          <div
            style={{
              fontSize: 11.5,
              color: "var(--ink-2)",
              background: "var(--watch-bg)",
              borderRadius: 10,
              padding: "9px 11px",
              marginTop: 10,
              lineHeight: 1.55,
            }}
          >
            Check the pH board&apos;s supply before you trust a high reading. Powered from 5V its
            output can exceed the ADS1115&apos;s own 3.3V rail, which both clips the value and
            stresses the ADC input.
          </div>
        </div>
        <div className="card card-pad">
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>TDS</div>
          <div style={{ fontSize: 12.5, color: "var(--ink-2)", lineHeight: 1.65 }}>
            Parts per million come from the probe&apos;s published cubic, with the reading first
            compensated for temperature using the DS18B20 — conductivity rises about 2% per °C, so
            the same water reads higher when warm. That also means a dead temperature probe
            quietly degrades this one. Scale <code className="inline">TDS_K</code> (currently{" "}
            <strong>{TDS_K}</strong>) against a solution of known ppm.
          </div>
        </div>
        <div className="card card-pad">
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>Turbidity</div>
          <div style={{ fontSize: 12.5, color: "var(--ink-2)", lineHeight: 1.65 }}>
            NTU is mapped linearly from probe voltage: <strong>{TURBIDITY_CLEAR_V} V</strong> is
            treated as 0 NTU (clear water) and <strong>{TURBIDITY_OPAQUE_V} V</strong> as{" "}
            <strong>{TURBIDITY_MAX_NTU} NTU</strong>. Drop the probe in clean water, read the volts
            shown on Live Monitoring, and put that number into{" "}
            <code className="inline">TURBIDITY_CLEAR_V</code> — the readings stay honest as long as
            those two anchors match your sensor.
          </div>
        </div>
      </div>
      <div style={{ fontSize: 11.5, color: "var(--muted-2)", marginTop: 9, lineHeight: 1.55 }}>
        All three are estimates until you calibrate them. Until then the Live page&apos;s{" "}
        <strong>Volts</strong> switch shows the raw probe output, which is the number to read while
        adjusting these constants.
      </div>

      <div className="eyebrow" style={{ margin: "28px 0 12px" }}>
        Pump safety
      </div>
      <div className="card card-pad">
        <div style={{ fontSize: 12.5, color: "var(--ink-2)", lineHeight: 1.65 }}>
          No float switch is wired, so nothing physical stops a pump once it starts. Three
          independent limits stand in for one:{" "}
          <strong>{Math.round(PUMP_MAX_RUN_MS / 60000)} minutes</strong> maximum run, enforced by
          the server and again by the firmware&apos;s own clock; both relays switched off by the
          node if it cannot reach the dashboard for{" "}
          <strong>{Math.round(PUMP_COMMS_FAILSAFE_MS / 1000)} seconds</strong>; and relays driven
          off before the pin is made an output, so a reset cannot pulse a motor. Wire a float
          switch in series with the pump before this runs unattended.
        </div>
      </div>

      <div className="eyebrow" style={{ margin: "28px 0 12px" }}>
        Ingest endpoint
      </div>
      <div className="card card-pad">
        <IngestHelp />
      </div>
    </div>
  );
}
