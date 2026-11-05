package com.hrms.attendance.schedule.dto;

import com.hrms.attendance.schedule.EmployeeScheduleAssignment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Bản gán policy — latest = bản mới nhất của NV (đang áp dụng). */
public record ScheduleAssignmentResponse(
    UUID id,
    String employeeId,
    String employeeCode,
    String fullName,
    UUID policyId,
    String policyName,
    LocalDate effectiveFrom,
    String assignedBy,
    boolean latest,
    Instant createdAt) {

  public static ScheduleAssignmentResponse from(
      EmployeeScheduleAssignment assignment,
      String employeeCode,
      String fullName,
      String policyName,
      boolean latest) {
    return new ScheduleAssignmentResponse(
        assignment.getId(),
        assignment.getEmployeeId(),
        employeeCode,
        fullName,
        assignment.getPolicyId(),
        policyName,
        assignment.getEffectiveFrom(),
        assignment.getAssignedBy(),
        latest,
        assignment.getCreatedAt());
  }
}
