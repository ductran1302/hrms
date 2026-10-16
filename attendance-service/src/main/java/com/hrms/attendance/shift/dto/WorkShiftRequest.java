package com.hrms.attendance.shift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * Tạo ca (POST — validate đầy đủ). PATCH: null = giữ nguyên giá trị cũ.
 * workingTime/breakingTime là PHÚT (480 = 8h, 420 = 7h) — không dùng giờ decimal.
 */
public record WorkShiftRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank String shiftType,
    @NotNull LocalTime startWorkTime,
    @NotNull LocalTime endWorkTime,
    LocalTime startBreakTime,
    LocalTime endBreakTime,
    @NotNull @Positive Integer workingTime,
    @NotNull @PositiveOrZero Integer breakingTime,
    Boolean active) {}
