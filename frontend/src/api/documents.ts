import client from './client';
import type { EmployeeDocument } from './types';

export function listDocuments(employeeId: string): Promise<EmployeeDocument[]> {
  return client
    .get<EmployeeDocument[]>(`/api/v1/employees/${employeeId}/documents`)
    .then((r) => r.data);
}

export function uploadDocument(
  employeeId: string,
  file: File,
  docType?: string,
  note?: string,
): Promise<EmployeeDocument> {
  const form = new FormData();
  form.append('file', file);
  if (docType) form.append('docType', docType);
  if (note) form.append('note', note);
  return client
    .post<EmployeeDocument>(`/api/v1/employees/${employeeId}/documents`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((r) => r.data);
}

export async function downloadDocument(
  employeeId: string,
  documentId: string,
  fileName: string,
): Promise<void> {
  const res = await client.get<Blob>(
    `/api/v1/employees/${employeeId}/documents/${documentId}`,
    { responseType: 'blob' },
  );
  const url = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export function deleteDocument(employeeId: string, documentId: string): Promise<void> {
  return client
    .delete(`/api/v1/employees/${employeeId}/documents/${documentId}`)
    .then(() => undefined);
}
