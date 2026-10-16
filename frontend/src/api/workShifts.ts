import client from './client';
import type {
  ShiftAssignment,
  ShiftAssignmentCreate,
  WorkShift,
  WorkShiftUpsert,
} from './types';

export function listWorkShifts(params?: {
  q?: string;
  type?: string;
  active?: boolean;
}): Promise<WorkShift[]> {
  return client
    .get<WorkShift[]>('/api/v1/attendance/work-shifts', { params })
    .then((r) => r.data);
}

export function createWorkShift(data: WorkShiftUpsert): Promise<WorkShift> {
  return client.post<WorkShift>('/api/v1/attendance/work-shifts', data).then((r) => r.data);
}

export function updateWorkShift(id: string, data: Partial<WorkShiftUpsert>): Promise<WorkShift> {
  return client.patch<WorkShift>(`/api/v1/attendance/work-shifts/${id}`, data).then((r) => r.data);
}

export function deleteWorkShift(id: string): Promise<void> {
  return client.delete(`/api/v1/attendance/work-shifts/${id}`).then((r) => r.data);
}

export function listShiftAssignments(params?: {
  employeeId?: string;
  q?: string;
}): Promise<ShiftAssignment[]> {
  return client
    .get<ShiftAssignment[]>('/api/v1/attendance/shift-assignments', { params })
    .then((r) => r.data);
}

export function assignShift(data: ShiftAssignmentCreate): Promise<ShiftAssignment> {
  return client
    .post<ShiftAssignment>('/api/v1/attendance/shift-assignments', data)
    .then((r) => r.data);
}

export function removeShiftAssignment(id: string): Promise<void> {
  return client.delete(`/api/v1/attendance/shift-assignments/${id}`).then((r) => r.data);
}

export function getMyShift(date?: string): Promise<ShiftAssignment> {
  return client
    .get<ShiftAssignment>('/api/v1/attendance/shift-assignments/my', { params: { date } })
    .then((r) => r.data);
}
