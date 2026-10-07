package com.hrms.attendance.employee;

import java.time.Instant;

public record EmployeeResponse(
    String employeeId,
    String employeeCode,
    String fullName,
    String email,
    String departmentId,
    String departmentName,
    String position,
    String status,
    Instant hiredAt,
    Instant terminatedAt) {

  public static EmployeeResponse from(Employee employee) {
    return new EmployeeResponse(
        employee.getEmployeeId(),
        employee.getEmployeeCode(),
        employee.getFullName(),
        employee.getEmail(),
        employee.getDepartmentId(),
        employee.getDepartmentName(),
        employee.getPosition(),
        employee.getStatus(),
        employee.getHiredAt(),
        employee.getTerminatedAt());
  }
}
