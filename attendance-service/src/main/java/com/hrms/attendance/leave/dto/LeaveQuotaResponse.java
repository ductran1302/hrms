package com.hrms.attendance.leave.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tổng hợp quỹ phép 1 nhân viên trong năm. remaining = total − used (GĐ1 used = 0).
 *
 * <p>{@code roleDays} = bonus cao nhất trong các role của NV (Q&A #4).
 */
public record LeaveQuotaResponse(
    String employeeId,
    String employeeCode,
    String fullName,
    String departmentName,
    String position,
    String roles,
    String status,
    BigDecimal baseDays,
    BigDecimal seniorityDays,
    BigDecimal roleDays,
    BigDecimal bonusDays,
    BigDecimal carryoverDays,
    BigDecimal adjustmentDays,
    BigDecimal totalDays,
    BigDecimal remainingDays,
    String note,
    List<LeaveEntitlementResponse> entitlements) {}
