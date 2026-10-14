package com.hrms.identity.event;

import com.hrms.identity.holiday.Holiday;
import java.time.LocalDate;

/** JSON payload outbox cho event holiday.changed — action phân loại CREATED/UPDATED/DELETED. */
public record HolidayEventPayload(
    String action,
    String holidayId,
    String calendarId,
    String calendarCode,
    String name,
    LocalDate dateFrom,
    LocalDate dateTo,
    int year,
    String holidayType,
    boolean halfDay,
    String note,
    String actorId) {

  public static HolidayEventPayload from(Holiday holiday, String action, String actorId) {
    return new HolidayEventPayload(
        action,
        holiday.getId().toString(),
        holiday.getCalendarId().toString(),
        holiday.getCalendarCode(),
        holiday.getName(),
        holiday.getDateFrom(),
        holiday.getDateTo(),
        holiday.getYear(),
        holiday.getType().name(),
        holiday.isHalfDay(),
        holiday.getNote(),
        actorId);
  }
}
