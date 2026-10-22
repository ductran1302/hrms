import client from './client';
import type {
  LeaveEntitlement,
  LeaveGrant,
  LeavePolicy,
  LeavePolicyCreate,
  LeaveQuota,
  LeaveSettings,
  LeaveWorkDay,
} from './types';

// ---- Phiên bản chính sách (effective-dated, 6 states) ----

/** Chính sách in-force của năm (self-heal: tự tạo DRAFT mặc định nếu chưa có). */
export function getLeavePolicy(year: number): Promise<LeavePolicy> {
  return client.get<LeavePolicy>(`/api/v1/attendance/leave/policies/${year}`).then((r) => r.data);
}

/** Lịch sử phiên bản — không truyền year = mọi năm. */
export function listLeavePolicies(year?: number): Promise<LeavePolicy[]> {
  return client
    .get<LeavePolicy[]>('/api/v1/attendance/leave/policies', { params: year ? { year } : undefined })
    .then((r) => r.data);
}

export function getLeavePolicyVersion(id: string): Promise<LeavePolicy> {
  return client.get<LeavePolicy>(`/api/v1/attendance/leave/policies/versions/${id}`).then((r) => r.data);
}

export function createLeavePolicy(data: LeavePolicyCreate): Promise<LeavePolicy> {
  return client.post<LeavePolicy>('/api/v1/attendance/leave/policies', data).then((r) => r.data);
}

/** Sửa phiên bản — CHỈ DRAFT (422 nếu khác). */
export function updateLeavePolicy(id: string, data: LeavePolicyCreate): Promise<LeavePolicy> {
  return client
    .put<LeavePolicy>(`/api/v1/attendance/leave/policies/versions/${id}`, data)
    .then((r) => r.data);
}

export function submitLeavePolicy(id: string): Promise<LeavePolicy> {
  return client.post<LeavePolicy>(`/api/v1/attendance/leave/policies/versions/${id}/submit`).then((r) => r.data);
}

export function approveLeavePolicy(id: string): Promise<LeavePolicy> {
  return client.post<LeavePolicy>(`/api/v1/attendance/leave/policies/versions/${id}/approve`).then((r) => r.data);
}

export function rejectLeavePolicy(id: string): Promise<LeavePolicy> {
  return client.post<LeavePolicy>(`/api/v1/attendance/leave/policies/versions/${id}/reject`).then((r) => r.data);
}

export function activateLeavePolicy(id: string): Promise<LeavePolicy> {
  return client.post<LeavePolicy>(`/api/v1/attendance/leave/policies/versions/${id}/activate`).then((r) => r.data);
}

export function cancelLeavePolicy(id: string): Promise<LeavePolicy> {
  return client.post<LeavePolicy>(`/api/v1/attendance/leave/policies/versions/${id}/cancel`).then((r) => r.data);
}

/** Xóa phiên bản — CHỈ DRAFT. */
export function deleteLeavePolicy(id: string): Promise<void> {
  return client.delete(`/api/v1/attendance/leave/policies/versions/${id}`).then((r) => r.data);
}

// ---- Tham số global + ghi chú + ngày công ----

export function getLeaveSettings(): Promise<LeaveSettings> {
  return client.get<LeaveSettings>('/api/v1/attendance/leave/settings').then((r) => r.data);
}

export function updateLeaveSettings(data: LeaveSettings): Promise<LeaveSettings> {
  return client.put<LeaveSettings>('/api/v1/attendance/leave/settings', data).then((r) => r.data);
}

export function getLeaveNote(employeeId: string, year: number): Promise<string> {
  return client
    .get<string>(`/api/v1/attendance/leave/quotas/${employeeId}/note`, { params: { year } })
    .then((r) => r.data);
}

export function saveLeaveNote(employeeId: string, year: number, note: string): Promise<void> {
  return client
    .put(`/api/v1/attendance/leave/quotas/${employeeId}/note`, { note }, { params: { year } })
    .then((r) => r.data);
}

export function listLeaveWorkDays(employeeId: string, year: number): Promise<LeaveWorkDay[]> {
  return client
    .get<LeaveWorkDay[]>(`/api/v1/attendance/leave/quotas/${employeeId}/work-days`, {
      params: { year },
    })
    .then((r) => r.data);
}

export function upsertLeaveWorkDay(
  employeeId: string,
  data: { periodStart: string; workDays: number },
): Promise<LeaveWorkDay> {
  return client
    .put<LeaveWorkDay>(`/api/v1/attendance/leave/quotas/${employeeId}/work-days`, data)
    .then((r) => r.data);
}

// ---- Quota ----

export function listLeaveQuotas(params: { year: number; q?: string }): Promise<LeaveQuota[]> {
  return client
    .get<LeaveQuota[]>('/api/v1/attendance/leave/quotas', { params })
    .then((r) => r.data);
}

export function getLeaveQuota(employeeId: string, year: number): Promise<LeaveQuota> {
  return client
    .get<LeaveQuota>(`/api/v1/attendance/leave/quotas/${employeeId}`, { params: { year } })
    .then((r) => r.data);
}

export function recalculateLeaveQuotas(year: number): Promise<number> {
  return client
    .post<number>(`/api/v1/attendance/leave/quotas/recalculate`, undefined, { params: { year } })
    .then((r) => r.data);
}

export function grantLeaveEntitlement(
  employeeId: string,
  year: number,
  data: LeaveGrant,
): Promise<LeaveEntitlement> {
  return client
    .post<LeaveEntitlement>(
      `/api/v1/attendance/leave/quotas/${employeeId}/entitlements`,
      data,
      { params: { year } },
    )
    .then((r) => r.data);
}

export function deleteLeaveEntitlement(id: string): Promise<void> {
  return client.delete(`/api/v1/attendance/leave/entitlements/${id}`).then((r) => r.data);
}
