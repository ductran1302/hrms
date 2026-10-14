package com.hrms.identity.holiday.dto;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(
    UUID id,
    UUID calendarId,
    String calendarCode,
    String name,
    LocalDate dateFrom,
    LocalDate dateTo,
    int year,
    String type,
    boolean halfDay,
    String note) {}
