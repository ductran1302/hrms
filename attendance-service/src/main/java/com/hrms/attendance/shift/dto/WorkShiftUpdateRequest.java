package com.hrms.attendance.shift.dto;

import java.time.LocalTime;

/** PATCH ca — null = giữ nguyên giá trị cũ (validate trên bản gộp trong service). */
public record WorkShiftUpdateRequest(
    String name,
    String shiftType,
    LocalTime startWorkTime,
    LocalTime endWorkTime,
    LocalTime startBreakTime,
    LocalTime endBreakTime,
    Integer workingTime,
    Integer breakingTime,
    Boolean active) {}
