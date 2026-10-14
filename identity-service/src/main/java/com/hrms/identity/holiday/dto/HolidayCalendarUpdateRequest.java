package com.hrms.identity.holiday.dto;

import jakarta.validation.constraints.Size;

/** Code bất biến sau tạo (attendance projection join theo code). */
public record HolidayCalendarUpdateRequest(
    @Size(max = 100) String name,
    @Size(max = 300) String description,
    String status) {}
