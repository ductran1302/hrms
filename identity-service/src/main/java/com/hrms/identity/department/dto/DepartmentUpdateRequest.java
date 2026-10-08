package com.hrms.identity.department.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** PATCH: field null = không đổi; status nhận ACTIVE/INACTIVE. */
public record DepartmentUpdateRequest(
    @Size(max = 20) String code,
    @Size(max = 100) String name,
    UUID parentId,
    UUID managerId,
    String status) {}
