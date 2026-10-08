package com.hrms.identity.orgchart;

import java.util.List;
import java.util.UUID;

/** Nút sơ đồ tổ chức — cây theo quan hệ quản lý (manager). */
public record OrgChartNode(
    UUID id,
    String code,
    String fullName,
    String position,
    String departmentName,
    String status,
    List<OrgChartNode> children) {}
