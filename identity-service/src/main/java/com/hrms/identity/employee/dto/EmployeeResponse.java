package com.hrms.identity.employee.dto;

import com.hrms.identity.employee.EmployeeStatus;
import java.time.Instant;
import java.util.UUID;

public record EmployeeResponse(
    UUID id,
    String employeeCode,
    String fullName,
    String email,
    String departmentId,
    String departmentName,
    String position,
    String managerId,
    EmployeeStatus status,
    Instant hiredAt,
    Instant createdAt,
    Instant updatedAt,
    Long version) {}
