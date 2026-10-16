package com.hrms.attendance.shift.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** Gán ca cho nhân viên từ ngày effectiveFrom (phải sau bản gán trước của NV đó). */
public record ShiftAssignmentRequest(
    @NotNull String employeeId, @NotNull UUID workShiftId, @NotNull LocalDate effectiveFrom) {}
