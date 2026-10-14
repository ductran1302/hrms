package com.hrms.identity.holiday.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HolidayCalendarCreateRequest(
    @NotBlank @Size(max = 30) String code,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "country phải là ISO alpha-2 (VD: VN)")
    String country,
    @Size(max = 300) String description) {}
