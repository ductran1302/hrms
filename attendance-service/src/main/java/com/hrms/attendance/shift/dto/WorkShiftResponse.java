package com.hrms.attendance.shift.dto;

import com.hrms.attendance.shift.WorkShift;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** Response ca làm việc — workingTime/breakingTime đơn vị PHÚT. */
public record WorkShiftResponse(
    UUID id,
    String name,
    String shiftType,
    LocalTime startWorkTime,
    LocalTime endWorkTime,
    LocalTime startBreakTime,
    LocalTime endBreakTime,
    Integer workingTime,
    Integer breakingTime,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static WorkShiftResponse from(WorkShift shift) {
    return new WorkShiftResponse(
        shift.getId(),
        shift.getName(),
        shift.getShiftType().name(),
        shift.getStartWorkTime(),
        shift.getEndWorkTime(),
        shift.getStartBreakTime(),
        shift.getEndBreakTime(),
        shift.getWorkingTime(),
        shift.getBreakingTime(),
        shift.isActive(),
        shift.getCreatedAt(),
        shift.getUpdatedAt());
  }
}
