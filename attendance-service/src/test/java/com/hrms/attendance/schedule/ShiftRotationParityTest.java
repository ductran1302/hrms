package com.hrms.attendance.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hrms.attendance.leave.LeaveSettings;
import com.hrms.attendance.leave.PeriodEngine;
import com.hrms.attendance.leave.PeriodEngine.Period;
import com.hrms.attendance.leave.SaturdayPolicy;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Parity roster vs {@link PeriodEngine} cho cả 4 mô hình Thứ 7: Off/All/Half/Alternating.
 * Alternating neo {@code rotation_anchor_date} = Thứ 7 đầu tiên của năm TC — tương đương
 * {@code saturdayIndex} (Sat #0 = đi làm) trong cùng FY và FY kế tiếp (52 tuần).
 */
class ShiftRotationParityTest {

  private static final LocalDate FY_START_2026 = LocalDate.of(2026, 4, 1);
  private static final LocalDate FY_START_2027 = LocalDate.of(2027, 4, 1);
  private static final LocalDate FY_END_2027 = LocalDate.of(2028, 3, 31);
  private static final LocalDate ANCHOR =
      DefaultPolicySeeder.firstSaturdayOnOrAfter(FY_START_2026);

  private static LeaveSettings settings(SaturdayPolicy policy) {
    LeaveSettings settings = new LeaveSettings();
    settings.setId(UUID.randomUUID());
    settings.setFyStartMonth(4);
    settings.setFyStartDay(1);
    settings.setPeriodStartDay(26);
    settings.setPeriodEndDay(25);
    settings.setSaturdayPolicy(policy);
    settings.setMinWorkPercent(new BigDecimal("50"));
    return settings;
  }

  private static WorkSchedulePolicy policy(
      SaturdayPolicy mode, UUID fullShiftId, UUID halfShiftId) {
    WorkSchedulePolicy policy = new WorkSchedulePolicy();
    policy.setId(UUID.randomUUID());
    policy.setName("Test " + mode);
    policy.setMonShiftId(fullShiftId);
    policy.setTueShiftId(fullShiftId);
    policy.setWedShiftId(fullShiftId);
    policy.setThuShiftId(fullShiftId);
    policy.setFriShiftId(fullShiftId);
    policy.setDefaultPolicy(true);
    policy.setActive(true);
    switch (mode) {
      case OFF -> policy.setSatShiftId(null);
      case ALL -> policy.setSatShiftId(fullShiftId);
      case HALF -> policy.setSatShiftId(halfShiftId);
      case ALTERNATING -> {
        policy.setSatShiftId(fullShiftId);
        policy.setSatAlternateShiftId(null);
        policy.setRotationAnchorDate(ANCHOR);
      }
    }
    return policy;
  }

  @Test
  void weekIndex_anchorAndFollowingSaturdays_correctParity() {
    assertEquals(0, ScheduleResolver.weekIndex(ANCHOR, ANCHOR));
    assertEquals(1, ScheduleResolver.weekIndex(ANCHOR, ANCHOR.plusWeeks(1)));
    assertEquals(2, ScheduleResolver.weekIndex(ANCHOR, ANCHOR.plusWeeks(2)));
    assertTrue(ScheduleResolver.isEvenWeek(ANCHOR, ANCHOR));
    assertFalse(ScheduleResolver.isEvenWeek(ANCHOR, ANCHOR.plusWeeks(1)));
  }

  @Test
  void saturdayPlan_matchesPeriodEngine_allFourModes_twoFiscalYears() {
    UUID fullShiftId = UUID.randomUUID();
    UUID halfShiftId = UUID.randomUUID();
    for (SaturdayPolicy mode : SaturdayPolicy.values()) {
      LeaveSettings settings = settings(mode);
      WorkSchedulePolicy policy = policy(mode, fullShiftId, halfShiftId);
      checkFiscalYear(settings, policy, mode, FY_START_2026, fullShiftId, halfShiftId);
      checkFiscalYear(settings, policy, mode, FY_START_2027, fullShiftId, halfShiftId);
    }
  }

  private void checkFiscalYear(
      LeaveSettings settings, WorkSchedulePolicy policy, SaturdayPolicy mode, LocalDate fyStart,
      UUID fullShiftId, UUID halfShiftId) {
    LocalDate fyEnd = fyStart.plusYears(1).minusDays(1);
    for (LocalDate date = fyStart; !date.isAfter(fyEnd); date = date.plusDays(1)) {
      if (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
        continue;
      }
      BigDecimal expected = PeriodEngine.workingDays(new Period(date, date), settings, fyStart);
      ScheduleResolver.DayPlan plan = ScheduleResolver.planFromPolicy(policy, date);
      String label = mode + " " + date;
      if (expected.signum() == 0) {
        assertTrue(plan.dayOff(), label + ": phải nghỉ");
      } else {
        assertFalse(plan.dayOff(), label + ": phải làm");
        if (expected.compareTo(new BigDecimal("0.5")) == 0) {
          assertEquals(halfShiftId, plan.shiftId(), label + ": phải dùng ca nửa ngày");
        } else {
          assertEquals(fullShiftId, plan.shiftId(), label + ": phải dùng ca đủ ngày");
        }
      }
    }
  }
}
