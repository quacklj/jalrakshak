import { PUMP_MAX_RUN_MS, RELAYS } from "./config";
import { latestReading } from "./store";
import type { RelayId, RelayView } from "./types";

/**
 * What the dashboard is asking each pump to do.
 *
 * Deliberately in-memory and deliberately not persisted: on a restart every
 * pump comes back off. A relay command that survives a crash and re-latches a
 * motor when the server comes up is a hazard, not a feature.
 *
 * Held on globalThis for the same reason as the reading store — the dev
 * server's module reloading must not silently reset a running pump.
 */

type Command = { desired: boolean; since: number; source: string };
type RelayStore = Record<RelayId, Command>;

const globalRef = globalThis as typeof globalThis & { __jalrakshaRelays?: RelayStore };

function store(): RelayStore {
  if (!globalRef.__jalrakshaRelays) {
    const now = Date.now();
    globalRef.__jalrakshaRelays = Object.fromEntries(
      RELAYS.map((r) => [r.id, { desired: false, since: now, source: "boot" }]),
    ) as RelayStore;
  }
  return globalRef.__jalrakshaRelays;
}

export function isRelayId(v: unknown): v is RelayId {
  return typeof v === "string" && RELAYS.some((r) => r.id === v);
}

/**
 * The server-side half of the run limit. Reading the state is what expires it,
 * so it applies whether the expiry is noticed by the device poll, the UI, or
 * the ingest handler — no timer to miss, and no way to read a stale "on".
 */
function currentCommand(id: RelayId, now: number): Command {
  const c = store()[id];
  if (c.desired && now - c.since >= PUMP_MAX_RUN_MS) {
    const expired: Command = { desired: false, since: now, source: "auto-off" };
    store()[id] = expired;
    return expired;
  }
  return c;
}

export function setRelay(id: RelayId, on: boolean, source = "dashboard"): void {
  const c = store()[id];
  // Re-commanding the same state must not restart the run clock, or holding a
  // button down would keep a pump alive past its limit.
  if (c.desired === on) return;
  store()[id] = { desired: on, since: Date.now(), source };
}

/** Turns everything off. Used by the UI's stop-all control. */
export function allOff(source = "dashboard"): void {
  for (const r of RELAYS) setRelay(r.id, false, source);
}

/** Just the commanded booleans, in RELAYS order — what the firmware polls for. */
export function desiredStates(now = Date.now()): boolean[] {
  return RELAYS.map((r) => currentCommand(r.id, now).desired);
}

/** Everything the UI needs: what was asked, and what the device says it did. */
export function relayViews(now = Date.now()): RelayView[] {
  const latest = latestReading();
  return RELAYS.map((spec) => {
    const c = currentCommand(spec.id, now);
    const reported = latest?.relays?.[spec.id];
    return {
      id: spec.id,
      name: spec.name,
      pin: spec.pin,
      blurb: spec.blurb,
      desired: c.desired,
      since: c.since,
      autoOffAt: c.desired ? c.since + PUMP_MAX_RUN_MS : null,
      actual: reported === undefined ? null : reported,
      actualAt: reported === undefined ? null : (latest?.t ?? null),
    };
  });
}
