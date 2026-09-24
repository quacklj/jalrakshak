"use client";

import { SERVO_POSITIONS } from "@/lib/config";

/**
 * The servo drawn as a ring, matching RiskDial and the conic gauges elsewhere.
 *
 * Three different claims are on this dial at once and they are drawn
 * differently on purpose, because conflating them is exactly the mistake this
 * hardware invites:
 *
 *   solid needle   where the node believes it is
 *   hollow marker  where it has been asked to go
 *   arc            the path it will take, and which way round
 *
 * None of it is measured. There is no encoder on this servo.
 */

const R = 78;
const CX = 100;
const CY = 100;

/** 0° at twelve o'clock, increasing clockwise — how a dial is read. */
function point(deg: number, radius: number) {
  const rad = ((deg - 90) * Math.PI) / 180;
  return { x: CX + radius * Math.cos(rad), y: CY + radius * Math.sin(rad) };
}

function arcPath(fromDeg: number, sweepDeg: number, dir: 1 | -1, radius: number): string {
  const sweep = Math.min(359.9, Math.abs(sweepDeg));
  const a = point(fromDeg, radius);
  const b = point(fromDeg + dir * sweep, radius);
  const largeArc = sweep > 180 ? 1 : 0;
  // In SVG's y-down space a clockwise arc is sweep-flag 1.
  const sweepFlag = dir === 1 ? 1 : 0;
  return `M ${a.x} ${a.y} A ${radius} ${radius} 0 ${largeArc} ${sweepFlag} ${b.x} ${b.y}`;
}

export default function ServoDial({
  deg,
  targetDeg,
  planDir,
  planSweep,
  progress,
  tone,
  size = 190,
}: {
  /** Believed angle, or null when the node has never reported one. */
  deg: number | null;
  targetDeg: number | null;
  planDir: 1 | -1 | null;
  planSweep: number | null;
  /** 0–1 through a predicted move, or null when not moving. */
  progress: number | null;
  tone: string;
  size?: number;
}) {
  const known = deg !== null;
  const shown = deg ?? 0;
  const needle = point(shown, R - 12);
  const hub = point(shown, 16);

  return (
    <svg width={size} height={size} viewBox="0 0 200 200" aria-hidden>
      <circle cx={CX} cy={CY} r={R} fill="none" stroke="var(--border)" strokeWidth={10} />

      {/* Where it will travel, and which way round. */}
      {known && planDir !== null && planSweep !== null && planSweep > 0 && (
        <path
          d={arcPath(shown, planSweep, planDir, R)}
          fill="none"
          stroke={tone}
          strokeWidth={10}
          strokeLinecap="round"
          opacity={progress === null ? 0.28 : 0.5}
        />
      )}

      {/* How far through that travel we predict it is. */}
      {known && planDir !== null && planSweep !== null && progress !== null && progress > 0 && (
        <path
          d={arcPath(shown, planSweep * progress, planDir, R)}
          fill="none"
          stroke={tone}
          strokeWidth={10}
          strokeLinecap="round"
        />
      )}

      {/* Detents at the named positions. */}
      {SERVO_POSITIONS.map((p) => {
        const outer = point(p.deg, R + 9);
        const inner = point(p.deg, R - 9);
        return (
          <line
            key={p.deg}
            x1={outer.x}
            y1={outer.y}
            x2={inner.x}
            y2={inner.y}
            stroke="var(--muted-2)"
            strokeWidth={1.6}
            opacity={0.6}
          />
        );
      })}

      {/* Target: hollow, because it is a request and not yet a fact. */}
      {targetDeg !== null && (
        <circle
          cx={point(targetDeg, R).x}
          cy={point(targetDeg, R).y}
          r={7}
          fill="var(--surface)"
          stroke={tone}
          strokeWidth={3}
        />
      )}

      {known ? (
        <>
          <line
            x1={hub.x}
            y1={hub.y}
            x2={needle.x}
            y2={needle.y}
            stroke={tone}
            strokeWidth={4}
            strokeLinecap="round"
          />
          <circle cx={CX} cy={CY} r={7} fill={tone} />
        </>
      ) : (
        <circle cx={CX} cy={CY} r={7} fill="var(--muted-2)" />
      )}
    </svg>
  );
}
