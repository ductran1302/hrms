import client from './client';
import type { AttendanceEmployee } from './types';

export function listAttendanceEmployees(): Promise<AttendanceEmployee[]> {
  return client.get<AttendanceEmployee[]>('/api/v1/attendance/employees').then((r) => r.data);
}
