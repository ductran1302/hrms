import client from './client';
import type {
  Department,
  DepartmentCreate,
  DepartmentNode,
  DepartmentUpdate,
  PageResponse,
} from './types';

export interface DepartmentQuery {
  page?: number;
  size?: number;
}

export function listDepartments(params?: DepartmentQuery): Promise<PageResponse<Department>> {
  return client.get<PageResponse<Department>>('/api/v1/departments', { params }).then((r) => r.data);
}

/** Nạp toàn bộ phòng ban theo từng trang (API giới hạn tối đa 200/trang). */
export async function listAllDepartments(): Promise<Department[]> {
  const all: Department[] = [];
  let page = 0;
  while (true) {
    const response = await listDepartments({ page, size: 200 });
    all.push(...response.items);
    if (response.items.length === 0 || all.length >= response.total) {
      return all;
    }
    page += 1;
  }
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
