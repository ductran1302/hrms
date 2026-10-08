import client from './client';
import type { Department, DepartmentCreate, DepartmentNode, DepartmentUpdate } from './types';

export function listDepartments(): Promise<Department[]> {
  return client.get<Department[]>('/api/v1/departments').then((r) => r.data);
}

export function getDepartmentTree(): Promise<DepartmentNode[]> {
  return client.get<DepartmentNode[]>('/api/v1/departments/tree').then((r) => r.data);
}

export function createDepartment(data: DepartmentCreate): Promise<Department> {
  return client.post<Department>('/api/v1/departments', data).then((r) => r.data);
}

export function updateDepartment(id: string, data: DepartmentUpdate): Promise<Department> {
  return client.patch<Department>(`/api/v1/departments/${id}`, data).then((r) => r.data);
}

export function deactivateDepartment(id: string): Promise<void> {
  return client.delete(`/api/v1/departments/${id}`).then(() => undefined);
}
