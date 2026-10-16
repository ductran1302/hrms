package com.hrms.attendance.leave.dto;

import com.hrms.attendance.leave.LeavePolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LeavePolicyResponse(
    UUID id,
    int year,
    BigDecimal baseDays,
    int seniorityStepYears,
    BigDecimal seniorityBonusDays,
    BigDecimal seniorityMaxBonus,
    BigDecimal carryoverMax,
    List<PositionQuotaDto> positionQuotas) {

  public record PositionQuotaDto(String positionName, BigDecimal bonusDays) {}

  public static LeavePolicyResponse from(LeavePolicy p, List<PositionQuotaDto> quotas) {
    return new LeavePolicyResponse(
        p.getId(), p.getYear(), p.getBaseDays(), p.getSeniorityStepYears(),
        p.getSeniorityBonusDays(), p.getSeniorityMaxBonus(), p.getCarryoverMax(), quotas);
  }
}
