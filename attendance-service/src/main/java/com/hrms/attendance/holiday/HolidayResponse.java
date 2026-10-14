package com.hrms.attendance.holiday;

import java.time.LocalDate;

public record HolidayResponse(
    String holidayId,
    String calendarId,
    String calendarCode,
    String name,
    LocalDate dateFrom,
    LocalDate dateTo,
    int year,
    String holidayType,
    boolean halfDay,
    String note) {

  public static HolidayResponse from(Holiday holiday) {
    return new HolidayResponse(
        holiday.getHolidayId(),
        holiday.getCalendarId(),
        holiday.getCalendarCode(),
        holiday.getName(),
        holiday.getDateFrom(),
        holiday.getDateTo(),
        holiday.getYear(),
        holiday.getHolidayType(),
        holiday.isHalfDay(),
        holiday.getNote());
  }
}
