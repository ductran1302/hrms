package com.hrms.identity.holiday.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record HolidayCreateRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull LocalDate dateFrom,
    @NotNull LocalDate dateTo,
    @NotBlank String type,
    Boolean halfDay,
    @Size(max = 300) String note) {}
