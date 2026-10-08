package com.hrms.identity.employee.dto;

import com.hrms.identity.employee.EmployeeStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponse(
    UUID id,
    String employeeCode,
    String fullName,
    String email,
    UUID departmentId,
    String departmentName,
    String position,
    UUID managerId,
    EmployeeStatus status,
    Instant hiredAt,
    LocalDate birthDate,
    String gender,
    String phone,
    String nationalId,
    String address,
    Instant createdAt,
    Instant updatedAt,
    Long version) {}
