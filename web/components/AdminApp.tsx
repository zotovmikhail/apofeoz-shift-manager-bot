"use client";

import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import {
  bootstrapSession,
  createWorker,
  downloadTimesheetXlsx,
  login,
  logout,
  patchUser,
  patchWorker,
  register,
  reportRange,
  timesheet,
  users,
  workers,
} from "../lib/api";
import type { HoursReportResponseDto, TimesheetDayCellDto, TimesheetReportResponseDto, UserResponseDto, WorkerResponseDto } from "../lib/types";

type TabKey = "workers" | "users" | "reports" | "profile";

type SummaryMetric = {
  label: string;
  value: string;
  tone?: "accent" | "neutral";
};

const TAB_ORDER: TabKey[] = ["workers", "users", "reports", "profile"];

const TAB_META: Record<
  TabKey,
  {
    label: string;
    eyebrow: string;
    title: string;
    description: string;
    icon: ReactNode;
  }
> = {
  workers: {
    label: "Рабочие",
    eyebrow: "Состав бригад",
    title: "Карточки рабочих и назначение по бригадирам",
    description: "Создавайте карточки, быстро переназначайте рабочих и держите фактический состав бригад в одном экране.",
    icon: <GridIcon />,
  },
  users: {
    label: "Пользователи",
    eyebrow: "Доступ и роли",
    title: "Роли, статусы и административный контроль",
    description: "Меняйте роли ПОЛЬЗОВАТЕЛЬ / БРИГАДИР / АДМИН, блокируйте доступ и сразу видьте текущую структуру аккаунтов.",
    icon: <UsersIcon />,
  },
  reports: {
    label: "Отчёты",
    eyebrow: "Табель и аналитика",
    title: "Табельная матрица по диапазону дат",
    description: "Формируйте тот же табель, что уходит в XLSX, и просматривайте его прямо в браузере по колонкам и датам.",
    icon: <ReportIcon />,
  },
  profile: {
    label: "Профиль",
    eyebrow: "Текущая сессия",
    title: "Профиль администратора и контекст входа",
    description: "Проверьте, под какой учётной записью открыт web-console, и завершите сессию вручную в один клик.",
    icon: <ShieldIcon />,
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
          setError(e instanceof Error ? e.message : "Не удалось восстановить сессию");
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
          <p className="eyebrow">Запуск панели</p>
          <h1>Подготавливаем рабочее место администратора</h1>
          <p className="mutedText">Проверяем токены, восстанавливаем сессию и подключаем рабочие данные.</p>
        </section>
      </main>
    );
  }

  if (!user) return <LoginForm onLogin={setUser} />;
  if (user.role !== "ADMIN") {
    async function forceLogout() {
      await logout();
      setUser(null);
    }

    return (
      <main className="shell shellCentered">
        <section className="spotlightPanel narrowPanel">
          <p className="eyebrow">Ограничение доступа</p>
          <h1>Нужна роль ADMIN</h1>
          <p className="mutedText">Эта web-панель доступна только администраторам. Войдите под учётной записью с ролью `ADMIN`.</p>
          <div className="spotlightActions">
            <button type="button" className="ghostButton" onClick={() => void forceLogout()}>
              Выйти
            </button>
          </div>
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
  const [mode, setMode] = useState<"login" | "register">("login");
  const [loginValue, setLoginValue] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const trimmedLogin = loginValue.trim();
      const u = mode === "login"
        ? await login({ login: trimmedLogin, password })
        : await register({
            login: trimmedLogin,
            firstName: firstName.trim(),
            lastName: lastName.trim(),
            password,
          });
      onLogin(u);
    } catch (err) {
      setError(err instanceof Error ? err.message : mode === "login" ? "Не удалось войти" : "Не удалось зарегистрироваться");
    } finally {
      setLoading(false);
    }
  }

  function switchMode(nextMode: "login" | "register") {
    setMode(nextMode);
    setError(null);
  }

  return (
    <main className="shell shellCentered">
      <section className="loginHero">
        <div className="loginBrand">
          <span className="brandBadge">АПОФЕОЗ</span>
          <p className="eyebrow">Web-админка</p>
          <h1>Единый центр управления сменами и табелем.</h1>
          <p className="mutedText">
            Темный графит, золотые сигналы и та же бизнес-логика, что уже работает в мобильной админке и backend API.
          </p>
          <div className="metricRail">
            <MetricCard label="Контур" value="Админ-панель" tone="accent" />
            <MetricCard label="Авторизация" value="JWT + refresh" />
            <MetricCard label="Табель" value="Live + XLSX" />
          </div>
        </div>

        <div className="loginCard">
          <div className="cardHeader">
            <div>
              <p className="eyebrow">{mode === "login" ? "Вход" : "Регистрация"}</p>
              <h2>{mode === "login" ? "Защищённый доступ" : "Новая учётная запись"}</h2>
            </div>
          </div>
          <div className="authModeToggle" role="tablist" aria-label="Режим авторизации">
            <button
              type="button"
              className={mode === "login" ? "authModeButton active" : "authModeButton"}
              onClick={() => switchMode("login")}
            >
              Вход
            </button>
            <button
              type="button"
              className={mode === "register" ? "authModeButton active" : "authModeButton"}
              onClick={() => switchMode("register")}
            >
              Регистрация
            </button>
          </div>
          <form onSubmit={submit} className="stackForm">
            {mode === "register" && (
              <>
                <label className="field">
                  <span>Имя</span>
                  <input value={firstName} onChange={(e) => setFirstName(e.target.value)} placeholder="имя" required />
                </label>
                <label className="field">
                  <span>Фамилия</span>
                  <input value={lastName} onChange={(e) => setLastName(e.target.value)} placeholder="фамилия" required />
                </label>
              </>
            )}
            <label className="field">
              <span>Логин</span>
              <input
                type="text"
                value={loginValue}
                onChange={(e) => setLoginValue(e.target.value)}
                placeholder={mode === "login" ? "логин" : "логин для входа"}
                required
              />
            </label>
            <label className="field">
              <span>Пароль</span>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="пароль" required />
            </label>
            <button type="submit" className="primaryButton" disabled={loading}>
              {loading
                ? mode === "login" ? "Входим..." : "Создаём учётную запись..."
                : mode === "login" ? "Открыть админ-консоль" : "Создать учётную запись"}
            </button>
          </form>
          <p className="authModeHint">
            {mode === "login"
              ? "Если учётной записи ещё нет, создайте её здесь и затем администратор сможет выдать нужную роль."
              : "После регистрации вы сразу войдёте в систему. Для доступа в web-админку нужна роль ADMIN."}
          </p>
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
            <span className="brandBadge">АПОФЕОЗ</span>
            <div>
              <p className="eyebrow">Админ-сессия</p>
              <h1 className="sidebarTitle">Пульт управления</h1>
            </div>
            <p className="mutedText">
              {user.firstName} {user.lastName}
            </p>
          </div>

          <nav className="sidebarNav">
            {TAB_ORDER.map((key) => {
              const meta = TAB_META[key];
              return (
                <button
                  key={key}
                  className={tab === key ? "navCard active" : "navCard"}
                  onClick={() => setTab(key)}
                >
                  <span className="navIcon">{meta.icon}</span>
                  <span className="navLabel">{meta.label}</span>
                  <span className="navDescription">{meta.eyebrow}</span>
                </button>
              );
            })}
          </nav>

          <div className="sidebarFooter">
            <div className="userChip">
              <span className="userInitials">{initials(user)}</span>
              <div>
                <strong>{roleTitle(user.role)}</strong>
                <p>{statusTitle(user.status)}</p>
              </div>
            </div>
            <button className="ghostButton" onClick={doLogout}>Выйти</button>
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
              <MetricCard label="Оператор" value={`${user.firstName} ${user.lastName}`} />
              <MetricCard label="Роль" value={roleTitle(user.role)} tone="accent" />
              <MetricCard label="Состояние" value={statusTitle(user.status)} />
            </div>
          </header>

          {globalError && (
            <div className="noticeCard danger">
              <div>
                <strong>Ошибка запроса</strong>
                <p>{globalError}</p>
              </div>
              <button className="ghostButton" onClick={clearError}>Закрыть</button>
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
      onError(e instanceof Error ? e.message : "Не удалось загрузить список рабочих");
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
      onError(err instanceof Error ? err.message : "Не удалось создать карточку рабочего");
    }
  }

  async function toggleStatus(row: WorkerResponseDto) {
    onError(null);
    try {
      await patchWorker(row.id, { status: row.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" });
      await load();
    } catch (err) {
      onError(err instanceof Error ? err.message : "Не удалось изменить статус рабочего");
    }
  }

  async function reassign(workerId: string, nextForemanId: string) {
    onError(null);
    try {
      await patchWorker(workerId, { foremanId: nextForemanId });
      await load();
    } catch (err) {
      onError(err instanceof Error ? err.message : "Не удалось переназначить рабочего");
    }
  }

  return (
    <section className="contentStack">
      <div className="metricRail">
        <MetricCard label="Всего рабочих" value={String(items.length)} tone="accent" />
        <MetricCard label="Активных" value={String(activeCount)} />
        <MetricCard label="Активных бригадиров" value={String(foremen.length)} />
      </div>

      <section className="contentCard">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">Новая карточка</p>
            <h3>Добавить рабочего</h3>
          </div>
          <button className="ghostButton" onClick={() => void load()} disabled={loading}>
            {loading ? "Обновляем..." : "Обновить данные"}
          </button>
        </div>

        <form onSubmit={submitCreate} className="formGrid">
          <label className="field">
            <span>Имя</span>
            <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
          </label>
          <label className="field">
            <span>Фамилия</span>
            <input value={lastName} onChange={(e) => setLastName(e.target.value)} required />
          </label>
          <label className="field">
            <span>Должность</span>
            <input value={position} onChange={(e) => setPosition(e.target.value)} placeholder="необязательно" />
          </label>
          <label className="field">
            <span>Бригадир</span>
            <select value={foremanId} onChange={(e) => setForemanId(e.target.value)} required>
              <option value="" disabled>Выберите бригадира</option>
              {foremen.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.firstName} {f.lastName}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="primaryButton">Создать рабочего</button>
        </form>
      </section>

      <section className="contentCard">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">Список</p>
            <h3>Текущий состав</h3>
          </div>
        </div>
        <div className="tableShell">
          <table className="dataTable">
            <thead>
              <tr>
                <th>Рабочий</th>
                <th>Должность</th>
                <th>Статус</th>
                <th>Бригадир</th>
                <th>Действие</th>
              </tr>
            </thead>
            <tbody>
              {items.map((w) => (
                <tr key={w.id}>
                  <td>
                    <div className="tableIdentity">
                      <strong>{w.lastName} {w.firstName}</strong>
                      <span>{w.userId ? "Связан с учётной записью" : "Отдельная карточка рабочего"}</span>
                    </div>
                  </td>
                  <td>{w.position ?? "—"}</td>
                  <td>
                    <StatusPill tone={w.status === "ACTIVE" ? "ok" : "muted"}>{statusTitle(w.status)}</StatusPill>
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
                      {w.status === "ACTIVE" ? "Деактивировать" : "Активировать"}
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
      onError(e instanceof Error ? e.message : "Не удалось загрузить пользователей");
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
      onError(e instanceof Error ? e.message : "Не удалось изменить роль пользователя");
    }
  }

  async function updateStatus(id: string, status: UserResponseDto["status"]) {
    onError(null);
    try {
      await patchUser(id, { status });
      await load();
    } catch (e) {
      onError(e instanceof Error ? e.message : "Не удалось изменить статус пользователя");
    }
  }

  return (
    <section className="contentStack">
      <div className="metricRail">
        <MetricCard label="Всего пользователей" value={String(items.length)} tone="accent" />
        <MetricCard label="Активных админов" value={String(adminsCount)} />
        <MetricCard label="Бригадиров" value={String(items.filter((item) => item.role === "FOREMAN").length)} />
      </div>

      <section className="contentCard">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">Матрица доступа</p>
            <h3>Роли и статусы учётных записей</h3>
          </div>
          <button className="ghostButton" onClick={() => void load()} disabled={loading}>
            {loading ? "Обновляем..." : "Обновить данные"}
          </button>
        </div>
        <div className="tableShell">
          <table className="dataTable">
            <thead>
              <tr>
                <th>Пользователь</th>
                <th>Контакт</th>
                <th>Роль</th>
                <th>Статус</th>
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
                      <option value="USER">ПОЛЬЗОВАТЕЛЬ</option>
                      <option value="FOREMAN">БРИГАДИР</option>
                      <option value="ADMIN">АДМИН</option>
                    </select>
                  </td>
                  <td>
                    <div className="statusControl">
                      <StatusPill tone={u.status === "ACTIVE" ? "ok" : "muted"}>{statusTitle(u.status)}</StatusPill>
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
  const [timesheetData, setTimesheetData] = useState<TimesheetReportResponseDto | null>(null);
  const [selectedCellKey, setSelectedCellKey] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const today = new Date();
    const weekAgo = new Date(today.getTime() - 6 * 24 * 60 * 60 * 1000);
    setFrom(toIsoDate(weekAgo));
    setTo(toIsoDate(today));
  }, []);

  const totalWorkers = timesheetData?.workers.length ?? 0;
  const totalDays = timesheetData?.rows.length ?? 0;
  const activeCellCount = useMemo(() => {
    return timesheetData?.rows.reduce((sum, row) => sum + row.cells.filter((cell) => cell.hours > 0).length, 0) ?? 0;
  }, [timesheetData]);
  const maxHoursInGrid = useMemo(() => {
    return timesheetData?.rows.flatMap((row) => row.cells).reduce((max, cell) => Math.max(max, cell.hours), 0) ?? 0;
  }, [timesheetData]);
  const selectedCell = useMemo(() => {
    if (!timesheetData || !selectedCellKey) return null;
    for (const row of timesheetData.rows) {
      for (const cell of row.cells) {
        const key = `${row.date}:${cell.workerId}`;
        if (key === selectedCellKey) {
          const worker = timesheetData.workers.find((item) => item.workerId === cell.workerId) ?? null;
          return { row, cell, worker };
        }
      }
    }
    return null;
  }, [timesheetData, selectedCellKey]);
  const workerTotals = useMemo(() => {
    if (!timesheetData) return new Map<string, { hours: number; shifts: number }>();
    const totals = new Map<string, { hours: number; shifts: number }>();
    for (const worker of timesheetData.workers) {
      totals.set(worker.workerId, { hours: 0, shifts: 0 });
    }
    for (const row of timesheetData.rows) {
      for (const cell of row.cells) {
        const current = totals.get(cell.workerId);
        if (!current) continue;
        current.hours += cell.hours;
        current.shifts += cell.shiftEquivalent;
      }
    }
    return totals;
  }, [timesheetData]);

  async function build() {
    if (!from || !to) return;
    setLoading(true);
    onError(null);
    try {
      const [jsonReport, xlsxTable] = await Promise.all([
        reportRange(from, to),
        timesheet(from, to),
      ]);
      setData(jsonReport);
      setTimesheetData(xlsxTable);
      setSelectedCellKey(null);
    } catch (e) {
      onError(e instanceof Error ? e.message : "Не удалось построить отчет");
    } finally {
      setLoading(false);
    }
  }

  async function downloadXlsx() {
    if (!from || !to) return;
    onError(null);
    try {
      const blob = await downloadTimesheetXlsx(from, to);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `timesheet_${from}_${to}.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      onError(e instanceof Error ? e.message : "Не удалось скачать XLSX");
    }
  }

  return (
    <section className="contentStack">
      <div className="metricRail">
        <MetricCard label="Дата начала" value={from || "—"} tone="accent" />
        <MetricCard label="Дата конца" value={to || "—"} />
        <MetricCard label="Работников" value={String(totalWorkers)} />
        <MetricCard label="Дней" value={String(totalDays)} />
        <MetricCard label="Активных ячеек" value={String(activeCellCount)} />
        <MetricCard label="Всего часов" value={data ? data.totals.hours.toFixed(1) : "—"} tone="accent" />
        <MetricCard label="Всего смен" value={data ? data.totals.shiftEquivalent.toFixed(3) : "—"} />
      </div>

      <section className="contentCard">
        <div className="sectionHeading">
          <div>
            <p className="eyebrow">Построение табеля</p>
            <h3>Command Center Grid</h3>
          </div>
        </div>

        <div className="formGrid reportControls">
          <label className="field">
            <span>С</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="field">
            <span>По</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
          <button className="primaryButton" onClick={() => void build()} disabled={loading || !from || !to}>
            {loading ? "Строим..." : "Построить отчет"}
          </button>
          <button className="ghostButton" onClick={() => void downloadXlsx()} disabled={!from || !to}>
            Скачать XLSX
          </button>
        </div>

        {data && (
          <div className="noticeCard">
            <div>
              <strong>{data.fromDate ?? from} — {data.toDate ?? to}</strong>
              <p>
                Таймзона {data.timezone} · Норма смены {data.shiftNormHours} ч · Итого {data.totals.hours.toFixed(1)} ч /{" "}
                {data.totals.shiftEquivalent.toFixed(3)} смен
              </p>
            </div>
          </div>
        )}

        {timesheetData && (
          <div className="timesheetWrap">
            <div className="timesheetCaption">
              <p className="eyebrow">Операционная матрица</p>
              <h4>{timesheetData.title}</h4>
              <p className="mutedText">
                Таблица больше не копирует Excel буквально. Она уже построена как рабочая матрица под будущий edit mode для администратора.
              </p>
            </div>
            <div className="timesheetExecutiveGrid">
              <div className="timesheetBoard">
                <div className="timesheetBoardToolbar">
                  <div className="timesheetBoardMeta">
                    <span>Период: {timesheetData.fromDate} - {timesheetData.toDate}</span>
                    <span>Таймзона: {timesheetData.timezone}</span>
                    <span>Норма смены: {timesheetData.shiftNormHours} ч</span>
                  </div>
                  <div className="timesheetBoardLegend">
                    <span className="legendSwatch low" />
                    <span>тихо</span>
                    <span className="legendSwatch mid" />
                    <span>средняя загрузка</span>
                    <span className="legendSwatch high" />
                    <span>плотная смена</span>
                  </div>
                </div>

                <div className="timesheetGridShell">
                  <div
                    className="timesheetGrid"
                    style={{ ["--timesheet-cols" as string]: String(timesheetData.workers.length) }}
                  >
                    <div className="timesheetGridCorner">
                      <span className="eyebrow">Дата</span>
                      <strong>Дневной срез</strong>
                    </div>
                    {timesheetData.workers.map((worker) => (
                      <div key={worker.workerId} className="timesheetWorkerHead">
                        <span>{worker.foremanDisplayName ?? "Без бригадира"}</span>
                        <strong>{worker.lastName} {worker.firstName}</strong>
                      </div>
                    ))}

                    <div className="timesheetSummaryLabel">
                      <span className="eyebrow">Итого</span>
                      <strong>По работнику</strong>
                    </div>
                    {timesheetData.workers.map((worker) => {
                      const totals = workerTotals.get(worker.workerId) ?? { hours: 0, shifts: 0 };
                      return (
                        <div key={`${worker.workerId}-summary`} className="timesheetSummaryCell">
                          <span>{totals.shifts.toFixed(3)} смен</span>
                          <strong>{totals.hours.toFixed(1)} ч</strong>
                        </div>
                      );
                    })}

                    {timesheetData.rows.flatMap((row) => [
                      <div key={`${row.date}-label`} className="timesheetDateCol">
                        <span>{formatReportDate(row.date)}</span>
                        <strong>{formatWeekday(row.date)}</strong>
                      </div>,
                      ...row.cells.map((cell) => {
                        const key = `${row.date}:${cell.workerId}`;
                        const worker = timesheetData.workers.find((item) => item.workerId === cell.workerId) ?? null;
                        return (
                          <button
                            key={key}
                            type="button"
                            className={cellClassName(cell, key === selectedCellKey, maxHoursInGrid)}
                            onClick={() => setSelectedCellKey(key)}
                            title={worker ? `${worker.lastName} ${worker.firstName}` : cell.workerId}
                          >
                            <span className="cellShift">{cell.shiftEquivalent > 0 ? cell.shiftEquivalent.toFixed(3) : "—"}</span>
                            <span className="cellHours">{cell.hours > 0 ? `${cell.hours.toFixed(1)} ч` : "пусто"}</span>
                          </button>
                        );
                      }),
                    ])}
                  </div>
                </div>
              </div>
            </div>

            {selectedCell && selectedCell.worker && (
              <>
                <button
                  type="button"
                  className="timesheetDrawerBackdrop"
                  onClick={() => setSelectedCellKey(null)}
                  aria-label="Закрыть детали ячейки"
                />
                <aside className="timesheetDrawer">
                  <div className="timesheetInspectorCard">
                    <div className="timesheetDrawerHead">
                      <div>
                        <p className="eyebrow">Inspector</p>
                        <h5>{selectedCell.worker.lastName} {selectedCell.worker.firstName}</h5>
                      </div>
                      <button type="button" className="ghostButton" onClick={() => setSelectedCellKey(null)}>
                        Закрыть
                      </button>
                    </div>
                    <p className="mutedText">{selectedCell.row.date} · {formatWeekday(selectedCell.row.date)}</p>
                    <div className="inspectorStats">
                      <div>
                        <span>Смены</span>
                        <strong>{selectedCell.cell.shiftEquivalent.toFixed(3)}</strong>
                      </div>
                      <div>
                        <span>Часы</span>
                        <strong>{selectedCell.cell.hours.toFixed(1)}</strong>
                      </div>
                    </div>
                    <div className="inspectorHints">
                      <span className="statusPill ok">Источник: JSON endpoint</span>
                      <span className="statusPill muted">Edit mode: planned</span>
                    </div>
                    <p className="mutedText">
                      Следующий этап: по клику на такую ячейку можно будет открывать реальные смены за день и давать админу точечное редактирование.
                    </p>
                  </div>
                </aside>
              </>
            )}
          </div>
        )}

      </section>
    </section>
  );
}

function cellClassName(cell: TimesheetDayCellDto, selected: boolean, maxHours: number): string {
  const parts = ["timesheetCellCard"];
  if (cell.hours <= 0) parts.push("empty");
  else if (maxHours > 0 && cell.hours >= maxHours * 0.7) parts.push("dense");
  else if (maxHours > 0 && cell.hours >= maxHours * 0.35) parts.push("medium");
  else parts.push("light");
  if (selected) parts.push("selected");
  return parts.join(" ");
}

function formatReportDate(raw: string): string {
  const date = new Date(`${raw}T00:00:00`);
  return new Intl.DateTimeFormat("ru-RU", { day: "2-digit", month: "short" }).format(date);
}

function formatWeekday(raw: string): string {
  const date = new Date(`${raw}T00:00:00`);
  return new Intl.DateTimeFormat("ru-RU", { weekday: "short" }).format(date);
}

function ProfileTab({ user }: { user: UserResponseDto }) {
  const metrics: SummaryMetric[] = [
    { label: "Роль", value: roleTitle(user.role), tone: "accent" },
    { label: "Статус", value: statusTitle(user.status) },
    { label: "Контакт", value: user.email ?? user.phone ?? "Нет контакта" },
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
            <p className="eyebrow">Текущий оператор</p>
            <h3>{user.firstName} {user.lastName}</h3>
            <p className="mutedText">
              Здесь видно, какая именно админская учётная запись сейчас работает в web-консоли.
            </p>
          </div>
        </div>

        <div className="profileGrid">
          <ProfileField label="ID пользователя" value={user.id} />
          <ProfileField label="Логин" value={user.email ?? user.phone ?? "—"} />
          <ProfileField label="Телефон" value={user.phone ?? "—"} />
          <ProfileField label="Роль" value={roleTitle(user.role)} />
          <ProfileField label="Статус" value={statusTitle(user.status)} />
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
        <strong>Ошибка</strong>
        <p>{text}</p>
      </div>
    </div>
  );
}

function GridIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="2" />
      <rect x="14" y="3" width="7" height="7" rx="2" />
      <rect x="3" y="14" width="7" height="7" rx="2" />
      <rect x="14" y="14" width="7" height="7" rx="2" />
    </svg>
  );
}

function UsersIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7.5 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z" />
      <path d="M16.5 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" />
      <path d="M3.5 19.5c0-2.5 2.3-4.5 5-4.5s5 2 5 4.5v1h-10v-1Z" />
      <path d="M14 20.5v-1c0-1.4-.5-2.6-1.3-3.6.6-.3 1.3-.4 2-.4 2.1 0 3.8 1.5 3.8 3.5v1.5H14Z" />
    </svg>
  );
}

function ReportIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6 3h8l4 4v14H6V3Z" />
      <path d="M14 3v5h5" />
      <path d="M9 12h6" />
      <path d="M9 16h6" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 3 5 6v5c0 5 3.2 8.8 7 10 3.8-1.2 7-5 7-10V6l-7-3Z" />
      <path d="M9.5 12.5 11 14l3.5-3.5" />
    </svg>
  );
}

function toIsoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function initials(user: UserResponseDto): string {
  return `${user.firstName[0] ?? ""}${user.lastName[0] ?? ""}`.toUpperCase() || "AD";
}

function roleTitle(role: UserResponseDto["role"]): string {
  switch (role) {
    case "ADMIN":
      return "АДМИН";
    case "FOREMAN":
      return "БРИГАДИР";
    default:
      return "ПОЛЬЗОВАТЕЛЬ";
  }
}

function statusTitle(status: "ACTIVE" | "INACTIVE"): string {
  return status === "ACTIVE" ? "Активен" : "Неактивен";
}
