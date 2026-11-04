import client from './client';
import type { OTApproverInfo, OTConfig, OTConfigHistory, OTConfigUpdate, OTRequest, OTRequestCreate } from './types';

// Single source of truth cho type OT nằm trong api/types.ts
export type {
  OTApproverInfo,
  OTConfig,
  OTConfigHistory,
  OTConfigUpdate,
  OTRequest,
  OTRequestCreate,
  OTRequestItem,
  OTRequestItemCreate,
  OTRequestStatusValue,
} from './types';

export function getOTConfig(): Promise<OTConfig> {
  return client.get<OTConfig>('/api/v1/attendance/ot/config').then((r) => r.data);
}

export function updateOTConfig(payload: OTConfigUpdate): Promise<OTConfig> {
  return client.put<OTConfig>('/api/v1/attendance/ot/config', payload).then((r) => r.data);
}

export function getOTConfigHistory(page = 0, size = 20): Promise<{ items: OTConfigHistory[]; page: number; size: number; total: number }> {
  return client
    .get('/api/v1/attendance/ot/config/history', { params: { page: String(page), size: String(size) } })
    .then((r) => r.data);
}

export function listOTRequests(
  page = 0,
  size = 20,
  status?: string,
): Promise<{ items: OTRequest[]; page: number; size: number; total: number }> {
  const params: Record<string, string> = { page: String(page), size: String(size) };
  if (status) params.status = status;
  return client
    .get('/api/v1/attendance/ot/requests', { params })
    .then((r) => r.data);
}

/** Người duyệt trực tiếp (manager) của mình — null khi không có. */
export function getOTApprover(): Promise<OTApproverInfo | null> {
  return client.get<OTApproverInfo>('/api/v1/attendance/ot/approver').then((r) =>
    r.data && typeof r.data === 'object' ? r.data : null,
  );
}

export function createOTRequest(payload: OTRequestCreate): Promise<OTRequest> {
  return client.post<OTRequest>('/api/v1/attendance/ot/requests', payload).then((r) => r.data);
}

export function approveOTRequest(id: string, approved: boolean, reason?: string): Promise<void> {
  return client.patch(`/api/v1/attendance/ot/requests/${id}/approve`, null, {
    params: { approved, reason },
  });
}

export function cancelOTRequest(id: string, reason: string): Promise<void> {
  return client.patch(`/api/v1/attendance/ot/requests/${id}/cancel`, null, {
    params: { reason },
  }).then(() => undefined);
}

export function deleteOTRequest(id: string): Promise<void> {
  return client.delete(`/api/v1/attendance/ot/requests/${id}`).then(() => undefined);
}

export function updateOTRequest(id: string, payload: OTRequestCreate): Promise<OTRequest> {
  return client.put<OTRequest>(`/api/v1/attendance/ot/requests/${id}`, payload).then((r) => r.data);
}
