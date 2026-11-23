package com.hrms.identity.account.dto;

import com.hrms.identity.account.RoleAuditAction;
import java.util.List;

/** Một tài khoản có role lệch hoặc không map được hồ sơ nhân viên. */
public record RoleAuditItem(
    String username,
    String employeeCode,
    String fullName,
    List<String> currentRoles,
    String expectedRole,
    RoleAuditAction action,
    String message) {}
