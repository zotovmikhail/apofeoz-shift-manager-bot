export type UserRole = "USER" | "FOREMAN" | "ADMIN";
export type UserStatus = "ACTIVE" | "INACTIVE";
export type WorkerStatus = "ACTIVE" | "INACTIVE";

export type Tokens = {
  accessToken: string;
  refreshToken: string;
};

export type LoginRequest = {
  login: string;
  password: string;
};

export type RegisterRequest = {
  email?: string;
  phone?: string;
  firstName: string;
  lastName: string;
  password: string;
};

export type UserResponseDto = {
  id: string;
  email: string | null;
  phone: string | null;
  firstName: string;
  lastName: string;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
};

export type WorkerResponseDto = {
  id: string;
  firstName: string;
  lastName: string;
  position: string | null;
  status: WorkerStatus;
  foremanId: string;
  foremanDisplayName: string | null;
  userId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PatchUserRequestDto = {
  role?: UserRole;
  status?: UserStatus;
  firstName?: string;
  lastName?: string;
};

export type CreateWorkerRequestDto = {
  firstName: string;
  lastName: string;
  position?: string;
  foremanId?: string;
};

export type PatchWorkerRequestDto = {
  foremanId?: string;
  status?: WorkerStatus;
};

export type ReportRowDto = {
  workerId: string;
  firstName: string;
  lastName: string;
  foremanId: string;
  foremanDisplayName: string | null;
  hours: number;
  shiftEquivalent: number;
};

export type HoursReportResponseDto = {
  date?: string | null;
  fromDate?: string | null;
  toDate?: string | null;
  timezone: string;
  shiftNormHours: number;
  rows: ReportRowDto[];
  totals: {
    hours: number;
    shiftEquivalent: number;
  };
};

export type TimesheetWorkerDto = {
  workerId: string;
  firstName: string;
  lastName: string;
  foremanId: string;
  foremanDisplayName: string | null;
};

export type TimesheetDayCellDto = {
  workerId: string;
  hours: number;
  shiftEquivalent: number;
};

export type TimesheetDayRowDto = {
  date: string;
  cells: TimesheetDayCellDto[];
};

export type TimesheetReportResponseDto = {
  title: string;
  fromDate: string;
  toDate: string;
  timezone: string;
  shiftNormHours: number;
  workers: TimesheetWorkerDto[];
  rows: TimesheetDayRowDto[];
};

export type ApiError = {
  code?: string;
  message?: string;
  details?: Record<string, unknown>;
};
