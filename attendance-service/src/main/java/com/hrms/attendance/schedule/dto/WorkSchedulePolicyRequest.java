package com.hrms.attendance.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tạo/sửa policy (PUT = thay thế toàn bộ: field null nghĩa là nghỉ/không dùng).
 * Thứ 7 luân phiên: {@code satAlternateShiftId} + {@code rotationAnchorDate} (phải là Thứ 7).
 */
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
    Boolean active) {}
