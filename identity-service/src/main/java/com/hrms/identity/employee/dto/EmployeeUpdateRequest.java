package com.hrms.identity.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PATCH toàn phần (field null = không đổi). Không cho sửa {@code employeeCode};
 * {@code status} chỉ nhận ACTIVE/PROBATION/INACTIVE — nghỉ việc qua DELETE.
 * {@code clearDepartment}/{@code clearManager} = true để gỡ liên kết (vì null = không đổi).
 */
public record EmployeeUpdateRequest(
    @Size(max = 200) String fullName,
    @Email @Size(max = 200) String email,
    UUID departmentId,
    Boolean clearDepartment,
    @Size(max = 100) String position,
    UUID managerId,
    Boolean clearManager,
    String status,
    LocalDate birthDate,
    @Size(max = 10) String gender,
    @Size(max = 20) String phone,
    @Size(max = 20) String nationalId,
    @Size(max = 300) String address,
    @Size(max = 50) String educationLevel,
    @Size(max = 1000) String skills,
    @Size(max = 100) String emergencyContactName,
    @Size(max = 20) String emergencyContactPhone,
    @Size(max = 50) String emergencyContactRelation) {}
