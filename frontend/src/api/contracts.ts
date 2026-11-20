import client from './client';
import type { Contract, ContractCreate, ContractUpdate, PageResponse } from './types';

export interface ContractQuery {
  employeeId?: string;
  status?: string;
  page?: number;
  size?: number;
}

export function listContracts(params?: ContractQuery): Promise<PageResponse<Contract>> {
  return client.get<PageResponse<Contract>>('/api/v1/contracts', { params }).then((r) => r.data);
}

export function listExpiringContracts(
  days = 30,
  params?: { page?: number; size?: number },
): Promise<PageResponse<Contract>> {
  return client
    .get<PageResponse<Contract>>('/api/v1/contracts/expiring-soon', {
      params: { days, ...params },
    })
    .then((r) => r.data);
}

export function createContract(employeeId: string, data: ContractCreate): Promise<Contract> {
  return client.post<Contract>(`/api/v1/employees/${employeeId}/contracts`, data).then((r) => r.data);
}

export function updateContract(id: string, data: ContractUpdate): Promise<Contract> {
  return client.patch<Contract>(`/api/v1/contracts/${id}`, data).then((r) => r.data);
}

export function extendContract(
  id: string,
  data: { endDate: string; note?: string },
): Promise<Contract> {
  return client.post<Contract>(`/api/v1/contracts/${id}/extend`, data).then((r) => r.data);
}

export function replaceContract(id: string, data: ContractCreate): Promise<Contract> {
  return client.post<Contract>(`/api/v1/contracts/${id}/replace`, data).then((r) => r.data);
}

export function terminateContract(id: string, reason?: string): Promise<Contract> {
  return client
    .patch<Contract>(`/api/v1/contracts/${id}/terminate`, null, {
      params: reason ? { reason } : undefined,
    })
    .then((r) => r.data);
}
