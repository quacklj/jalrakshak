import type { Band } from "@/lib/types";

const stroke = {
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.6,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
};

export function NavIcon({ id, size = 16 }: { id: string; size?: number }) {
  const p = { width: size, height: size, viewBox: "0 0 18 18", ...stroke };
  switch (id) {
    case "overview":
      return (
        <svg {...p}>
          <rect x={2} y={2} width={6} height={6} rx={1.4} />
          <rect x={10} y={2} width={6} height={6} rx={1.4} />
          <rect x={2} y={10} width={6} height={6} rx={1.4} />
          <rect x={10} y={10} width={6} height={6} rx={1.4} />
        </svg>
      );
    case "live":
      return (
        <svg {...p}>
          <path d="M1 12l3-6 3 8 3-11 3 7 3-3 1 0" />
        </svg>
      );
    case "history":
      return (
        <svg {...p}>
          <rect x={3.5} y={2} width={11} height={14} rx={1.4} />
          <path d="M6 6h6M6 9h6M6 12h3.5" />
        </svg>
      );
    case "device":
      return (
        <svg {...p}>
          <rect x={2.5} y={5} width={13} height={8} rx={1.6} />
          <circle cx={6} cy={9} r={1.2} fill="currentColor" />
          <path d="M9.5 9h4" />
        </svg>
      );
    case "thermometer":
      return (
        <svg {...p}>
          <path d="M9 2.6a2 2 0 0 1 2 2v5.2a3.6 3.6 0 1 1-4 0V4.6a2 2 0 0 1 2-2z" />
          <path d="M9 7.4v4.4" />
        </svg>
      );
    case "ph":
      // A dropper over a graduated scale — the gesture of a titration.
      return (
        <svg {...p}>
          <path d="M11.4 2.2l4.4 4.4-6.1 6.1a1.6 1.6 0 0 1-.8.44l-3.3.74.74-3.3c.06-.3.22-.58.44-.8z" />
          <path d="M2 15.8h5" />
        </svg>
      );
    case "tds":
      // Particles suspended in a body of water: what the probe is counting.
      return (
        <svg {...p}>
          <path d="M2.4 12.6c1.4-1.1 2.8-1.1 4.2 0s2.8 1.1 4.2 0 2.8-1.1 4.2 0" />
          <path d="M2.4 15.4c1.4-1.1 2.8-1.1 4.2 0s2.8 1.1 4.2 0 2.8-1.1 4.2 0" />
          <circle cx={5.4} cy={4.6} r={1.15} />
          <circle cx={10.4} cy={7.1} r={1.15} />
          <circle cx={13.2} cy={3.6} r={1.15} />
        </svg>
      );
    case "pump":
      // Impeller housing with an outlet — reads as a pump at 16px.
      return (
        <svg {...p}>
          <circle cx={7.6} cy={10.4} r={4.4} />
          <path d="M7.6 10.4V6M7.6 10.4l3.5 2.2" />
          <path d="M12 6.6h4v3.2" />
        </svg>
      );
    case "droplet":
      return (
        <svg {...p}>
          <path d="M9 2.2c2.6 3 4.4 5.1 4.4 7.4A4.4 4.4 0 0 1 9 14a4.4 4.4 0 0 1-4.4-4.4C4.6 7.3 6.4 5.2 9 2.2z" />
          <path d="M6.9 9.9c.1 1.1.9 2 2 2.2" />
        </svg>
      );
    case "download":
      return (
        <svg {...p}>
          <path d="M9 2.5v8" />
          <path d="M5.8 7.6 9 10.8l3.2-3.2" />
          <path d="M3 13.4h12" />
        </svg>
      );
    case "search":
      return (
        <svg {...p} strokeWidth={1.7}>
          <circle cx={8} cy={8} r={5.2} />
          <path d="M12 12l4 4" />
        </svg>
      );
    default:
      return null;
  }
}

/** Band glyphs, ported straight from the design source. */
export function BandIcon({ band, size = 14 }: { band: Band; size?: number }) {
  const c = "currentColor";
  if (band === "safe")
    return (
      <svg width={size} height={size} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.6}>
        <circle cx={8} cy={8} r={6.2} />
        <path d="M5.2 8.2l1.9 1.9 3.7-4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  if (band === "watch")
    return (
      <svg width={size} height={size} viewBox="0 0 16 16" fill="none" stroke={c} strokeWidth={1.7}>
        <path d="M8 2.2 14.4 13.4H1.6z" strokeLinejoin="round" />
        <path d="M8 6.4v3.1" strokeLinecap="round" />
        <circle cx={8} cy={11.2} r={0.6} fill={c} stroke="none" />
      </svg>
    );
  if (band === "warning")
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 16 16"
        fill="none"
        stroke={c}
        strokeWidth={1.7}
        strokeLinejoin="round"
      >
        <path d="M8 1.6 14.4 8 8 14.4 1.6 8z" />
        <path d="M8 5v3.4" strokeLinecap="round" />
        <circle cx={8} cy={10.6} r={0.7} fill={c} stroke="none" />
      </svg>
    );
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill={c}>
      <path d="M5.3 1.4h5.4L14.6 5.3v5.4L10.7 14.6H5.3L1.4 10.7V5.3z" />
      <path d="M8 4.4v4" stroke="#fff" strokeWidth={1.5} strokeLinecap="round" />
      <circle cx={8} cy={10.8} r={0.8} fill="#fff" />
    </svg>
  );
}
