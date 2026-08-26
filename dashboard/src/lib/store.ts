import fs from "node:fs";
import path from "node:path";
import { MAX_READINGS } from "./config";
import type { Reading } from "./types";

/**
 * In-process ring buffer with optional NDJSON persistence.
 *
 * Held on globalThis so the dev server's module reloading doesn't wipe
 * history between requests. Good for a single long-lived Node process
 * (local, Render, Railway, a Pi). If you ever run this on multiple
 * serverless instances, swap this file for a real database — the API
 * surface below is deliberately small.
 */

type Subscriber = (reading: Reading) => void;

type Store = {
  readings: Reading[];
  subscribers: Set<Subscriber>;
  loaded: boolean;
};

const PERSIST = process.env.JALRAKSHA_PERSIST !== "0";
const DATA_DIR = process.env.JALRAKSHA_DATA_DIR || path.join(process.cwd(), "data");
const DATA_FILE = path.join(DATA_DIR, "readings.ndjson");

const globalRef = globalThis as typeof globalThis & { __jalraksha?: Store };

function store(): Store {
  if (!globalRef.__jalraksha) {
    globalRef.__jalraksha = { readings: [], subscribers: new Set(), loaded: false };
    hydrate(globalRef.__jalraksha);
  }
  return globalRef.__jalraksha;
}

function hydrate(s: Store) {
  s.loaded = true;
  if (!PERSIST) return;
  try {
    if (!fs.existsSync(DATA_FILE)) return;
    const lines = fs.readFileSync(DATA_FILE, "utf8").trim().split("\n");
    const tail = lines.slice(-MAX_READINGS);
    for (const line of tail) {
      if (!line) continue;
      try {
        s.readings.push(JSON.parse(line) as Reading);
      } catch {
        /* skip torn line */
      }
    }
    s.readings.sort((a, b) => a.t - b.t);
  } catch (err) {
    console.warn("[store] could not restore readings:", err);
  }
}

function persist(reading: Reading) {
  if (!PERSIST) return;
  try {
    fs.mkdirSync(DATA_DIR, { recursive: true });
    fs.appendFileSync(DATA_FILE, JSON.stringify(reading) + "\n");
  } catch (err) {
    // A read-only filesystem (some hosts) is not fatal — memory still works.
    console.warn("[store] persistence disabled:", (err as Error).message);
  }
}

export function addReading(reading: Reading): Reading {
  const s = store();
  s.readings.push(reading);
  if (s.readings.length > MAX_READINGS) s.readings.splice(0, s.readings.length - MAX_READINGS);
  persist(reading);
  for (const fn of s.subscribers) {
    try {
      fn(reading);
    } catch {
      /* a broken stream must not break ingest */
    }
  }
  return reading;
}

export function getReadings(opts: { since?: number; limit?: number } = {}): Reading[] {
  const s = store();
  let out = s.readings;
  if (opts.since !== undefined) out = out.filter((r) => r.t > opts.since!);
  if (opts.limit !== undefined && out.length > opts.limit) out = out.slice(-opts.limit);
  return out;
}

export function latestReading(): Reading | null {
  const s = store();
  return s.readings.length ? s.readings[s.readings.length - 1] : null;
}

export function readingCount(): number {
  return store().readings.length;
}

export function subscribe(fn: Subscriber): () => void {
  const s = store();
  s.subscribers.add(fn);
  return () => s.subscribers.delete(fn);
}

export function clearReadings() {
  const s = store();
  s.readings.length = 0;
  if (PERSIST) {
    try {
      fs.rmSync(DATA_FILE, { force: true });
    } catch {
      /* ignore */
    }
  }
}

/**
 * Evenly thins a series down to `max` points, always keeping the newest
 * sample. Charts don't need 8000 nodes and the browser is happier without.
 */
export function downsample<T>(rows: T[], max: number): T[] {
  if (rows.length <= max) return rows;
  const step = rows.length / max;
  const out: T[] = [];
  for (let i = 0; i < max; i++) out.push(rows[Math.floor(i * step)]);
  out[out.length - 1] = rows[rows.length - 1];
  return out;
}
