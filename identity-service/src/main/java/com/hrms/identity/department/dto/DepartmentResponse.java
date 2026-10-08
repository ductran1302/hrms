package com.hrms.identity.department.dto;

import java.util.UUID;

public record DepartmentResponse(
    UUID id,
    String code,
    String name,
    UUID parentId,
    UUID managerId,
    String managerName,
    String status,
    long employeeCount) {}
