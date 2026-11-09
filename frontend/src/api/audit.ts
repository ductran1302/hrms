import client from './client';
import type { AuditLog, PageResponse } from './types';

export function searchAuditLogs(params: { actor?: string; action?: string; page?: number; size?: number }): Promise<PageResponse<AuditLog>> {
  return client.get('/api/v1/audit/logs', { params }).then((r) => ({
    items: r.data.content ?? [],
    page: r.data.number ?? 0,
    size: r.data.size ?? 20,
    total: r.data.totalElements ?? 0,
  }));
}

export function searchAttendanceAuditLogs(params: { actor?: string; action?: string; page?: number; size?: number }): Promise<PageResponse<AuditLog>> {
  return client.get('/api/v1/attendance/audit-logs', { params }).then((r) => ({
    items: r.data.content ?? [],
    page: r.data.number ?? 0,
    size: r.data.size ?? 20,
    total: r.data.totalElements ?? 0,
  }));
}
