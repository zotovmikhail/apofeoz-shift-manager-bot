"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  authHeader,
  bootstrapSession,
  createWorker,
  login,
  logout,
  patchUser,
  patchWorker,
  reportRange,
  timesheetDownloadUrl,
  users,
  workers,
} from "../lib/api";
import type { HoursReportResponseDto, UserResponseDto, WorkerResponseDto } from "../lib/types";

type TabKey = "workers" | "users" | "reports" | "profile";

type SummaryMetric = {
  label: string;
  value: string;
  tone?: "accent" | "neutral";
};

const TAB_META: Record<TabKey, { label: string; eyebrow: string; title: string; description: string }> = {
  workers: {
    label: "Workers",
    eyebrow: "Field structure",
    title: "Workers and brigades",
    description: "Manage worker cards, keep foreman assignments clean, and update team availability without leaving the panel.",
  },
  users: {
    label: "Users",
    eyebrow: "Access control",
    title: "Roles and account states",
    description: "Control who becomes FOREMAN or ADMIN, and lock accounts with clear server feedback when backend rules block the change.",
  },
  reports: {
    label: "Reports",
    eyebrow: "Shift analytics",
    title: "Operational report matrix",
    description: "Review hours, shift equivalents and downloadable timesheet exports in a browser view that mirrors the XLSX logic.",
  },
  profile: {
    label: "Profile",
    eyebrow: "Session",
    title: "Current admin session",
    description: "Check the current account, confirm role context, and end the session explicitly.",
  },
};

export default function AdminApp() {
  const [busy, setBusy] = useState(true);
  const [user, setUser] = useState<UserResponseDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function run() {
      setBusy(true);
      try {
        const me = await bootstrapSession();
        if (!cancelled) setUser(me);
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : "Session bootstrap error");
        }
      } finally {
        if (!cancelled) setBusy(false);
      }
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, []);

  if (busy) {
    return (
      <main className="shell shellCentered">
        <section className="spotlightPanel skeletonPanel">
          <p className="eyebrow">Boot sequence</p>
          <h1>Loading admin workspace</h1>
          <p className="mutedText">Refreshing session and preparing live data channels.</p>
        </section>
      </main>
    );
  }

  if (!user) return <LoginForm onLogin={setUser} />;
  if (user.role !== "ADMIN") {
    return (
      <main className="shell shellCentered">
        <section className="spotlightPanel narrowPanel">
          <p className="eyebrow">Access boundary</p>
          <h1>ADMIN role required</h1>
          <p className="mutedText">This panel is reserved for administrators. Log in with an account that has role `ADMIN`.</p>
        </section>
      </main>
    );
  }

  return (
    <AdminPanel
      user={user}
      onLogout={() => setUser(null)}
      onError={setError}
      globalError={error}
      clearError={() => setError(null)}
    />
  );
}

function LoginForm({ onLogin }: { onLogin: (u: UserResponseDto) => void }) {
  const [loginValue, setLoginValue] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const u = await login({ login: loginValue.trim(), password });
      onLogin(u);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="shell shellCentered">
      <section className="loginHero">
        <div className="loginBrand">
          <span className="brandBadge">Apofeoz</span>
          <p className="eyebrow">Admin web console</p>
          <h1>Command the shift floor from one screen.</h1>
          <p className="mutedText">
            Dark graphite surfaces, gold signal accents, and the same operational backbone as the mobile admin experience.
          </p>
          <div className="metricRail">
            <MetricCard label="Domain" value="Admin control" tone="accent" />
            <MetricCard label="Auth" value="JWT + refresh" />
            <MetricCard label="Reports" value="Live + XLSX" />
          </div>
        </div>

        <div className="loginCard">
          <div className="cardHeader">
            <p className="eyebrow">Sign in</p>
            <h2>Secure admin entry</h2>
          </div>
          <form onSubmit={submit} className="stackForm">
            <label className="field">
              <span>Login</span>
              <input value={loginValue} onChange={(e) => setLoginValue(e.target.value)} placeholder="email or phone" required />
            </label>
            <label className="field">
              <span>Password</span>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="password" required />
            </label>
            <button type="submit" className="primaryButton" disabled={loading}>
              {loading ? "Signing in..." : "Enter admin console"}
            </button>
          </form>
          {error && <InlineError text={error} />}
        </div>
      </section>
    </main>
  );
}

function AdminPanel({
  user,
  onLogout,
  onError,
  globalError,
  clearError,
}: {
  user: UserResponseDto;
  onLogout: () => void;
  onError: (msg: string | null) => void;
  globalError: string | null;
  clearError: () => void;
}) {
  const [tab, setTab] = useState<TabKey>("workers");
  const tabInfo = TAB_META[tab];

  async function doLogout() {
    await logout();
    onLogout();
  }

  return (
    <main className="shell">
      <section className="dashboardGrid">
        <aside className="sidebarPanel">
          <div className="sidebarTop">
            <span className="brandBadge">Apofeoz</span>
            <div>
              <p className="eyebrow">Admin session</p>
              <h1 className="sidebarTitle">Control deck</h1>
            </div>
            <p className="mutedText">
              {user.firstName} {user.lastName}
            </p>
          </div>

          <nav className="sidebarNav">
            {Object.entries(TAB_META).map(([key, meta]) => (
              <button
                key={key}
                className={tab === key ? "navCard active" : "navCard"}
                onClick={() => setTab(key as TabKey)}
              >
                <span className="navLabel">{meta.label}</span>
                <span className="navDescription">{meta.eyebrow}</span>
              </button>
            ))}
          </nav>

          <div className="sidebarFooter">
            <div className="userChip">
              <span className="userInitials">{initials(user)}</span>
              <div>
                <strong>{user.role}</strong>
                <p>{user.status}</p>
              </div>
            </div>
            <button className="ghostButton" onClick={doLogout}>Logout</button>
          </div>
        </aside>

        <section className="mainColumn">
          <header className="heroPanel">
            <div className="heroCopy">
              <p className="eyebrow">{tabInfo.eyebrow}</p>
              <h2>{tabInfo.title}</h2>
              <p className="mutedText">{tabInfo.description}</p>
            </div>
            <div className="heroMetrics">
              <MetricCard label="Operator" value={`${user.firstName} ${user.lastName}`} />
              <MetricCard label="Role" value={user.role} tone="accent" />
              <MetricCard label="State" value={user.status} />
            </div>
          </header>

          {globalError && (
            <div className="noticeCard danger">
              <div>
                <strong>Request error</strong>
                <p>{globalError}</p>
              </div>
              <button className="ghostButton" onClick={clearError}>Dismiss</button>
            </div>
          )}

          {tab === "workers" && <WorkersTab onError={onError} />}
          {tab === "users" && <UsersTab onError={onError} />}
          {tab === "reports" && <ReportsTab onError={onError} />}
          {tab === "profile" && <ProfileTab user={user} />}
        </section>
      </section>
    </main>
  );
}

function WorkersTab({ onError }: { onError: (msg: string | null) => void }) {
  const [items, setItems] = useState<WorkerResponseDto[]>([]);
  const [foremen, setForemen] = useState<UserResponseDto[]>([]);
  const [loading, setLoading] = useState(false);

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [position, setPosition] = useState("");
  const [foremanId, setForemanId] = useState("");

  const activeCount = items.filter((item) => item.status === "ACTIVE").length;

  async function load() {
    setLoading(true);
    onError(null);
    try {
      const [ws, us] = await Promise.all([workers(), users()]);
      setItems(ws);
      const activeForemen = us.filter((u) => u.role === "FOREMAN" && u.status === "ACTIVE");
      setForemen(activeForemen);
      if (!foremanId && activeForemen.length > 0) {
        setForemanId(activeForemen[0].id);
      }
    } catch (e) {
      onError(e instanceof Error ? e.message : "Failed to load workers");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function submitCreate(e: FormEvent) {
    e.preventDefault();
    onError(null);
    try {
      await createWorker({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        position: position.trim() || undefined,
        foremanId: foremanId || undefined,
      });
      setFirstName("");
      setLastName("");
      setPosition("");
      await load();
    } catch (err) {
      onError(err instanceof Error ? err.message : "Create worker failed");
    }
  }

  async function toggleStatus(row: WorkerResponseDto) {
    onError(null);
    try {
      await patchWorker(row.id, { status: row.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" });
      await load();
    } catch (err) {
      onError(err instanceof Error ? err.message : "Patch worker failed");
    }
  }

  async function reassign(workerId: string, nextForemanId: string) {
    onError(null);
    try {
      await patchWorker(workerId, { foremanId: nextForemanId });
      await load();
    } catch (err) {
      onError(err instanceof Error ? err.message : "Reassign failed");
    }
  }

  return (
    <section className="contentStack">
      <div className="metricRail">
        <MetricCard label="Workers total" value={String(items.length)} tone="accent" />
        <MetricCard label="Active now" value={String(activeCount)} />
        <MetricCard label="Foremen online" value={String(foremen.length)} />
      </div>

      <section className="contentCard">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">Create card</p>
            <h3>Add worker</h3>
          </div>
          <button className="ghostButton" onClick={() => void load()} disabled={loading}>
            {loading ? "Refreshing..." : "Refresh data"}
          </button>
        </div>

        <form onSubmit={submitCreate} className="formGrid">
          <label className="field">
            <span>First name</span>
            <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
          </label>
          <label className="field">
            <span>Last name</span>
            <input value={lastName} onChange={(e) => setLastName(e.target.value)} required />
          </label>
          <label className="field">
            <span>Position</span>
            <input value={position} onChange={(e) => setPosition(e.target.value)} placeholder="optional" />
          </label>
          <label className="field">
            <span>Foreman</span>
            <select value={foremanId} onChange={(e) => setForemanId(e.target.value)} required>
              <option value="" disabled>Select foreman</option>
              {foremen.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.firstName} {f.lastName}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="primaryButton">Create worker</button>
        </form>
      </section>

      <section className="contentCard">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">Registry</p>
            <h3>Assigned workforce</h3>
          </div>
        </div>
        <div className="tableShell">
          <table className="dataTable">
            <thead>
              <tr>
                <th>Worker</th>
                <th>Position</th>
                <th>Status</th>
                <th>Foreman</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {items.map((w) => (
                <tr key={w.id}>
                  <td>
                    <div className="tableIdentity">
                      <strong>{w.lastName} {w.firstName}</strong>
                      <span>{w.userId ? "Linked to account" : "Standalone worker card"}</span>
                    </div>
                  </td>
                  <td>{w.position ?? "—"}</td>
                  <td>
                    <StatusPill tone={w.status === "ACTIVE" ? "ok" : "muted"}>{w.status}</StatusPill>
                  </td>
                  <td>
                    <select value={w.foremanId} onChange={(e) => void reassign(w.id, e.target.value)}>
                      {foremen.map((f) => (
                        <option key={f.id} value={f.id}>
                          {f.firstName} {f.lastName}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <button className="ghostButton" onClick={() => void toggleStatus(w)}>
                      {w.status === "ACTIVE" ? "Deactivate" : "Activate"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  );
}

function UsersTab({ onError }: { onError: (msg: string | null) => void }) {
  const [items, setItems] = useState<UserResponseDto[]>([]);
  const [loading, setLoading] = useState(false);

  const adminsCount = items.filter((item) => item.role === "ADMIN" && item.status === "ACTIVE").length;

  async function load() {
    setLoading(true);
    onError(null);
    try {
      setItems(await users());
    } catch (e) {
      onError(e instanceof Error ? e.message : "Failed to load users");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function updateRole(id: string, role: UserResponseDto["role"]) {
    onError(null);
    try {
      await patchUser(id, { role });
      await load();
    } catch (e) {
      onError(e instanceof Error ? e.message : "Patch user role failed");
    }
  }

  async function updateStatus(id: string, status: UserResponseDto["status"]) {
    onError(null);
    try {
      await patchUser(id, { status });
      await load();
    } catch (e) {
      onError(e instanceof Error ? e.message : "Patch user status failed");
    }
  }

  return (
    <section className="contentStack">
      <div className="metricRail">
        <MetricCard label="Users total" value={String(items.length)} tone="accent" />
        <MetricCard label="Active admins" value={String(adminsCount)} />
        <MetricCard label="Foremen" value={String(items.filter((item) => item.role === "FOREMAN").length)} />
      </div>

      <section className="contentCard">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">Access matrix</p>
            <h3>Roles and account states</h3>
          </div>
          <button className="ghostButton" onClick={() => void load()} disabled={loading}>
            {loading ? "Refreshing..." : "Refresh data"}
          </button>
        </div>
        <div className="tableShell">
          <table className="dataTable">
            <thead>
              <tr>
                <th>User</th>
                <th>Contact</th>
                <th>Role</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {items.map((u) => (
                <tr key={u.id}>
                  <td>
                    <div className="tableIdentity">
                      <strong>{u.lastName} {u.firstName}</strong>
                      <span>{u.id}</span>
                    </div>
                  </td>
                  <td>{u.email ?? u.phone ?? "—"}</td>
                  <td>
                    <select value={u.role} onChange={(e) => void updateRole(u.id, e.target.value as UserResponseDto["role"])}>
                      <option value="USER">USER</option>
                      <option value="FOREMAN">FOREMAN</option>
                      <option value="ADMIN">ADMIN</option>
                    </select>
                  </td>
                  <td>
                    <div className="statusControl">
                      <StatusPill tone={u.status === "ACTIVE" ? "ok" : "muted"}>{u.status}</StatusPill>
                      <select value={u.status} onChange={(e) => void updateStatus(u.id, e.target.value as UserResponseDto["status"])}>
                        <option value="ACTIVE">ACTIVE</option>
                        <option value="INACTIVE">INACTIVE</option>
                      </select>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  );
}

function ReportsTab({ onError }: { onError: (msg: string | null) => void }) {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [data, setData] = useState<HoursReportResponseDto | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const today = new Date();
    const weekAgo = new Date(today.getTime() - 6 * 24 * 60 * 60 * 1000);
    setFrom(toIsoDate(weekAgo));
    setTo(toIsoDate(today));
  }, []);

  const sortedRows = useMemo(() => {
    return [...(data?.rows ?? [])].sort((a, b) => {
      if (b.hours !== a.hours) return b.hours - a.hours;
      return `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`);
    });
  }, [data]);

  async function build() {
    if (!from || !to) return;
    setLoading(true);
    onError(null);
    try {
      setData(await reportRange(from, to));
    } catch (e) {
      onError(e instanceof Error ? e.message : "Failed to build report");
    } finally {
      setLoading(false);
    }
  }

  async function downloadXlsx() {
    if (!from || !to) return;
    onError(null);
    try {
      const res = await fetch(timesheetDownloadUrl(from, to), {
        headers: authHeader(),
      });
      if (!res.ok) throw new Error(`XLSX download failed: HTTP ${res.status}`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `timesheet_${from}_${to}.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      onError(e instanceof Error ? e.message : "XLSX download failed");
    }
  }

  return (
    <section className="contentStack">
      <div className="metricRail">
        <MetricCard label="Range start" value={from || "—"} tone="accent" />
        <MetricCard label="Range end" value={to || "—"} />
        <MetricCard label="Rows loaded" value={String(data?.rows.length ?? 0)} />
      </div>

      <section className="contentCard">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">Report builder</p>
            <h3>Interactive shift matrix</h3>
          </div>
        </div>

        <div className="formGrid reportControls">
          <label className="field">
            <span>From</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="field">
            <span>To</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
          <button className="primaryButton" onClick={() => void build()} disabled={loading || !from || !to}>
            {loading ? "Building..." : "Build report"}
          </button>
          <button className="ghostButton" onClick={() => void downloadXlsx()} disabled={!from || !to}>
            Download XLSX
          </button>
        </div>

        {data && (
          <>
            <div className="noticeCard">
              <div>
                <strong>{data.fromDate ?? from} — {data.toDate ?? to}</strong>
                <p>Timezone {data.timezone} · Shift norm {data.shiftNormHours}h · Totals {data.totals.hours.toFixed(1)}h / {data.totals.shiftEquivalent.toFixed(3)}</p>
              </div>
            </div>
            <div className="tableShell reportTableShell">
              <table className="dataTable reportTable">
                <thead>
                  <tr>
                    <th>Foreman</th>
                    <th>Worker</th>
                    <th>Hours</th>
                    <th>Shift eq</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedRows.map((r) => (
                    <tr key={r.workerId}>
                      <td>{r.foremanDisplayName ?? r.foremanId}</td>
                      <td>
                        <div className="tableIdentity">
                          <strong>{r.lastName} {r.firstName}</strong>
                          <span>{r.workerId}</span>
                        </div>
                      </td>
                      <td>{r.hours.toFixed(1)}</td>
                      <td>{r.shiftEquivalent.toFixed(3)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={2}><strong>Total</strong></td>
                    <td><strong>{data.totals.hours.toFixed(1)}</strong></td>
                    <td><strong>{data.totals.shiftEquivalent.toFixed(3)}</strong></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </>
        )}
      </section>
    </section>
  );
}

function ProfileTab({ user }: { user: UserResponseDto }) {
  const metrics: SummaryMetric[] = [
    { label: "Role", value: user.role, tone: "accent" },
    { label: "Status", value: user.status },
    { label: "Identity", value: user.email ?? user.phone ?? "No contact" },
  ];

  return (
    <section className="contentStack">
      <div className="metricRail">
        {metrics.map((metric) => (
          <MetricCard key={metric.label} label={metric.label} value={metric.value} tone={metric.tone} />
        ))}
      </div>

      <section className="contentCard profileCard">
        <div className="profileHero">
          <div className="profileSeal">{initials(user)}</div>
          <div>
            <p className="eyebrow">Current operator</p>
            <h3>{user.firstName} {user.lastName}</h3>
            <p className="mutedText">Use this block to confirm which privileged account is currently operating the web admin panel.</p>
          </div>
        </div>

        <div className="profileGrid">
          <ProfileField label="User ID" value={user.id} />
          <ProfileField label="Email" value={user.email ?? "—"} />
          <ProfileField label="Phone" value={user.phone ?? "—"} />
          <ProfileField label="Role" value={user.role} />
          <ProfileField label="Status" value={user.status} />
        </div>
      </section>
    </section>
  );
}

function MetricCard({ label, value, tone = "neutral" }: SummaryMetric) {
  return (
    <div className={tone === "accent" ? "metricCard accent" : "metricCard"}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function StatusPill({ children, tone }: { children: string; tone: "ok" | "muted" }) {
  return <span className={tone === "ok" ? "statusPill ok" : "statusPill muted"}>{children}</span>;
}

function ProfileField({ label, value }: { label: string; value: string }) {
  return (
    <div className="profileField">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function InlineError({ text }: { text: string }) {
  return (
    <div className="noticeCard danger compact">
      <div>
        <strong>Error</strong>
        <p>{text}</p>
      </div>
    </div>
  );
}

function toIsoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function initials(user: UserResponseDto): string {
  return `${user.firstName[0] ?? ""}${user.lastName[0] ?? ""}`.toUpperCase() || "AD";
}

