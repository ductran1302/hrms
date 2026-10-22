package com.hrms.attendance.leave.seniority;

import com.hrms.attendance.leave.LeavePolicy;
import com.hrms.attendance.leave.SeniorityRuleType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Rule legacy (LINEAR): {@code steps = năm_dương_lịch / stepYears}, bonus = steps ×
 * bonusDays, trần maxBonus — giữ nguyên hành vi cũ cho policy chọn LINEAR.
 */
@Component
public class LinearSeniorityRule implements SeniorityRule {

  @Override
  public SeniorityRuleType type() {
    return SeniorityRuleType.LINEAR;
  }

  @Override
  public BigDecimal bonus(SeniorityContext context) {
    LeavePolicy policy = context.policy();
    if (policy.getSeniorityStepYears() <= 0
        || policy.getSeniorityBonusDays().signum() <= 0
        || context.hiredAt() == null) {
      return ZERO;
    }
    int hiredYear = context.hiredAt().atZone(ZoneOffset.UTC).getYear();
    int years = context.year() - hiredYear;
    if (years <= 0) {
      return ZERO;
    }
    int steps = years / policy.getSeniorityStepYears();
    BigDecimal bonus = policy.getSeniorityBonusDays().multiply(BigDecimal.valueOf(steps));
    return bonus.min(policy.getSeniorityMaxBonus()).setScale(4, RoundingMode.HALF_UP);
  }
}
