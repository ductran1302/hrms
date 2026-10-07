import client from './client';
import type { Employee, EmployeeCreate, EmployeeUpdate } from './types';

export function listEmployees(status?: string): Promise<Employee[]> {
  return client
    .get<Employee[]>('/api/v1/employees', { params: status ? { status } : undefined })
    .then((r) => r.data);
}

export function createEmployee(data: EmployeeCreate): Promise<Employee> {
  return client.post<Employee>('/api/v1/employees', data).then((r) => r.data);
}

export function updateEmployee(id: string, data: EmployeeUpdate): Promise<Employee> {
  return client.patch<Employee>(`/api/v1/employees/${id}`, data).then((r) => r.data);
}

export function terminateEmployee(id: string, reason?: string): Promise<void> {
  return client.delete(`/api/v1/employees/${id}`, { params: reason ? { reason } : undefined }).then(() => undefined);
}
