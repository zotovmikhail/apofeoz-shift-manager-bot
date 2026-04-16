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

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

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
        if (cancelled) return;
        setUser(me);
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : "Session bootstrap error");
      } finally {
        if (!cancelled) setBusy(false);
      }
    }
    run();
    return () => {
      cancelled = true;
    };
  }, []);

  if (busy) return <main className="screen"><p>Loading...</p></main>;
  if (!user) return <LoginForm onLogin={setUser} />;
  if (user.role !== "ADMIN") return <main className="screen"><p>Access denied: ADMIN only.</p></main>;

  return <AdminPanel user={user} onLogout={() => setUser(null)} onError={setError} globalError={error} clearError={() => setError(null)} />;
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
    <main className="screen">
      <div className="card narrow">
        <h1>Apofeoz Admin</h1>
        <p className="muted">Login by email or phone</p>
        <form onSubmit={submit} className="form">
          <label>
            Login
            <input value={loginValue} onChange={(e) => setLoginValue(e.target.value)} required />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          <button type="submit" disabled={loading}>
            {loading ? "Signing in..." : "Sign in"}
          </button>
        </form>
        {error && <p className="error">{error}</p>}
      </div>
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

  async function doLogout() {
    await logout();
    onLogout();
  }

  return (
    <main className="screen">
      <header className="topbar">
        <div>
          <h1>Apofeoz Admin</h1>
          <p className="muted">{user.firstName} {user.lastName} · {user.role}</p>
        </div>
        <button className="secondary" onClick={doLogout}>Logout</button>
      </header>

      <nav className="tabs">
        <TabButton active={tab === "workers"} onClick={() => setTab("workers")}>Workers</TabButton>
        <TabButton active={tab === "users"} onClick={() => setTab("users")}>Users</TabButton>
        <TabButton active={tab === "reports"} onClick={() => setTab("reports")}>Reports</TabButton>
        <TabButton active={tab === "profile"} onClick={() => setTab("profile")}>Profile</TabButton>
      </nav>

      {globalError && (
        <div className="notice error">
          <span>{globalError}</span>
          <button className="link" onClick={clearError}>Dismiss</button>
        </div>
      )}

      {tab === "workers" && <WorkersTab onError={onError} />}
      {tab === "users" && <UsersTab onError={onError} />}
      {tab === "reports" && <ReportsTab onError={onError} />}
      {tab === "profile" && <ProfileTab user={user} />}
    </main>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: string }) {
  return (
    <button className={active ? "tab active" : "tab"} onClick={onClick}>{children}</button>
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
    <section className="card">
      <div className="sectionHeader">
        <h2>Workers</h2>
        <button className="secondary" onClick={() => void load()} disabled={loading}>Refresh</button>
      </div>

      <form onSubmit={submitCreate} className="form row">
        <label>
          First name
          <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
        </label>
        <label>
          Last name
          <input value={lastName} onChange={(e) => setLastName(e.target.value)} required />
        </label>
        <label>
          Position
          <input value={position} onChange={(e) => setPosition(e.target.value)} />
        </label>
        <label>
          Foreman
          <select value={foremanId} onChange={(e) => setForemanId(e.target.value)} required>
            <option value="" disabled>Select foreman</option>
            {foremen.map((f) => (
              <option key={f.id} value={f.id}>{f.firstName} {f.lastName}</option>
            ))}
          </select>
        </label>
        <button type="submit">Create worker</button>
      </form>

      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>Worker</th>
              <th>Status</th>
              <th>Foreman</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {items.map((w) => (
              <tr key={w.id}>
                <td>{w.lastName} {w.firstName}</td>
                <td>{w.status}</td>
                <td>
                  <select value={w.foremanId} onChange={(e) => void reassign(w.id, e.target.value)}>
                    {foremen.map((f) => (
                      <option key={f.id} value={f.id}>{f.firstName} {f.lastName}</option>
                    ))}
                  </select>
                </td>
                <td>
                  <button className="secondary" onClick={() => void toggleStatus(w)}>
                    {w.status === "ACTIVE" ? "Deactivate" : "Activate"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function UsersTab({ onError }: { onError: (msg: string | null) => void }) {
  const [items, setItems] = useState<UserResponseDto[]>([]);
  const [loading, setLoading] = useState(false);

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
    <section className="card">
      <div className="sectionHeader">
        <h2>Users</h2>
        <button className="secondary" onClick={() => void load()} disabled={loading}>Refresh</button>
      </div>
      <div className="tableWrap">
        <table>
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
                <td>{u.lastName} {u.firstName}</td>
                <td>{u.email ?? u.phone ?? "—"}</td>
                <td>
                  <select value={u.role} onChange={(e) => void updateRole(u.id, e.target.value as UserResponseDto["role"])}>
                    <option value="USER">USER</option>
                    <option value="FOREMAN">FOREMAN</option>
                    <option value="ADMIN">ADMIN</option>
                  </select>
                </td>
                <td>
                  <select value={u.status} onChange={(e) => void updateStatus(u.id, e.target.value as UserResponseDto["status"])}>
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="INACTIVE">INACTIVE</option>
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function ReportsTab({ onError }: { onError: (msg: string | null) => void }) {
  const today = new Date();
  const weekAgo = new Date(today.getTime() - 6 * 24 * 60 * 60 * 1000);
  const [from, setFrom] = useState(isoDate(weekAgo));
  const [to, setTo] = useState(isoDate(today));
  const [data, setData] = useState<HoursReportResponseDto | null>(null);
  const [loading, setLoading] = useState(false);

  const sortedRows = useMemo(() => {
    return [...(data?.rows ?? [])].sort((a, b) => {
      if (b.hours !== a.hours) return b.hours - a.hours;
      return `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`);
    });
  }, [data]);

  async function build() {
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
    <section className="card">
      <div className="sectionHeader">
        <h2>Reports</h2>
      </div>

      <div className="form row">
        <label>
          From
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label>
          To
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button onClick={() => void build()} disabled={loading}>{loading ? "Building..." : "Build report"}</button>
        <button className="secondary" onClick={() => void downloadXlsx()}>Download XLSX</button>
      </div>

      {data && (
        <>
          <div className="notice">
            <span>
              Period: {data.fromDate ?? from} — {data.toDate ?? to} · Timezone: {data.timezone} · Shift norm: {data.shiftNormHours}h
            </span>
          </div>
          <div className="tableWrap">
            <table>
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
                    <td>{r.lastName} {r.firstName}</td>
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
  );
}

function ProfileTab({ user }: { user: UserResponseDto }) {
  return (
    <section className="card">
      <h2>Profile</h2>
      <p><strong>Name:</strong> {user.firstName} {user.lastName}</p>
      <p><strong>Role:</strong> {user.role}</p>
      <p><strong>Status:</strong> {user.status}</p>
      <p><strong>Email:</strong> {user.email ?? "—"}</p>
      <p><strong>Phone:</strong> {user.phone ?? "—"}</p>
    </section>
  );
}

