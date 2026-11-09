import client from './client';
import type {
  RosterEntry,
  RosterOverride,
  ScheduleAssignment,
  ScheduleAssignmentCreate,
  WorkSchedulePolicy,
  WorkSchedulePolicyUpsert,
} from './types';

// ===== Roster (lịch làm việc) =====

export function listRosters(params: {
  employeeId?: string;
  employeeIds?: string[];
  from: string;
  to: string;
}): Promise<RosterEntry[]> {
  const { employeeIds, ...rest } = params;
  return client
    .get<RosterEntry[]>('/api/v1/attendance/rosters', {
      params: {
        ...rest,
        employeeIds: employeeIds && employeeIds.length > 0 ? employeeIds.join(',') : undefined,
      },
    })
    .then((r) => r.data);
}

/** Lịch 1 ngày — backend suy ra từ policy tuần, ưu tiên sửa tay. */
export function getRosterDay(employeeId: string, date: string): Promise<RosterEntry> {
  return client
    .get<RosterEntry>(`/api/v1/attendance/rosters/${employeeId}/${date}`)
    .then((r) => r.data);
}

/** Sửa tay 1 ngày (shiftId null = nghỉ) — chỉ từ hôm nay. */
export function overrideRoster(
  employeeId: string,
  date: string,
  data: RosterOverride
): Promise<RosterEntry> {
  return client
    .put<RosterEntry>(`/api/v1/attendance/rosters/${employeeId}/${date}`, data)
    .then((r) => r.data);
}

/** Bỏ sửa tay → quay về lịch theo policy. */
export function clearRosterOverride(employeeId: string, date: string): Promise<RosterEntry> {
  return client
    .delete<RosterEntry>(`/api/v1/attendance/rosters/${employeeId}/${date}`)
    .then((r) => r.data);
}

// ===== Schedule policies =====

export function listSchedulePolicies(params?: { active?: boolean }): Promise<WorkSchedulePolicy[]> {
  return client
    .get<WorkSchedulePolicy[]>('/api/v1/attendance/schedule-policies', { params })
    .then((r) => r.data);
}

export function createSchedulePolicy(
  data: WorkSchedulePolicyUpsert
): Promise<WorkSchedulePolicy> {
  return client
    .post<WorkSchedulePolicy>('/api/v1/attendance/schedule-policies', data)
    .then((r) => r.data);
}

export function updateSchedulePolicy(
  id: string,
  data: WorkSchedulePolicyUpsert
): Promise<WorkSchedulePolicy> {
  return client
    .put<WorkSchedulePolicy>(`/api/v1/attendance/schedule-policies/${id}`, data)
    .then((r) => r.data);
}

export function deleteSchedulePolicy(id: string): Promise<void> {
  return client.delete(`/api/v1/attendance/schedule-policies/${id}`).then((r) => r.data);
}

// ===== Schedule assignments =====

export function listScheduleAssignments(params?: {
  employeeId?: string;
}): Promise<ScheduleAssignment[]> {
  return client
    .get<ScheduleAssignment[]>('/api/v1/attendance/schedule-assignments', { params })
    .then((r) => r.data);
}

export function createScheduleAssignment(
  data: ScheduleAssignmentCreate
): Promise<ScheduleAssignment> {
  return client
    .post<ScheduleAssignment>('/api/v1/attendance/schedule-assignments', data)
    .then((r) => r.data);
}

export function deleteScheduleAssignment(id: string): Promise<void> {
  return client.delete(`/api/v1/attendance/schedule-assignments/${id}`).then((r) => r.data);
}
