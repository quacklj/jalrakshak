"use client";

import { useSyncExternalStore } from "react";

const noop = () => () => {};

/** Shows the exact endpoint this deployment listens on, plus a smoke-test call. */
export default function IngestHelp({ compact = false }: { compact?: boolean }) {
  // window.location is client-only; this keeps the server render matching.
  const origin = useSyncExternalStore(
    noop,
    () => window.location.origin,
    () => "http://localhost:3000",
  );

  const url = `${origin}/api/ingest`;

  return (
    <div>
      <div style={{ fontSize: 12.5, color: "var(--muted)", marginBottom: 10, lineHeight: 1.6 }}>
        Point the ESP32 at <code className="inline">{url}</code> and POST one JSON object per
        reading. Nothing else needs configuring.
      </div>
      <pre className="block">
{`curl -X POST ${url} \\
  -H 'content-type: application/json' \\
  -d '{"device_id":"ESP32-JR01","temp_c":26.94,"turbidity_v":4.05}'`}
      </pre>
      {!compact && (
        <div style={{ fontSize: 11.5, color: "var(--muted-2)", marginTop: 10, lineHeight: 1.6 }}>
          Fields: <code className="inline">temp_c</code> °C from the DS18B20 (send −127 or omit when
          no probe answers), <code className="inline">turbidity_v</code> the probe&apos;s own output
          in volts with the 10k/15k divider already undone. Optional:{" "}
          <code className="inline">raw</code>, <code className="inline">rssi</code>,{" "}
          <code className="inline">uptime_ms</code>.
        </div>
      )}
    </div>
  );
}
