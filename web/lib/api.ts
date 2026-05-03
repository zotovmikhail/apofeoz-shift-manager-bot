import type {
  ApiError,
  CreateWorkerRequestDto,
  DeviceResponseDto,
  FailedBatchListItemDto,
  HoursReportResponseDto,
  LoginRequest,
  PatchUserRequestDto,
  PatchWorkerRequestDto,
  RegisterRequest,
  TimesheetReportResponseDto,
  Tokens,
  UserResponseDto,
  WorkerResponseDto,
} from "./types";

const REFRESH_KEY = "apofeoz_refresh_token";

let accessToken: string | null = null;

function apiBase(): string {
  return (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}

function refreshToken(): string | null {
  if (typeof window === "undefined") return null;
  const persistent = window.localStorage.getItem(REFRESH_KEY);
  if (persistent) return persistent;
  const legacySessionToken = window.sessionStorage.getItem(REFRESH_KEY);
  if (legacySessionToken) {
    window.localStorage.setItem(REFRESH_KEY, legacySessionToken);
    window.sessionStorage.removeItem(REFRESH_KEY);
  }
  return legacySessionToken;
}

function setTokens(tokens: Tokens) {
  accessToken = tokens.accessToken;
  if (typeof window !== "undefined") {
    window.localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    window.sessionStorage.removeItem(REFRESH_KEY);
  }
}

export function clearTokens() {
  accessToken = null;
  if (typeof window !== "undefined") {
    window.localStorage.removeItem(REFRESH_KEY);
    window.sessionStorage.removeItem(REFRESH_KEY);
  }
}

async function refreshAccess(): Promise<boolean> {
  const token = refreshToken();
  if (!token) return false;
  const res = await fetch(`${apiBase()}/api/v1/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: token }),
  });
  if (!res.ok) {
    clearTokens();
    return false;
  }
  const tokens = (await res.json()) as Tokens;
  setTokens(tokens);
  return true;
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  retry401 = true,
): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const res = await fetch(`${apiBase()}${path}`, { ...init, headers });
  if (res.status === 401 && retry401) {
    const ok = await refreshAccess();
    if (ok) return request<T>(path, init, false);
  }

  if (!res.ok) {
    let errMessage = `HTTP ${res.status}`;
    try {
      const body = (await res.json()) as ApiError;
      if (body.message) errMessage = body.message;
      if (body.code) errMessage = `${body.code}: ${errMessage}`;
    } catch {
      // ignore non-json responses
    }
    throw new Error(errMessage);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

async function requestRaw(
  path: string,
  init: RequestInit = {},
  retry401 = true,
): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const res = await fetch(`${apiBase()}${path}`, { ...init, headers });
  if (res.status === 401 && retry401) {
    const ok = await refreshAccess();
    if (ok) return requestRaw(path, init, false);
  }

  if (!res.ok) {
    let errMessage = `HTTP ${res.status}`;
    const contentType = res.headers.get("Content-Type") ?? "";
    try {
      if (contentType.includes("application/json")) {
        const body = (await res.json()) as ApiError;
        if (body.message) errMessage = body.message;
        if (body.code) errMessage = `${body.code}: ${errMessage}`;
      } else {
        const text = (await res.text()).trim();
        if (text) errMessage = text;
      }
    } catch {
      // ignore body parsing errors
    }
    throw new Error(errMessage);
  }

  return res;
}

export async function login(body: LoginRequest): Promise<UserResponseDto> {
  const tokens = await request<Tokens>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify(body),
  });
  setTokens(tokens);
  return me();
}

export async function register(body: RegisterRequest): Promise<UserResponseDto> {
  const tokens = await request<Tokens>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify(body),
  });
  setTokens(tokens);
  return me();
}

export async function logout(): Promise<void> {
  const token = refreshToken();
  if (token) {
    try {
      await request<void>("/api/v1/auth/logout", {
        method: "POST",
        body: JSON.stringify({ refreshToken: token }),
      });
    } catch {
      // ignore logout API errors
    }
  }
  clearTokens();
}

export async function bootstrapSession(): Promise<UserResponseDto | null> {
  const refreshed = await refreshAccess();
  if (!refreshed) return null;
  return me();
}

export async function me(): Promise<UserResponseDto> {
  return request<UserResponseDto>("/api/v1/users/me");
}

export async function users(): Promise<UserResponseDto[]> {
  return request<UserResponseDto[]>("/api/v1/users");
}

export async function patchUser(id: string, body: PatchUserRequestDto): Promise<UserResponseDto> {
  return request<UserResponseDto>(`/api/v1/users/${id}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function workers(): Promise<WorkerResponseDto[]> {
  return request<WorkerResponseDto[]>("/api/v1/workers");
}

export async function createWorker(body: CreateWorkerRequestDto): Promise<WorkerResponseDto> {
  return request<WorkerResponseDto>("/api/v1/workers", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function patchWorker(id: string, body: PatchWorkerRequestDto): Promise<WorkerResponseDto> {
  return request<WorkerResponseDto>(`/api/v1/workers/${id}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function reportRange(from: string, to: string): Promise<HoursReportResponseDto> {
  const q = new URLSearchParams({ from, to });
  return request<HoursReportResponseDto>(`/api/v1/reports/hours-by-worker-range?${q.toString()}`);
}

export async function timesheet(from: string, to: string): Promise<TimesheetReportResponseDto> {
  const q = new URLSearchParams({ from, to });
  return request<TimesheetReportResponseDto>(`/api/v1/reports/timesheet?${q.toString()}`);
}

export function timesheetDownloadUrl(from: string, to: string): string {
  const q = new URLSearchParams({ from, to });
  return `${apiBase()}/api/v1/reports/timesheet.xlsx?${q.toString()}`;
}

export async function downloadTimesheetXlsx(from: string, to: string): Promise<Blob> {
  const q = new URLSearchParams({ from, to });
  const res = await requestRaw(`/api/v1/reports/timesheet.xlsx?${q.toString()}`);
  return res.blob();
}

export async function devices(): Promise<DeviceResponseDto[]> {
  return request<DeviceResponseDto[]>("/api/v1/devices");
}

export async function failedBatches(): Promise<FailedBatchListItemDto[]> {
  return request<FailedBatchListItemDto[]>("/api/v1/sync/failed-batches");
}

export function authHeader(): Record<string, string> {
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
}
