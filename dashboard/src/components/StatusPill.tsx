import { BAND_LABEL } from "@/lib/config";
import { bandBg, bandColor } from "@/lib/bandStyle";
import type { Band } from "@/lib/types";
import { BandIcon } from "./icons";

export default function StatusPill({
  band,
  label,
  size = 11.5,
}: {
  band: Band;
  label?: string;
  size?: number;
}) {
  return (
    <span
      className="pill"
      style={{ color: bandColor[band], background: bandBg[band], fontSize: size }}
    >
      <BandIcon band={band} size={size} />
      {label ?? BAND_LABEL[band]}
    </span>
  );
}
