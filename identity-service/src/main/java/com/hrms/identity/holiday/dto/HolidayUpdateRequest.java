package com.hrms.identity.holiday.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PATCH: field null = không đổi. */
public record HolidayUpdateRequest(
    @Size(max = 200) String name,
    LocalDate dateFrom,
    LocalDate dateTo,
    String type,
    Boolean halfDay,
    @Size(max = 300) String note) {}
