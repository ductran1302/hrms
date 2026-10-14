package com.hrms.identity.holiday.dto;

import java.util.UUID;

public record HolidayCalendarResponse(
    UUID id,
    String code,
    String name,
    String country,
    String description,
    String status) {}
