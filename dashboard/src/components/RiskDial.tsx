import { bandColor } from "@/lib/bandStyle";
import { bandOfScore } from "@/lib/config";

/** Composite-risk ring, ported from the design's riskDial(). */
export default function RiskDial({ score, size = 58 }: { score: number; size?: number }) {
  const sw = size > 80 ? 7 : 5;
  const r = (size - sw) / 2;
  const c = 2 * Math.PI * r;
  const color = bandColor[bandOfScore(score)];
  const dash = (Math.max(0, Math.min(100, score)) / 100) * c;

  return (
    <div
      style={{
        position: "relative",
        width: size,
        height: size,
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
      }}
    >
      <svg width={size} height={size} style={{ transform: "rotate(-90deg)" }}>
        <circle cx={size / 2} cy={size / 2} r={r} stroke="var(--surface-2)" strokeWidth={sw} fill="none" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke={color}
          strokeWidth={sw}
          fill="none"
          strokeLinecap={dash > 0.5 ? "round" : "butt"}
          strokeDasharray={`${dash} ${c}`}
          style={{ transition: "stroke-dasharray .4s ease, stroke .4s ease" }}
        />
      </svg>
      <div
        style={{
          position: "absolute",
          inset: 0,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <div
          style={{
            fontSize: size * 0.34,
            lineHeight: 1,
            letterSpacing: "-0.03em",
            fontWeight: 500,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {score}
        </div>
        <div
          style={{
            fontSize: Math.max(7, size * 0.14),
            letterSpacing: ".08em",
            textTransform: "uppercase",
            color: "var(--muted-2)",
            marginTop: 1,
          }}
        >
          risk
        </div>
      </div>
    </div>
  );
}
