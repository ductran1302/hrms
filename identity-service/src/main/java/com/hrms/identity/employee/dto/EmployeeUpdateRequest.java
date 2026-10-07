package com.hrms.identity.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * PATCH toàn phần (field null = không đổi). Không cho sửa {@code employeeCode};
 * {@code status} chỉ nhận ACTIVE/PROBATION — nghỉ việc qua DELETE.
 */
public record EmployeeUpdateRequest(
    @Size(max = 200) String fullName,
    @Email @Size(max = 200) String email,
    @Size(max = 100) String departmentName,
    @Size(max = 100) String position,
    @Size(max = 64) String managerId,
    String status) {}
