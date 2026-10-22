package com.hrms.attendance.leave.seniority;

import com.hrms.attendance.leave.LeaveSeniorityTier;
import com.hrms.attendance.leave.SeniorityRuleType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Period;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Rule luật VN (NĐ 145/2020): bậc theo <b>số năm hoàn thành</b> tại ngày tính (asOf) —
 * đếm đủ kỳ kỷ niệm, KHÔNG theo năm dương lịch (khác LINEAR → bonus đến đúng hạn, không
 * sớm tối đa 12 tháng).
 *
 * <p>Tìm bậc cao nhất có {@code serviceYears >= tier.minYears} (VD luật: 0/5/10/15/20 năm
 * → +0/2/4/6/8, tổng 12/14/16/18/20 khi base=12). Chưa đủ bậc thấp nhất → 0. Bonus bậc là
 * giá trị tuyệt đối theo luật — KHÔNG áp {@code seniorityMaxBonus} (chỉ LINEAR dùng trần).
 *
 * <p>Tiers rỗng (chưa seed / data lỗi) → self-heal tạm tính theo LINEAR để không về 0.
 */
@Component
public class VietnamTieredSeniorityRule implements SeniorityRule {

  @Override
  public SeniorityRuleType type() {
    return SeniorityRuleType.VN_TIERED;
  }

  @Override
  public BigDecimal bonus(SeniorityContext context) {
    if (context.tiers() == null || context.tiers().isEmpty()) {
      return new LinearSeniorityRule().bonus(context); // self-heal: chưa seed bậc.
    }
    if (context.hiredAt() == null) {
      return ZERO;
    }
    int serviceYears = Period.between(
        context.hiredAt().atZone(ZoneOffset.UTC).toLocalDate(), context.asOf()).getYears();
    BigDecimal best = null;
    int bestMin = Integer.MIN_VALUE;
    for (LeaveSeniorityTier tier : context.tiers()) {
      if (serviceYears >= tier.getMinYears() && tier.getMinYears() > bestMin) {
        bestMin = tier.getMinYears();
        best = tier.getBonusDays();
      }
    }
    return best == null ? ZERO : best.setScale(4, RoundingMode.HALF_UP);
  }
}
