package com.hrms.attendance.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hrms.attendance.leave.PeriodEngine.Period;
import com.hrms.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodEngineTest {

  private LeaveSettings settings(int fyMonth, int fyDay, int start, int end,
      SaturdayPolicy policy, String minPercent) {
    LeaveSettings s = new LeaveSettings();
    s.setId(java.util.UUID.randomUUID());
    s.setFyStartMonth(fyMonth);
    s.setFyStartDay(fyDay);
    s.setPeriodStartDay(start);
    s.setPeriodEndDay(end);
    s.setSaturdayPolicy(policy);
    s.setMinWorkPercent(new BigDecimal(minPercent));
    return s;
  }

  // ---- Validate combo ----

  @Test
  void validate_monthly1to31_ok() {
    PeriodEngine.validate(settings(1, 1, 1, 31, SaturdayPolicy.OFF, "50"));
  }

  @Test
  void validate_wrapping26to25_ok() {
    PeriodEngine.validate(settings(4, 1, 26, 25, SaturdayPolicy.OFF, "50"));
  }

  @Test
  void validate_holePeriod_throws() {
    // 15 → 14 liên tục (S = E+1) hợp lệ; nhưng 15 → 10 là hở
    assertThrows(BusinessRuleException.class,
        () -> PeriodEngine.validate(settings(1, 1, 15, 10, SaturdayPolicy.OFF, "50")));
  }

  @Test
  void validate_startEqualsEnd_throws() {
    // 15 → 15: hở (15/4 → 15/5 thiếu 16/4..14/5)
    assertThrows(BusinessRuleException.class,
        () -> PeriodEngine.validate(settings(1, 1, 15, 15, SaturdayPolicy.OFF, "50")));
  }

  @Test
  void validate_minPercentOutOfRange_throws() {
    assertThrows(BusinessRuleException.class,
        () -> PeriodEngine.validate(settings(1, 1, 1, 31, SaturdayPolicy.OFF, "101")));
  }

  // ---- Fiscal periods ----

  @Test
  void periods_fy14_monthly_12Periods() {
    // FY 1/4/2026 → 31/3/2027, kỳ [1/M, cuối tháng] = 12 kỳ
    List<Period> periods = PeriodEngine.fiscalPeriods(settings(4, 1, 1, 31, SaturdayPolicy.OFF, "50"), 2026);
    assertEquals(12, periods.size());
    assertEquals(LocalDate.of(2026, 4, 1), periods.get(0).start());
    assertEquals(LocalDate.of(2026, 4, 30), periods.get(0).end());
    assertEquals(LocalDate.of(2027, 3, 31), periods.get(11).end());
  }

  @Test
  void periods_fy14_wrapping26_25_12Periods() {
    // Lưới 26→25: đúng 12 kỳ/năm TC, kỳ WHOLE — kỳ đầu 26/3–25/4 (bắt đầu trước fyStart),
    // kỳ đuôi 26/3–31/3/2027 thuộc "kỳ tháng 4" của FY 2027 → KHÔNG tạo kỳ 13.
    List<Period> periods = PeriodEngine.fiscalPeriods(settings(4, 1, 26, 25, SaturdayPolicy.OFF, "50"), 2026);
    assertEquals(12, periods.size());
    assertEquals(LocalDate.of(2026, 3, 26), periods.get(0).start(), "kỳ đầu WHOLE trước fyStart");
    assertEquals(LocalDate.of(2026, 4, 25), periods.get(0).end());
    assertEquals(LocalDate.of(2026, 4, 26), periods.get(1).start());
    assertEquals(LocalDate.of(2026, 5, 25), periods.get(1).end());
    assertEquals(LocalDate.of(2027, 2, 26), periods.get(11).start());
    assertEquals(LocalDate.of(2027, 3, 25), periods.get(11).end(), "kỳ cuối = kỳ tháng 3, không clip đuôi");
  }

  @Test
  void periods_contiguous_noGapNoOverlap() {
    for (List<Period> periods : List.of(
        PeriodEngine.fiscalPeriods(settings(4, 1, 1, 31, SaturdayPolicy.OFF, "50"), 2026),
        PeriodEngine.fiscalPeriods(settings(4, 1, 26, 25, SaturdayPolicy.OFF, "50"), 2026),
        PeriodEngine.fiscalPeriods(settings(1, 1, 26, 25, SaturdayPolicy.OFF, "50"), 2026))) {
      for (int i = 1; i < periods.size(); i++) {
        LocalDate prevEnd = periods.get(i - 1).end();
        LocalDate curStart = periods.get(i).start();
        assertEquals(prevEnd.plusDays(1), curStart,
            "kỳ " + i + " phải liên tục với kỳ trước (prevEnd=" + prevEnd + ")");
        assertTrue(!periods.get(i).start().isAfter(periods.get(i).end()));
      }
    }
  }

  @Test
  void periods_fy11_wrapping26_25_12Periods() {
    // FY 1/1 + kỳ 26→25: đúng 12 kỳ — kỳ đầu WHOLE 26/12/2025–25/1/2026 (kỳ tháng 1),
    // kỳ cuối 26/11–25/12/2026; đuôi 26/12–31/12/2026 thuộc "kỳ tháng 1" của FY 2027.
    List<Period> periods = PeriodEngine.fiscalPeriods(settings(1, 1, 26, 25, SaturdayPolicy.OFF, "50"), 2026);
    assertEquals(12, periods.size());
    assertEquals(LocalDate.of(2025, 12, 26), periods.get(0).start());
    assertEquals(LocalDate.of(2026, 1, 25), periods.get(0).end());
    assertEquals(LocalDate.of(2026, 1, 26), periods.get(1).start());
    assertEquals(LocalDate.of(2026, 11, 26), periods.get(11).start());
    assertEquals(LocalDate.of(2026, 12, 25), periods.get(11).end());
  }

  @Test
  void periods_wrapping_fyBoundary_partitionNoGapNoOverlap() {
    // 2 năm TC kề nhau phải partition liên tục: FY Y kết thúc = kỳ tháng 3 (end 25/3),
    // FY Y+1 bắt đầu đúng ngày kế tiếp (26/3) — không hở, không chồng.
    LeaveSettings s = settings(4, 1, 26, 25, SaturdayPolicy.OFF, "50");
    Period prevLast = PeriodEngine.fiscalPeriods(s, 2025).get(11);
    Period curFirst = PeriodEngine.fiscalPeriods(s, 2026).get(0);
    Period curLast = PeriodEngine.fiscalPeriods(s, 2026).get(11);
    Period nextFirst = PeriodEngine.fiscalPeriods(s, 2027).get(0);
    assertEquals(LocalDate.of(2026, 3, 25), prevLast.end());
    assertEquals(prevLast.end().plusDays(1), curFirst.start(), "FY2025 → FY2026 không hở");
    assertEquals(LocalDate.of(2027, 3, 25), curLast.end());
    assertEquals(curLast.end().plusDays(1), nextFirst.start(), "FY2026 → FY2027 không hở");
    assertEquals(LocalDate.of(2027, 3, 26), nextFirst.start());
    assertEquals(LocalDate.of(2027, 4, 25), nextFirst.end(), "đuôi 26/3–31/3/2027 nằm trong kỳ tháng 4 của FY2027");
  }

  // ---- Working days ----

  @Test
  void workingDays_26sepTo25oct_off20() {
    // Q&A xác nhận: 26/9–25/10/2026 = 30 ngày → 20 T2–T6, T7 nghỉ (OFF)
    LeaveSettings s = settings(1, 1, 26, 25, SaturdayPolicy.OFF, "50");
    BigDecimal days = PeriodEngine.workingDays(
        new Period(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 25)),
        s, LocalDate.of(2026, 1, 1));
    assertEquals(new BigDecimal("20.00"), days);
  }

  @Test
  void workingDays_26sepTo25oct_half22_5() {
    // Q&A xác nhận: T7 half → 22.5 (20 + 5×0.5)
    LeaveSettings s = settings(1, 1, 26, 25, SaturdayPolicy.HALF, "50");
    BigDecimal days = PeriodEngine.workingDays(
        new Period(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 25)),
        s, LocalDate.of(2026, 1, 1));
    assertEquals(new BigDecimal("22.50"), days);
  }

  @Test
  void workingDays_all_25() {
    // OFF 20 + 5 T7×1 = 25
    LeaveSettings s = settings(1, 1, 26, 25, SaturdayPolicy.ALL, "50");
    BigDecimal days = PeriodEngine.workingDays(
        new Period(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 25)),
        s, LocalDate.of(2026, 1, 1));
    assertEquals(new BigDecimal("25.00"), days);
  }

  @Test
  void workingDays_alternating_sat0Works() {
    // FY bắt đầu 1/1/2026 (Thứ 4). T7 đầu tiên = 3/1/2026 = Sat #0 → đi làm.
    LeaveSettings s = settings(1, 1, 1, 31, SaturdayPolicy.ALTERNATING, "50");
    LocalDate fyStart = LocalDate.of(2026, 1, 1);
    assertEquals(0, PeriodEngine.saturdayIndex(LocalDate.of(2026, 1, 3), fyStart));
    BigDecimal firstWeek = PeriodEngine.workingDays(
        new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7)), s, fyStart);
    // T2–T6 (1/1 là T4) = 5 ngày + Sat #0 đi làm = 6 (1/1 T4 → 7/1 T3, có 1/3 T7)
    assertEquals(new BigDecimal("6.00"), firstWeek);
    // Sat #1 = 10/1 → nghỉ
    assertEquals(1, PeriodEngine.saturdayIndex(LocalDate.of(2026, 1, 10), fyStart));
    BigDecimal secondWeek = PeriodEngine.workingDays(
        new Period(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 14)), s, fyStart);
    assertEquals(new BigDecimal("5.00"), secondWeek);
  }

  @Test
  void workingDays_inClippedRange_hireMidPeriod() {
    // NV vào 1/10/2026 (T5) — kỳ 26/9–25/10 chỉ tính từ 1/10 (OFF → T2–T6)
    LeaveSettings s = settings(1, 1, 26, 25, SaturdayPolicy.OFF, "50");
    BigDecimal days = PeriodEngine.workingDaysIn(
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 25),
        s, LocalDate.of(2026, 1, 1));
    // 1/10 T5, 2/10 T6 → 2; 5–9/10 → 5; 12–16/10 → 5; 19–23/10 → 5; 25/10 = CN nghỉ → 17
    assertEquals(new BigDecimal("17.00"), days);
  }

  @Test
  void workingDays_sundayNeverCounted() {
    // 1/11/2026 → 7/11/2026 (T2→CN đủ tuần, 1/11 là CN)
    LeaveSettings s = settings(1, 1, 1, 31, SaturdayPolicy.ALL, "50");
    BigDecimal days = PeriodEngine.workingDays(
        new Period(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7)),
        s, LocalDate.of(2026, 1, 1));
    assertEquals(new BigDecimal("6.00"), days, "5 ngày T2–T6 + 1 T7 (ALL), CN nghỉ");
  }
}
