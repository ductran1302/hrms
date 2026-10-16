import client from './client';
import type {
  LeaveEntitlement,
  LeaveGrant,
  LeavePolicy,
  LeavePolicyUpsert,
  LeaveQuota,
} from './types';

export function getLeavePolicy(year: number): Promise<LeavePolicy> {
  return client.get<LeavePolicy>(`/api/v1/attendance/leave/policies/${year}`).then((r) => r.data);
}

export function listLeavePolicies(): Promise<LeavePolicy[]> {
  return client.get<LeavePolicy[]>('/api/v1/attendance/leave/policies').then((r) => r.data);
}

export function saveLeavePolicy(data: LeavePolicyUpsert): Promise<LeavePolicy> {
  return client.post<LeavePolicy>('/api/v1/attendance/leave/policies', data).then((r) => r.data);
}

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
