package com.hrms.attendance.leave.dto;

import java.math.BigDecimal;
import java.util.List;

public record LeavePolicyRequest(
    int year,
    BigDecimal baseDays,
    int seniorityStepYears,
    BigDecimal seniorityBonusDays,
    BigDecimal seniorityMaxBonus,
    BigDecimal carryoverMax,
    List<PositionQuota> positionQuotas) {

  public record PositionQuota(String positionName, BigDecimal bonusDays) {}
}
