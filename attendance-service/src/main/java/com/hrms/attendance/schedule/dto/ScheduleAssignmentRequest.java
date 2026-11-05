package com.hrms.attendance.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** Gán policy lịch cho nhân viên từ ngày hiệu lực. */
public record ScheduleAssignmentRequest(
    @NotBlank String employeeId, @NotNull UUID policyId, @NotNull LocalDate effectiveFrom) {}
