package com.hrms.identity.employee.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH hồ sơ của chính mình — chỉ các trường cá nhân an toàn. Field công việc
 * (department/position/manager/status) hay định danh (employeeCode/email/fullName) không có
 * trong DTO nên bị Jackson bỏ qua; {@code null} = không đổi, chuỗi rỗng = xóa.
 */
public record EmployeeSelfUpdateRequest(
    @Size(max = 20) String phone,
    @Size(max = 300) String address,
    @Size(max = 50) String educationLevel,
    @Size(max = 1000) String skills,
    @Size(max = 100) String emergencyContactName,
    @Size(max = 20) String emergencyContactPhone,
    @Size(max = 50) String emergencyContactRelation) {}
