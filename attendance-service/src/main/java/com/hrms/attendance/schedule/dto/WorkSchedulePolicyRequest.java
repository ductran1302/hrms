package com.hrms.attendance.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record WorkSchedulePolicyRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    UUID monShiftId,
    UUID tueShiftId,
    UUID wedShiftId,
    UUID thuShiftId,
    UUID friShiftId,
    UUID satShiftId,
    UUID satAlternateShiftId,
    UUID sunShiftId,
    LocalDate rotationAnchorDate,
    Boolean defaultPolicy,
    Boolean active,
    LocalDate effectiveFrom) {}
