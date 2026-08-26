"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { deviceDot, deviceLabel } from "@/lib/bandStyle";
import { DEVICE_LABEL } from "@/lib/config";
import { ageLabel } from "@/lib/derive";
import type { DeviceState } from "@/lib/types";
import { NavIcon } from "./icons";

const NAV = [
  {
    label: "Monitoring",
    items: [
      { href: "/", id: "overview", label: "Overview" },
      { href: "/live", id: "live", label: "Live Monitoring" },
    ],
  },
  {
    label: "Data",
    items: [
      { href: "/history", id: "history", label: "History & Export" },
      { href: "/device", id: "device", label: "Device" },
    ],
  },
];

const TITLES: Record<string, string> = {
  "/": "Overview",
  "/live": "Live Monitoring",
  "/history": "History & Export",
  "/device": "Device",
};

type Status = {
  deviceId: string;
  state: DeviceState;
  lastSeen: number | null;
  perMinute: number;
  total: number;
  sensorsOk: number;
  sensors: Record<string, { ok: boolean; label: string }>;
};

export default function Shell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [status, setStatus] = useState<Status | null>(null);
  const [now, setNow] = useState(0);

  useEffect(() => {
    let alive = true;
    const tick = async () => {
      try {
        const res = await fetch("/api/status", { cache: "no-store" });
        if (alive) setStatus(await res.json());
      } catch {
        /* ignore */
      }
      if (alive) setNow(Date.now());
    };
    tick();
    const id = setInterval(tick, 5000);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, []);

  const flat = NAV.flatMap((g) => g.items);

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">
            <i />
          </div>
          <div style={{ minWidth: 0 }}>
            <div className="brand-name">
              Jalraksha <em>One</em>
            </div>
            <div className="brand-sub">Water Safety OS</div>
          </div>
        </div>

        <div className="node-chip">
          <div className="avatar">JR</div>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 12, fontWeight: 600, lineHeight: 1.2 }}>{DEVICE_LABEL}</div>
            <div style={{ fontSize: 10, color: "var(--muted)" }}>
              1 node · 2 sensors
            </div>
          </div>
        </div>

        <nav className="nav">
          {NAV.map((group) => (
            <div key={group.label}>
              <div className="nav-group">{group.label}</div>
              {group.items.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`nav-item${pathname === item.href ? " active" : ""}`}
                >
                  <span className="ic">
                    <NavIcon id={item.id} />
                  </span>
                  <span>{item.label}</span>
                </Link>
              ))}
            </div>
          ))}
        </nav>

        <div className="ingest-card">
          <div className="row" style={{ gap: 7, marginBottom: 5 }}>
            <span className="dot" />
            <span style={{ fontSize: 11.5, fontWeight: 600, color: "var(--ink-2)" }}>
              Live ingest
            </span>
          </div>
          <div style={{ fontSize: 11, color: "var(--muted)", lineHeight: 1.5 }}>
            {status ? `${status.perMinute} readings/min · ` : "waiting for device · "}
            {status ? deviceLabel[status.state].toLowerCase() : "no data yet"}
          </div>
        </div>

        {status && (
          <div style={{ margin: "0 12px 12px", display: "flex", flexDirection: "column", gap: 6 }}>
            {Object.entries(status.sensors).map(([key, s]) => (
              <div
                key={key}
                className="row"
                style={{ gap: 8, fontSize: 11, color: "var(--muted)" }}
                title={s.label}
              >
                <span
                  style={{
                    width: 7,
                    height: 7,
                    borderRadius: "50%",
                    background: s.ok ? "var(--safe)" : "var(--critical)",
                    flexShrink: 0,
                  }}
                />
                <span style={{ textTransform: "capitalize" }}>{key}</span>
                <span
                  style={{
                    marginLeft: "auto",
                    fontWeight: 600,
                    color: s.ok ? "var(--safe)" : "var(--critical)",
                  }}
                >
                  {s.ok ? "reading" : s.label.toLowerCase()}
                </span>
              </div>
            ))}
          </div>
        )}

        <div
          style={{
            borderTop: "1px solid var(--border)",
            padding: 12,
            display: "flex",
            alignItems: "center",
            gap: 10,
          }}
        >
          <span
            style={{
              width: 9,
              height: 9,
              borderRadius: "50%",
              background: status ? deviceDot[status.state] : "#cbd5e1",
              flexShrink: 0,
            }}
          />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="mono" style={{ fontSize: 12, fontWeight: 500 }}>
              {status?.deviceId ?? "—"}
            </div>
            <div style={{ fontSize: 10.5, color: "var(--muted)" }}>
              {status?.lastSeen && now ? `seen ${ageLabel(status.lastSeen, now)}` : "never seen"}
            </div>
          </div>
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div className="topbar-inner">
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 15, fontWeight: 600, letterSpacing: "-0.01em" }}>
                {TITLES[pathname] ?? "Jalraksha One"}
              </div>
            </div>
            <span className="pill pill-neutral">
              <span
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: "50%",
                  background: status ? deviceDot[status.state] : "#cbd5e1",
                }}
              />
              {status ? deviceLabel[status.state] : "Connecting…"}
            </span>
            {status && (
              <span
                className="pill"
                style={
                  status.sensorsOk === 2
                    ? { color: "var(--safe)", background: "var(--safe-bg)" }
                    : { color: "var(--critical)", background: "var(--critical-bg)" }
                }
              >
                {status.sensorsOk}/2 sensors
              </span>
            )}
            <span className="pill pill-accent mono">{status?.total ?? 0} readings</span>
          </div>
          <div className="mobile-nav">
            {flat.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={pathname === item.href ? "active" : ""}
              >
                {item.label}
              </Link>
            ))}
          </div>
        </header>
        <div className="page">{children}</div>
      </main>
    </div>
  );
}
