package com.hrms.attendance.leave.dto;

import java.math.BigDecimal;
import java.util.List;

/** Tổng hợp quỹ phép 1 nhân viên trong năm. remaining = total − used (GĐ1 used = 0). */
public record LeaveQuotaResponse(
    String employeeId,
    String employeeCode,
    String fullName,
    String departmentName,
    String position,
    String status,
    BigDecimal baseDays,
    BigDecimal seniorityDays,
    BigDecimal positionDays,
    BigDecimal bonusDays,
    BigDecimal carryoverDays,
    BigDecimal adjustmentDays,
    BigDecimal totalDays,
    BigDecimal remainingDays,
    List<LeaveEntitlementResponse> entitlements) {}
