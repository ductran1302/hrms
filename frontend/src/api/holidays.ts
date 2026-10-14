import client from './client';
import type {
  Holiday,
  HolidayCalendar,
  HolidayCalendarCreate,
  HolidayCalendarUpdate,
  HolidayCreate,
  HolidayUpdate,
} from './types';

export function listCalendars(): Promise<HolidayCalendar[]> {
  return client.get<HolidayCalendar[]>('/api/v1/holiday-calendars').then((r) => r.data);
}

export function createCalendar(data: HolidayCalendarCreate): Promise<HolidayCalendar> {
  return client.post<HolidayCalendar>('/api/v1/holiday-calendars', data).then((r) => r.data);
}

export function updateCalendar(id: string, data: HolidayCalendarUpdate): Promise<HolidayCalendar> {
  return client.patch<HolidayCalendar>(`/api/v1/holiday-calendars/${id}`, data).then((r) => r.data);
}

export function deleteCalendar(id: string): Promise<void> {
  return client.delete(`/api/v1/holiday-calendars/${id}`).then((r) => r.data);
}

export function listHolidays(params?: {
  calendarCode?: string;
  year?: number;
  q?: string;
}): Promise<Holiday[]> {
  return client.get<Holiday[]>('/api/v1/holidays', { params }).then((r) => r.data);
}

export function createHoliday(calendarId: string, data: HolidayCreate): Promise<Holiday> {
  return client
    .post<Holiday>(`/api/v1/holiday-calendars/${calendarId}/holidays`, data)
    .then((r) => r.data);
}

export function updateHoliday(id: string, data: HolidayUpdate): Promise<Holiday> {
  return client.patch<Holiday>(`/api/v1/holidays/${id}`, data).then((r) => r.data);
}

export function deleteHoliday(id: string): Promise<void> {
  return client.delete(`/api/v1/holidays/${id}`).then((r) => r.data);
}

export function myHolidays(year?: number): Promise<Holiday[]> {
  return client
    .get<Holiday[]>('/api/v1/holidays/my', { params: year ? { year } : undefined })
    .then((r) => r.data);
}

export function todayHolidays(): Promise<Holiday[]> {
  return client.get<Holiday[]>('/api/v1/holidays/today').then((r) => r.data);
}

export function assignEmployeeCalendar(employeeId: string, calendarId: string | null): Promise<unknown> {
  return client.patch(`/api/v1/employees/${employeeId}/calendar`, { calendarId }).then((r) => r.data);
}
