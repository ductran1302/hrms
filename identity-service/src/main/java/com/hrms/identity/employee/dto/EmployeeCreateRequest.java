package com.hrms.identity.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeCreateRequest(
    /** Bỏ trống → BE sinh từ họ tên (mã NV = username đăng nhập Keycloak). */
    @Size(max = 20) String employeeCode,
    @NotBlank @Size(max = 200) String fullName,
    @Email @Size(max = 200) String email,
    UUID departmentId,
    @Size(max = 100) String position,
    UUID managerId,
    Instant hiredAt,
    LocalDate birthDate,
    @Size(max = 10) String gender,
    @Size(max = 20) String phone,
    @Size(max = 20) String nationalId,
    @Size(max = 300) String address) {}
