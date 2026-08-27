"use client";

import { useMemo, useState } from "react";
import StatusPill from "@/components/StatusPill";
import { NavIcon } from "@/components/icons";
import { useLive } from "@/components/useLive";
import { bandColor } from "@/lib/bandStyle";
import {
  SENSORS,
  SENSOR_ORDER,
  bandOfScore,
  bandOfValue,
  riskScore,
  scoredValue,
} from "@/lib/config";
import type { Band } from "@/lib/types";

const WINDOWS = [
  { label: "1 h", ms: 60 * 60 * 1000 },
  { label: "6 h", ms: 6 * 60 * 60 * 1000 },
  { label: "24 h", ms: 24 * 60 * 60 * 1000 },
];

const BANDS: (Band | "all")[] = ["all", "safe", "watch", "warning", "critical"];
const PAGE_SIZE = 60;

export default function HistoryPage() {
  const [win, setWin] = useState(WINDOWS[2]);
  const [filter, setFilter] = useState<Band | "all">("all");
  const [limit, setLimit] = useState(PAGE_SIZE);
  const { readings, loading } = useLive(win.ms, 4000);

  const rows = useMemo(() => {
    const mapped = readings
      .map((r) => {
        const score = riskScore(r);
        return { ...r, score, band: bandOfScore(score) };
      })
      .reverse();
    return filter === "all" ? mapped : mapped.filter((r) => r.band === filter);
  }, [readings, filter]);

  // One summary per sensor, built from whatever each probe actually produced —
  // a probe that was down for half the window is averaged over the half it was
  // up, and says so, rather than being averaged against zeros.
  const stats = useMemo(
    () =>
      SENSOR_ORDER.map((key) => {
        const vals = readings
          .map((r) => scoredValue(r, key))
          .filter((v): v is number => v !== null);
        return {
          key,
          spec: SENSORS[key],
          n: vals.length,
          avg: vals.length ? vals.reduce((x, y) => x + y, 0) / vals.length : null,
          min: vals.length ? Math.min(...vals) : null,
          max: vals.length ? Math.max(...vals) : null,
        };
      }),
    [readings],
  );

  const fmt = (v: number | null, d: number) => (v === null ? "—" : v.toFixed(d));

  return (
    <div className="page-inner">
      <div className="h2">History &amp; Export</div>
      <div className="subtle" style={{ marginTop: 8, marginBottom: 20 }}>
        Every stored reading from the field node · CSV export for the report
      </div>

      <div
        className="grid"
        style={{ gridTemplateColumns: "repeat(auto-fit,minmax(190px,1fr))", marginBottom: 20 }}
      >
        <div className="card card-pad">
          <div className="eyebrow">Readings in window</div>
          <div className="figure" style={{ marginTop: 6 }}>
            {readings.length}
          </div>
          <div style={{ fontSize: 11.5, color: "var(--muted)", marginTop: 4 }}>
            last {win.label} · newest first
          </div>
        </div>
        {stats.map((st) => (
          <div className="card card-pad" key={st.key}>
            <div className="eyebrow">{st.spec.name} avg</div>
            <div className="figure" style={{ marginTop: 6 }}>
              {fmt(st.avg, st.spec.decimals)}
              <span style={{ fontSize: 14, color: "var(--muted-2)", marginLeft: 4 }}>
                {st.spec.unit}
              </span>
            </div>
            <div style={{ fontSize: 11.5, color: "var(--muted)", marginTop: 4 }}>
              {st.n === 0
                ? "no readings from this probe"
                : `min ${fmt(st.min, st.spec.decimals)} · max ${fmt(st.max, st.spec.decimals)}`}
              {st.n > 0 && st.n < readings.length && ` · ${st.n}/${readings.length} samples`}
            </div>
          </div>
        ))}
        <div className="card card-pad" style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <div className="eyebrow">Export</div>
          <a className="btn btn-primary" href={`/api/export?window=${win.ms}`} download>
            <NavIcon id="download" size={15} />
            Readings CSV
          </a>
          <div style={{ fontSize: 11, color: "var(--muted-2)" }}>current window · all columns</div>
        </div>
      </div>

      <div
        style={{
          display: "flex",
          gap: 12,
          flexWrap: "wrap",
          alignItems: "center",
          marginBottom: 14,
        }}
      >
        <div className="seg">
          {WINDOWS.map((w) => (
            <button
              key={w.label}
              data-active={w.ms === win.ms}
              onClick={() => {
                setWin(w);
                setLimit(PAGE_SIZE);
              }}
            >
              {w.label}
            </button>
          ))}
        </div>
        <div className="seg">
          {BANDS.map((b) => (
            <button
              key={b}
              data-active={filter === b}
              onClick={() => {
                setFilter(b);
                setLimit(PAGE_SIZE);
              }}
            >
              {b === "all" ? "All" : b[0].toUpperCase() + b.slice(1)}
            </button>
          ))}
        </div>
        <div style={{ marginLeft: "auto", fontSize: 12, color: "var(--muted)" }}>
          Showing {Math.min(limit, rows.length)} of {rows.length}
        </div>
      </div>

      <div className="card" style={{ overflow: "hidden" }}>
        <div className="scroll-x">
          <table style={{ minWidth: 860 }}>
            <thead>
              <tr>
                <th>Time</th>
                {SENSOR_ORDER.map((key) => (
                  <th className="num" key={key}>
                    {SENSORS[key].name} {SENSORS[key].unit}
                  </th>
                ))}
                <th className="num">Risk</th>
                <th>Band</th>
              </tr>
            </thead>
            <tbody>
              {rows.slice(0, limit).map((r) => {
                return (
                  <tr key={r.t}>
                    <td className="mono" style={{ color: "var(--muted)", whiteSpace: "nowrap" }}>
                      {new Date(r.t).toLocaleString()}
                    </td>
                    {SENSOR_ORDER.map((key) => {
                      const v = scoredValue(r, key);
                      const b = v === null ? null : bandOfValue(v, SENSORS[key]);
                      return (
                        <td
                          className="mono num"
                          key={key}
                          style={{ color: b ? bandColor[b] : "var(--muted-2)", fontWeight: 500 }}
                          title={b ? undefined : "sensor not reading"}
                        >
                          {v === null ? "n/d" : v.toFixed(SENSORS[key].decimals)}
                        </td>
                      );
                    })}
                    <td className="mono num" style={{ fontWeight: 600 }}>
                      {r.score}
                    </td>
                    <td>
                      <StatusPill band={r.band} size={11} />
                    </td>
                  </tr>
                );
              })}
              {!rows.length && (
                <tr>
                  <td colSpan={SENSOR_ORDER.length + 3} style={{ padding: 28, textAlign: "center", color: "var(--muted)" }}>
                    {loading ? "Loading…" : "No readings match this filter."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {rows.length > limit && (
        <div style={{ textAlign: "center", marginTop: 16 }}>
          <button className="btn" onClick={() => setLimit((l) => l + PAGE_SIZE * 4)}>
            Show more
          </button>
        </div>
      )}
    </div>
  );
}
