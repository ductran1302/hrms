package com.hrms.identity.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EmployeeCreateRequest(
    @NotBlank @Size(max = 20) String employeeCode,
    @NotBlank @Size(max = 200) String fullName,
    @Email @Size(max = 200) String email,
    @Size(max = 100) String departmentName,
    @Size(max = 100) String position,
    @Size(max = 64) String managerId,
    Instant hiredAt) {}
