package com.hrms.identity.department.dto;

import java.util.List;
import java.util.UUID;

/** Nút sơ đồ phòng ban (cây). */
public record DepartmentNode(
    UUID id, String code, String name, String status, String managerName,
    List<DepartmentNode> children) {}
