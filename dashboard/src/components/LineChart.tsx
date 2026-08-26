"use client";

import { useMemo, useRef, useState } from "react";
import { bandColor } from "@/lib/bandStyle";
import type { SensorSpec } from "@/lib/config";
import type { Band } from "@/lib/types";

export type Point = { t: number; v: number | null };

/**
 * SVG line chart carried over from the design's sensorChart(): shaded normal
 * band, dashed stroke while a reading drifts, filled area once it goes
 * critical. Gaps (null temperature, or a device dropout) break the line
 * instead of being interpolated across.
 */
export default function LineChart({
  data,
  spec,
  band,
  height = 104,
  axes = false,
}: {
  data: Point[];
  spec: SensorSpec;
  band: Band;
  height?: number;
  axes?: boolean;
}) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [hover, setHover] = useState<number | null>(null);

  const W = 640;
  const H = height;
  const pT = 10;
  const pB = axes ? 22 : 14;
  const pL = axes ? 40 : 4;
  const pR = 6;

  const { lo, hi } = useMemo(() => {
    const vals = data.map((d) => d.v).filter((v): v is number => v !== null);
    let lo = spec.extent[0];
    let hi = spec.extent[1];
    if (vals.length) {
      // Zoom to the data, but never crop the safe band out of view.
      const dMin = Math.min(...vals, spec.safe[0]);
      const dMax = Math.max(...vals, spec.safe[1]);
      const pad = Math.max((dMax - dMin) * 0.15, (spec.safe[1] - spec.safe[0]) * 0.1, 0.5);
      lo = Math.max(spec.extent[0], dMin - pad);
      hi = Math.min(spec.extent[1], dMax + pad);
      if (hi - lo < 1e-6) hi = lo + 1;
    }
    return { lo, hi };
  }, [data, spec]);

  const n = data.length;
  const x = (i: number) => (n <= 1 ? pL : pL + (i / (n - 1)) * (W - pL - pR));
  const y = (v: number) => pT + (1 - (Math.max(lo, Math.min(hi, v)) - lo) / (hi - lo)) * (H - pT - pB);

  // Contiguous runs of real samples, so dropouts leave a visible gap.
  const segments = useMemo(() => {
    const out: { i: number; v: number }[][] = [];
    let cur: { i: number; v: number }[] = [];
    data.forEach((d, i) => {
      if (d.v === null) {
        if (cur.length) out.push(cur);
        cur = [];
      } else cur.push({ i, v: d.v });
    });
    if (cur.length) out.push(cur);
    return out;
  }, [data]);

  const color = bandColor[band];
  const dash = band === "watch" ? "6 4" : band === "warning" ? "3 4" : undefined;
  const bandTop = y(Math.min(hi, spec.safe[1]));
  const bandBottom = y(Math.max(lo, spec.safe[0]));

  const hovered = hover !== null ? data[hover] : null;
  const fmt = (v: number) => v.toFixed(spec.decimals);

  return (
    <div style={{ position: "relative" }}>
      <svg
        ref={svgRef}
        viewBox={`0 0 ${W} ${H}`}
        width="100%"
        style={{ display: "block", touchAction: "none" }}
        onPointerMove={(e) => {
          if (n < 2) return;
          const rect = svgRef.current!.getBoundingClientRect();
          const rel = ((e.clientX - rect.left) / rect.width) * W;
          const idx = Math.round(((rel - pL) / (W - pL - pR)) * (n - 1));
          setHover(Math.max(0, Math.min(n - 1, idx)));
        }}
        onPointerLeave={() => setHover(null)}
      >
        <rect
          x={pL}
          y={bandTop}
          width={W - pL - pR}
          height={Math.max(0, bandBottom - bandTop)}
          fill="var(--surface-2)"
          stroke="var(--border)"
          strokeDasharray="3 3"
        />
        <line x1={pL} y1={H - pB} x2={W - pR} y2={H - pB} stroke="var(--border)" />

        {axes && (
          <>
            {[hi, (hi + lo) / 2, lo].map((v, i) => (
              <text
                key={i}
                x={pL - 8}
                y={y(v) + 3.5}
                textAnchor="end"
                fontSize={10}
                fill="var(--muted-2)"
              >
                {fmt(v)}
              </text>
            ))}
            {n > 1 && (
              <>
                <text x={pL} y={H - 6} fontSize={10} fill="var(--muted-2)">
                  {new Date(data[0].t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </text>
                <text x={W - pR} y={H - 6} fontSize={10} fill="var(--muted-2)" textAnchor="end">
                  now
                </text>
              </>
            )}
          </>
        )}

        {band === "critical" &&
          segments.map((seg, k) => (
            <polygon
              key={`a${k}`}
              points={`${x(seg[0].i)},${H - pB} ${seg
                .map((p) => `${x(p.i).toFixed(1)},${y(p.v).toFixed(1)}`)
                .join(" ")} ${x(seg[seg.length - 1].i)},${H - pB}`}
              fill={color}
              opacity={0.16}
            />
          ))}

        {segments.map((seg, k) =>
          seg.length === 1 ? (
            <circle key={`p${k}`} cx={x(seg[0].i)} cy={y(seg[0].v)} r={2.4} fill={color} />
          ) : (
            <polyline
              key={`l${k}`}
              points={seg.map((p) => `${x(p.i).toFixed(1)},${y(p.v).toFixed(1)}`).join(" ")}
              fill="none"
              stroke={color}
              strokeWidth={band === "critical" ? 2.4 : 2}
              strokeDasharray={dash}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          ),
        )}

        {hovered && hovered.v !== null && (
          <g>
            <line
              x1={x(hover!)}
              y1={pT}
              x2={x(hover!)}
              y2={H - pB}
              stroke="var(--muted-2)"
              strokeWidth={1}
              strokeDasharray="2 3"
            />
            <circle cx={x(hover!)} cy={y(hovered.v)} r={3.4} fill={color} stroke="#fff" strokeWidth={1.6} />
          </g>
        )}
      </svg>

      {hovered && (
        <div
          className="mono"
          style={{
            position: "absolute",
            top: 0,
            right: 0,
            fontSize: 11,
            background: "var(--surface)",
            border: "1px solid var(--border)",
            borderRadius: 8,
            padding: "3px 8px",
            color: "var(--ink-2)",
            pointerEvents: "none",
          }}
        >
          {hovered.v === null ? "no reading" : `${fmt(hovered.v)} ${spec.unit}`}
          <span style={{ color: "var(--muted-2)", marginLeft: 6 }}>
            {new Date(hovered.t).toLocaleTimeString()}
          </span>
        </div>
      )}
    </div>
  );
}
