package com.hrms.attendance.leave.dto;

import com.hrms.attendance.leave.SeniorityRuleType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tạo phiên bản chính sách mới (DRAFT). null = mặc định (FY start; copy từ in-force nếu
 * sourcePolicyId null → giá trị mặc định).
 *
 * <p>seniorityRule null → VN_TIERED; seniorityTiers null → copy từ source / seed bậc luật VN.
 * Scalar seniority (step/bonus/max) chỉ dùng cho LINEAR.
 */
public record LeavePolicyRequest(
    int year,
    BigDecimal baseDays,
    int seniorityStepYears,
    BigDecimal seniorityBonusDays,
    BigDecimal seniorityMaxBonus,
    BigDecimal carryoverMax,
    List<RoleQuota> roleQuotas,
    LocalDate effectiveFrom,
    UUID sourcePolicyId,
    SeniorityRuleType seniorityRule,
    List<SeniorityTier> seniorityTiers) {

  public record RoleQuota(String role, BigDecimal bonusDays) {}

  /** Bậc thâm niên: áp dụng bonus khi nhân viên đã đủ minYears năm. */
  public record SeniorityTier(int minYears, BigDecimal bonusDays) {}
}
