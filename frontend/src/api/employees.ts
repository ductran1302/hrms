import client from './client';
import type {
  Employee,
  EmployeeCreate,
  EmployeeQuery,
  EmployeeUpdate,
  JobHistoryItem,
  PageResponse,
} from './types';

export function searchEmployees(params: EmployeeQuery = {}): Promise<PageResponse<Employee>> {
  const query: Record<string, string | number> = {};
  if (params.page !== undefined) query.page = params.page;
  if (params.size !== undefined) query.size = params.size;
  if (params.status) query.status = params.status;
  if (params.departmentId) query.departmentId = params.departmentId;
  if (params.q) query.q = params.q;
  return client.get<PageResponse<Employee>>('/api/v1/employees', { params: query }).then((r) => r.data);
}

export function getEmployee(id: string): Promise<Employee> {
  return client.get<Employee>(`/api/v1/employees/${id}`).then((r) => r.data);
}

export function getEmployeeHistory(id: string): Promise<JobHistoryItem[]> {
  return client.get<JobHistoryItem[]>(`/api/v1/employees/${id}/history`).then((r) => r.data);
}

export function createEmployee(data: EmployeeCreate): Promise<Employee> {
  return client.post<Employee>('/api/v1/employees', data).then((r) => r.data);
}

export function updateEmployee(id: string, data: EmployeeUpdate): Promise<Employee> {
  return client.patch<Employee>(`/api/v1/employees/${id}`, data).then((r) => r.data);
}

export function terminateEmployee(id: string, reason?: string): Promise<void> {
  return client
    .delete(`/api/v1/employees/${id}`, { params: reason ? { reason } : undefined })
    .then(() => undefined);
}
