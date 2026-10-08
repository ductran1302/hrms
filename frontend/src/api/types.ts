export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export type EmployeeStatusValue = 'ACTIVE' | 'PROBATION' | 'TERMINATED';

export interface Employee {
  id: string;
  employeeCode: string;
  fullName: string;
  email: string | null;
  departmentId: string | null;
  departmentName: string | null;
  position: string | null;
  managerId: string | null;
  status: EmployeeStatusValue;
  hiredAt: string | null;
  birthDate: string | null;
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null;
  phone: string | null;
  nationalId: string | null;
  address: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface EmployeeCreate {
  employeeCode: string;
  fullName: string;
  email?: string;
  departmentId?: string;
  position?: string;
  managerId?: string;
  hiredAt?: string;
  birthDate?: string;
  gender?: string;
  phone?: string;
  nationalId?: string;
  address?: string;
}

export interface EmployeeUpdate {
  fullName?: string;
  email?: string;
  departmentId?: string;
  clearDepartment?: boolean;
  position?: string;
  managerId?: string;
  clearManager?: boolean;
  status?: string;
  birthDate?: string;
  gender?: string;
  phone?: string;
  nationalId?: string;
  address?: string;
}

export interface EmployeeQuery {
  page?: number;
  size?: number;
  status?: string;
  departmentId?: string;
  q?: string;
}

export interface Department {
  id: string;
  code: string;
  name: string;
  parentId: string | null;
  managerId: string | null;
  managerName: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  employeeCount: number;
}

export interface DepartmentCreate {
  code: string;
  name: string;
  parentId?: string;
  managerId?: string;
}

export interface DepartmentUpdate {
  code?: string;
  name?: string;
  parentId?: string;
  managerId?: string;
  status?: string;
}

export interface DepartmentNode {
  id: string;
  code: string;
  name: string;
  status: string;
  managerName: string | null;
  children: DepartmentNode[];
}

export type ContractTypeValue =
  | 'PROBATION'
  | 'FULL_TIME'
  | 'PART_TIME'
  | 'INTERN'
  | 'OTHER';

export type ContractStatusValue = 'ACTIVE' | 'EXPIRED' | 'TERMINATED';

export interface Contract {
  id: string;
  employeeId: string;
  employeeCode: string | null;
  employeeName: string | null;
  contractType: ContractTypeValue;
  startDate: string;
  endDate: string | null;
  position: string | null;
  /** null khi caller không phải HR_ADMIN (backend che lương) */
  salary: number | null;
  signDate: string | null;
  note: string | null;
  status: ContractStatusValue;
}

export interface ContractCreate {
  contractType: ContractTypeValue;
  startDate: string;
  endDate?: string;
  position?: string;
  salary?: number;
  signDate?: string;
  note?: string;
}

export interface ContractUpdate {
  contractType?: ContractTypeValue;
  startDate?: string;
  endDate?: string;
  position?: string;
  salary?: number;
  signDate?: string;
  note?: string;
  status?: ContractStatusValue;
}

export interface JobHistoryItem {
  id: string;
  employeeId: string;
  effectiveDate: string;
  field: string;
  oldValue: string | null;
  newValue: string | null;
  actorId: string | null;
}

export interface OrgChartNode {
  id: string;
  employeeCode: string;
  fullName: string;
  position: string | null;
  departmentName: string | null;
  status: string;
  children: OrgChartNode[];
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
