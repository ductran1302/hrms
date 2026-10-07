export interface Employee {
  id: string;
  employeeCode: string;
  fullName: string;
  email: string | null;
  departmentId: string | null;
  departmentName: string | null;
  position: string | null;
  managerId: string | null;
  status: 'ACTIVE' | 'PROBATION' | 'TERMINATED';
  hiredAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface EmployeeCreate {
  employeeCode: string;
  fullName: string;
  email?: string;
  departmentName?: string;
  position?: string;
  managerId?: string;
  hiredAt?: string;
}

export interface EmployeeUpdate {
  fullName?: string;
  email?: string;
  departmentName?: string;
  position?: string;
  managerId?: string;
  status?: string;
}

export interface AttendanceEmployee {
  employeeId: string;
  employeeCode: string;
  fullName: string;
  email: string | null;
  departmentId: string | null;
  departmentName: string | null;
  position: string | null;
  status: string;
  hiredAt: string | null;
  terminatedAt: string | null;
}

export interface ApiError {
  timestamp?: string;
  status?: number;
  error?: string;
  code?: string;
  message?: string;
  path?: string;
  correlationId?: string;
  fieldErrors?: { field: string; message: string }[];
}
