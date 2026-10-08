import client from './client';
import type { Contract, ContractCreate, ContractUpdate } from './types';

export function listContracts(params?: { employeeId?: string; status?: string }): Promise<Contract[]> {
  return client.get<Contract[]>('/api/v1/contracts', { params }).then((r) => r.data);
}

export function createContract(employeeId: string, data: ContractCreate): Promise<Contract> {
  return client.post<Contract>(`/api/v1/employees/${employeeId}/contracts`, data).then((r) => r.data);
}

export function updateContract(id: string, data: ContractUpdate): Promise<Contract> {
  return client.patch<Contract>(`/api/v1/contracts/${id}`, data).then((r) => r.data);
}

export function terminateContract(id: string): Promise<Contract> {
  return client.patch<Contract>(`/api/v1/contracts/${id}/terminate`).then((r) => r.data);
}
