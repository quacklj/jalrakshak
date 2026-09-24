"use client";

import { useEffect, useState } from "react";
import type { ServoView } from "@/lib/types";
import { NavIcon } from "./icons";

/**
 * Read-only servo readout for the Overview page.
 *
 * Deliberately has no controls. Overview is a glance screen — five sensor
 * cards, a risk dial and the pump buttons already — and a rotary actuator is
 * not something to nudge in passing from a summary. The controls live on Live
 * Monitoring, next to the chart that shows what the move did.
 */
export default function ServoStatus() {
  const [servo, setServo] = useState<ServoView | null>(null);
  const [thresholds, setThresholds] = useState({ watch: 8, warning: 20 });

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch("/api/servo", { cache: "no-store" });
        const json = (await res.json()) as {
          servo: ServoView;
          driftWatch: number;
          driftWarning: number;
        };
        if (cancelled) return;
        setServo(json.servo);
        setThresholds({ watch: json.driftWatch, warning: json.driftWarning });
      } catch {
        /* the next tick tries again */
      }
    };
    void load();
    const id = setInterval(load, 3000);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  const drift = servo?.movesSinceZero ?? null;
  const bad = servo?.uncertain === true || (drift !== null && drift >= thresholds.warning);
  const watch = !bad && drift !== null && drift >= thresholds.watch;
  const tone = servo?.moving
    ? { c: "var(--accent)", bg: "var(--accent-tint)" }
    : bad
      ? { c: "var(--critical)", bg: "var(--critical-bg)" }
      : watch
        ? { c: "var(--watch)", bg: "var(--watch-bg)" }
        : { c: "var(--safe)", bg: "var(--safe-bg)" };

  return (
    <div className="card card-pad">
      <div className="eyebrow">Servo positioner</div>
      <div
        className="figure"
        style={{ marginTop: 8, color: servo?.actualDeg === null ? "var(--muted)" : tone.c }}
      >
        {servo?.actualDeg == null ? "—" : servo.actualDeg.toFixed(1)}
        <span style={{ fontSize: 15, color: "var(--muted-2)", marginLeft: 4 }}>°</span>
      </div>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 6,
          fontSize: 12,
          color: bad || watch ? tone.c : "var(--muted)",
          marginTop: 6,
          fontWeight: bad || watch ? 600 : 400,
        }}
      >
        <NavIcon id="servo" size={13} />
        {servo?.moving
          ? "moving"
          : servo?.uncertain
            ? "stopped mid-move · re-zero"
            : drift === null
              ? "not reported"
              : `believed · ${drift} move${drift === 1 ? "" : "s"} since zero`}
      </div>
    </div>
  );
}
