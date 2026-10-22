package com.hrms.attendance.leave.dto;

import com.hrms.attendance.leave.LeavePolicy;
import com.hrms.attendance.leave.PolicyStatus;
import com.hrms.attendance.leave.SeniorityRuleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Một phiên bản chính sách quỹ phép (effective-dated, 6 states). */
public record LeavePolicyResponse(
    UUID id,
    int year,
    int versionNo,
    PolicyStatus status,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    BigDecimal baseDays,
    int seniorityStepYears,
    BigDecimal seniorityBonusDays,
    BigDecimal seniorityMaxBonus,
    BigDecimal carryoverMax,
    String createdBy,
    String approvedBy,
    Instant approvedAt,
    String activatedBy,
    Instant activatedAt,
    List<RoleQuotaDto> roleQuotas,
    SeniorityRuleType seniorityRule,
    List<SeniorityTierDto> seniorityTiers) {

  public record RoleQuotaDto(String role, BigDecimal bonusDays) {}

  /** Bậc thâm niên (VN_TIERED): áp dụng khi đủ minYears năm → +bonusDays. */
  public record SeniorityTierDto(int minYears, BigDecimal bonusDays) {}

  public static LeavePolicyResponse from(
      LeavePolicy p, List<RoleQuotaDto> quotas, List<SeniorityTierDto> tiers) {
    return new LeavePolicyResponse(
        p.getId(), p.getYear(), p.getVersionNo(), p.getStatus(),
        p.getEffectiveFrom(), p.getEffectiveTo(),
        p.getBaseDays(), p.getSeniorityStepYears(), p.getSeniorityBonusDays(),
        p.getSeniorityMaxBonus(), p.getCarryoverMax(),
        p.getCreatedBy(), p.getApprovedBy(), p.getApprovedAt(),
        p.getActivatedBy(), p.getActivatedAt(), quotas,
        p.getSeniorityRule(), tiers);
  }
}
