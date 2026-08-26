"use client";

import type { SensorSpec } from "@/lib/config";
import type { SensorHealth } from "@/lib/derive";
import type { Band } from "@/lib/types";
import LineChart, { type Point } from "./LineChart";
import StatusPill from "./StatusPill";

export default function SensorCard({
  spec,
  data,
  value,
  band,
  health,
  secondary,
  height = 104,
  axes = false,
}: {
  spec: SensorSpec;
  data: Point[];
  value: number | null;
  band: Band;
  /** When present and not ok, the card reports the fault instead of a status. */
  health?: SensorHealth;
  /** Extra readout under the headline figure, e.g. the raw probe voltage. */
  secondary?: string;
  height?: number;
  axes?: boolean;
}) {
  const faulted = health !== undefined && !health.ok;
  const hasSamples = data.some((d) => d.v !== null);

  return (
    <div
      className="card"
      style={{
        padding: "16px 18px",
        // Fault state is worth seeing from across the room.
        borderColor: faulted ? "var(--critical)" : undefined,
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "baseline",
          justifyContent: "space-between",
          gap: 12,
          marginBottom: 4,
        }}
      >
        <div style={{ fontSize: 13, fontWeight: 600 }}>{spec.name}</div>
        <div
          className="mono"
          style={{ fontSize: 15, fontWeight: 500, color: faulted ? "var(--muted-2)" : undefined }}
        >
          {value === null ? "—" : value.toFixed(spec.decimals)}
          {value !== null && (
            <span style={{ color: "var(--muted-2)", fontSize: 11, marginLeft: 3 }}>{spec.unit}</span>
          )}
        </div>
      </div>

      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 7,
          fontSize: 11,
          marginBottom: 10,
          flexWrap: "wrap",
        }}
      >
        {faulted ? (
          <span
            className="pill"
            style={{ color: "var(--critical)", background: "var(--critical-bg)", fontSize: 11 }}
          >
            <svg
              width={11}
              height={11}
              viewBox="0 0 16 16"
              fill="none"
              stroke="currentColor"
              strokeWidth={1.8}
            >
              <circle cx={8} cy={8} r={6.2} />
              <path d="M5.6 5.6l4.8 4.8" strokeLinecap="round" />
            </svg>
            {health.label}
          </span>
        ) : (
          <StatusPill band={band} size={11} />
        )}
        <span className="mono" style={{ color: "var(--muted-2)" }}>
          normal {spec.safe[0]}–{spec.safe[1]} {spec.unit}
        </span>
        {secondary && (
          <span className="mono" style={{ color: "var(--muted-2)", marginLeft: "auto" }}>
            {secondary}
          </span>
        )}
      </div>

      {faulted && (
        <div
          style={{
            fontSize: 11.5,
            color: "var(--ink-2)",
            background: "var(--critical-bg)",
            borderRadius: 10,
            padding: "9px 11px",
            marginBottom: 10,
            lineHeight: 1.5,
          }}
        >
          {health.detail}
        </div>
      )}

      {hasSamples ? (
        <LineChart data={data} spec={spec} band={band} height={height} axes={axes} />
      ) : (
        <div
          style={{
            height,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "var(--muted-2)",
            fontSize: 12,
            border: "1px dashed var(--border)",
            borderRadius: 12,
          }}
        >
          {faulted ? "nothing to chart while the sensor is not reading" : "no samples in this window"}
        </div>
      )}
    </div>
  );
}
