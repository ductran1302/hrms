package com.hrms.identity.account.dto;

import java.util.List;

/**
 * Kết quả audit role Keycloak. {@code items} chỉ chứa tài khoản không OK
 * (lệch role, cần review, không map nhân viên hoặc sửa lỗi).
 */
public record RoleAuditResponse(
    boolean applied,
    int scanned,
    int mapped,
    int ok,
    int corrected,
    int reviewRequired,
    int skippedAdmin,
    int unmapped,
    int failed,
    List<RoleAuditItem> items) {}
