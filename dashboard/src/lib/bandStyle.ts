import type { Band, DeviceState } from "./types";

export const bandColor: Record<Band, string> = {
  safe: "var(--safe)",
  watch: "var(--watch)",
  warning: "var(--warning)",
  critical: "var(--critical)",
};

export const bandBg: Record<Band, string> = {
  safe: "var(--safe-bg)",
  watch: "var(--watch-bg)",
  warning: "var(--warning-bg)",
  critical: "var(--critical-bg)",
};

export const deviceColor: Record<DeviceState, string> = {
  online: "var(--safe)",
  degraded: "var(--watch)",
  offline: "var(--muted-2)",
};

export const deviceDot: Record<DeviceState, string> = {
  online: "var(--safe)",
  degraded: "var(--watch)",
  offline: "#cbd5e1",
};

export const deviceLabel: Record<DeviceState, string> = {
  online: "Online",
  degraded: "Degraded",
  offline: "Offline",
};
