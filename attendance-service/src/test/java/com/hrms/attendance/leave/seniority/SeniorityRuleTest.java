package com.hrms.attendance.leave.seniority;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hrms.attendance.leave.LeavePolicy;
import com.hrms.attendance.leave.LeaveSeniorityTier;
import com.hrms.attendance.leave.SeniorityRuleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test luật thâm niên: VN_TIERED = bậc NĐ 145/2020 tính đủ kỳ tại asOf;
 * LINEAR = logic legacy (năm dương lịch, trần maxBonus); registry fallback.
 */
class SeniorityRuleTest {

  private final VietnamTieredSeniorityRule vietnam = new VietnamTieredSeniorityRule();
  private final LinearSeniorityRule linear = new LinearSeniorityRule();
  private final SeniorityRuleRegistry registry =
      new SeniorityRuleRegistry(List.of(vietnam, linear));

  private LeavePolicy policy;
  private LocalDate asOf;

  @BeforeEach
  void setUp() {
    policy = new LeavePolicy();
    policy.setId(UUID.randomUUID());
    policy.setYear(2026);
    policy.setBaseDays(new BigDecimal("12"));
    policy.setSeniorityRule(SeniorityRuleType.VN_TIERED);
    policy.setSeniorityStepYears(1);
    policy.setSeniorityBonusDays(new BigDecimal("1"));
    policy.setSeniorityMaxBonus(new BigDecimal("5"));
    asOf = LocalDate.of(2026, 10, 22);
  }

  /** Bậc luật VN: 0/5/10/15/20 năm → +0/2/4/6/8. */
  private List<LeaveSeniorityTier> lawTiers() {
    List<LeaveSeniorityTier> tiers = new ArrayList<>();
    for (int[] t : new int[][] {{0, 0}, {5, 2}, {10, 4}, {15, 6}, {20, 8}}) {
      LeaveSeniorityTier tier = new LeaveSeniorityTier();
      tier.setPolicyId(policy.getId());
      tier.setMinYears(t[0]);
      tier.setBonusDays(new BigDecimal(t[1]));
      tiers.add(tier);
    }
    return tiers;
  }

  private SeniorityContext ctx(Instant hiredAt, List<LeaveSeniorityTier> tiers) {
    return new SeniorityContext(hiredAt, 2026, asOf, policy, tiers);
  }

  private static Instant hired(int years, int months, int days) {
    return LocalDate.of(2026, 10, 22).minusYears(years).minusMonths(months).minusDays(days)
        .atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  // ---- VN_TIERED (luật NĐ 145/2020) ----

  @Test
  void vietnam_underOneYear_returnsZero() {
    assertEquals(new BigDecimal("0.0000"),
        vietnam.bonus(ctx(hired(0, 10, 0), lawTiers())), "chưa đủ 12 tháng → 0");
  }

  @Test
  void vietnam_belowFiveYears_returnsZero() {
    assertEquals(new BigDecimal("0.0000"),
        vietnam.bonus(ctx(hired(4, 11, 0), lawTiers())), "đủ 4 năm 11 tháng → bậc 0 (+0)");
  }

  @Test
  void vietnam_exactlyFiveYears_returnsTwo() {
    assertEquals(new BigDecimal("2.0000"),
        vietnam.bonus(ctx(hired(5, 0, 0), lawTiers())),
        "đủ 5 năm → bậc +2 (tổng 14 — luật 'trên 5 năm' ±1 ngày)");
  }

  @Test
  void vietnam_elevenYears_returnsFour() {
    // NV nhà minh: hired 2015-01-05, tính 2026-10-22 → 11 năm → bậc +4 (tổng 16).
    assertEquals(new BigDecimal("4.0000"),
        vietnam.bonus(ctx(
            LocalDate.of(2015, 1, 5).atStartOfDay(ZoneOffset.UTC).toInstant(), lawTiers())));
  }

  @Test
  void vietnam_atTwentyYears_jumpsToTopTier_atNineteen_six() {
    // Áp bậc từ đúng ngày đủ N năm (luật 'trên N năm' chênh ~1 ngày — thống nhất mọi bậc).
    assertEquals(new BigDecimal("8.0000"), vietnam.bonus(ctx(hired(20, 0, 0), lawTiers())),
        "đủ 20 năm → bậc +8 (tổng 20)");
    assertEquals(new BigDecimal("6.0000"), vietnam.bonus(ctx(hired(19, 11, 0), lawTiers())),
        "chưa đủ 20 năm → bậc +6 (tổng 18)");
  }

  @Test
  void vietnam_anniversaryBasis_countsCompletedYearsNotCalendarYear() {
    // Vào làm 01/10/2021: tại 01/10/2026 = đúng 5 năm (bậc +2); tại 30/09/2026 = 4 năm (0).
    Instant hired2021 = LocalDate.of(2021, 10, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    assertEquals(new BigDecimal("2.0000"),
        vietnam.bonus(new SeniorityContext(hired2021, 2026, LocalDate.of(2026, 10, 1),
            policy, lawTiers())));
    assertEquals(new BigDecimal("0.0000"),
        vietnam.bonus(new SeniorityContext(hired2021, 2026, LocalDate.of(2026, 9, 30),
            policy, lawTiers())),
        "chưa đủ kỳ → không hưởng bậc sớm như năm dương lịch");
  }

  @Test
  void vietnam_nullHiredAt_returnsZero() {
    assertEquals(new BigDecimal("0.0000"), vietnam.bonus(ctx(null, lawTiers())));
  }

  @Test
  void vietnam_emptyTiers_selfHealsToLinear() {
    // Chưa seed bậc → tạm tính LINEAR (6 năm dương lịch × 1 × 1, trần 5).
    Instant hired2020 = LocalDate.of(2020, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    assertEquals(new BigDecimal("5.0000"), vietnam.bonus(ctx(hired2020, List.of())),
        "tiers rỗng → self-heal LINEAR, không về 0");
  }

  // ---- LINEAR (legacy) ----

  @Test
  void linear_calendarYearStepsWithCap() {
    policy.setSeniorityRule(SeniorityRuleType.LINEAR);
    policy.setSeniorityStepYears(1);
    policy.setSeniorityBonusDays(new BigDecimal("1"));
    policy.setSeniorityMaxBonus(new BigDecimal("5"));
    // hired 2020 → năm dương lịch 2026 = 6 → 6 steps × 1, trần maxBonus 5.
    Instant hired2020 = LocalDate.of(2020, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    assertEquals(new BigDecimal("5.0000"), linear.bonus(ctx(hired2020, List.of())));
  }

  @Test
  void linear_stepDisabled_returnsZero() {
    policy.setSeniorityRule(SeniorityRuleType.LINEAR);
    policy.setSeniorityStepYears(0);
    assertEquals(new BigDecimal("0.0000"),
        linear.bonus(ctx(hired(11, 0, 0), lawTiers())));
  }

  // ---- Registry ----

  @Test
  void registry_resolvesVnAndLinear() {
    assertEquals(VietnamTieredSeniorityRule.class,
        registry.resolve(policy).getClass(), "policy VN_TIERED → rule bậc luật VN");
    policy.setSeniorityRule(SeniorityRuleType.LINEAR);
    assertEquals(LinearSeniorityRule.class, registry.resolve(policy).getClass());
  }

  @Test
  void registry_unknownOrNullRule_fallsBackToLinear() {
    policy.setSeniorityRule(null);
    assertEquals(LinearSeniorityRule.class, registry.resolve(policy).getClass(),
        "rule null (data cũ) → fallback LINEAR");
  }
}
