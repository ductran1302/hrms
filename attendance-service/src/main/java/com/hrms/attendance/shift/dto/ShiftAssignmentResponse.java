package com.hrms.attendance.shift.dto;

import com.hrms.attendance.shift.ShiftAssignment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response gán ca — kèm tên NV (đọc từ projection cùng DB) và tên ca.
 * latest = bản gán mới nhất của NV này (đang áp dụng hiện tại).
 */
public record ShiftAssignmentResponse(
    UUID id,
    String employeeId,
    String employeeCode,
    String fullName,
    String departmentName,
    UUID workShiftId,
    String shiftName,
    String shiftType,
    LocalDate effectiveFrom,
    String assignedBy,
    boolean latest,
    Instant createdAt) {

  public static ShiftAssignmentResponse from(
      ShiftAssignment assignment,
      String employeeCode,
      String fullName,
      String departmentName,
      String shiftName,
      String shiftType,
      boolean latest) {
    return new ShiftAssignmentResponse(
        assignment.getId(),
        assignment.getEmployeeId(),
        employeeCode,
        fullName,
        departmentName,
        assignment.getWorkShiftId(),
        shiftName,
        shiftType,
        assignment.getEffectiveFrom(),
        assignment.getAssignedBy(),
        latest,
        assignment.getCreatedAt());
  }
}
