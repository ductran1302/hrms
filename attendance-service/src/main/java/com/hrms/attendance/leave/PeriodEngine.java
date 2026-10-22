package com.hrms.attendance.leave;

import com.hrms.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Engine kỳ công (pure — không state). Quy tắc:
 *
 * <ul>
 *   <li>Năm TC Y: [Y-fyStartMonth-fyStartDay, ngày trước Y+1 cùng mốc].
 *   <li>Kỳ công phải liên tục, chỉ nhận 2 combo: (start=1, end≥29) → kỳ [1/Tháng,
 *       cuối tháng]; hoặc (start=end+1) → kỳ [start/M, end/M+1]. Combo hở → 422.
 *   <li>Kỳ WHOLE theo lưới (26→25 không cắt vụn): kỳ đầu của năm TC là kỳ lưới CHỨA
 *       fyStart (có thể bắt đầu trước fyStart), kỳ có end &gt; fyEnd thuộc năm TC sau
 *       → mỗi năm TC đúng 12 kỳ với lưới 26→25 (vd FY 4/2026: 26/3/2026–25/4/2026 →
 *       26/2/2027–25/3/2027; đuôi 26/3–31/3/2027 thuộc "kỳ tháng 4" của FY 2027),
 *       liên tục không hở/không chồng giữa 2 năm TC.
 *   <li>Ngày làm việc: T2–T6 luôn; CN nghỉ; T7 theo saturday_policy. Ngày lễ KHÔNG trừ.
 *   <li>ALTERNATING: đánh số T7 từ đầu năm TC, Sat #0 = đi làm, reset mỗi năm TC.
 * </ul>
 */
public final class PeriodEngine {

  /** Kỳ [start, end] inclusive. */
  public record Period(LocalDate start, LocalDate end) {}

  private PeriodEngine() {}

  /** Validate settings — kỳ phải liên tục (2 combo hợp lệ) + dải giá trị cơ bản. */
  public static void validate(LeaveSettings s) {
    if (s.getFyStartMonth() < 1 || s.getFyStartMonth() > 12) {
      throw new BusinessRuleException("fyStartMonth phải trong khoảng 1..12");
    }
    if (s.getFyStartDay() < 1 || s.getFyStartDay() > 28) {
      throw new BusinessRuleException("fyStartDay phải trong khoảng 1..28");
    }
    if (s.getPeriodStartDay() < 1 || s.getPeriodStartDay() > 28) {
      throw new BusinessRuleException("periodStartDay phải trong khoảng 1..28");
    }
    if (s.getPeriodEndDay() < 1 || s.getPeriodEndDay() > 31) {
      throw new BusinessRuleException("periodEndDay phải trong khoảng 1..31");
    }
    if (s.getSaturdayPolicy() == null) {
      throw new BusinessRuleException("saturdayPolicy không được để trống");
    }
    if (s.getMinWorkPercent() == null
        || s.getMinWorkPercent().signum() < 0
        || s.getMinWorkPercent().compareTo(new BigDecimal("100")) > 0) {
      throw new BusinessRuleException("minWorkPercent phải trong khoảng 0..100");
    }
    int start = s.getPeriodStartDay();
    int end = s.getPeriodEndDay();
    boolean monthly = start == 1 && end >= 29;          // [1/M, cuối tháng]
    boolean wrapping = start == end + 1;                 // [S/M, E/M+1]
    if (!monthly && !wrapping) {
      throw new BusinessRuleException(
          "Kỳ công phải liên tục — chỉ hợp lệ (1 → ≥29) hoặc (start = end + 1),"
              + " vd 1→31 hoặc 26→25");
    }
  }

  /**
   * Các kỳ của năm TC Y — kỳ WHOLE theo lưới, gán năm TC theo ngày end (liên tục,
   * không hở/không chồng với năm TC kề). Kỳ đầu = kỳ lưới CHỨA fyStart (có thể bắt
   * đầu trước fyStart); dừng khi end &gt; fyEnd (kỳ đó thuộc năm TC sau) → lưới
   * 26→25 luôn đúng 12 kỳ/năm TC, không tạo kỳ đuôi clip (26/M–31/M).
   */
  public static List<Period> fiscalPeriods(LeaveSettings s, int year) {
    LocalDate fyStart = LocalDate.of(year, s.getFyStartMonth(), s.getFyStartDay());
    LocalDate fyEnd = fyStart.plusYears(1).minusDays(1);
    List<Period> periods = new ArrayList<>();
    boolean wrapping = s.getPeriodStartDay() != 1;
    int startDay = s.getPeriodStartDay();

    // Kỳ đầu tiên LÀ kỳ lưới chứa fyStart (WHOLE — vd FY 1/4 + kỳ 26→25 → 26/3–25/4).
    LocalDate start =
        wrapping
            ? (fyStart.getDayOfMonth() >= startDay
                ? fyStart.withDayOfMonth(startDay)
                : fyStart.withDayOfMonth(startDay).minusMonths(1))
            : fyStart.withDayOfMonth(1);
    while (true) {
      LocalDate rawEnd = wrapping ? endOfWrap(start, s) : endOfMonth(start);
      if (rawEnd.isAfter(fyEnd)) {
        break; // end vượt fyEnd → kỳ thuộc năm TC sau (đuôi 26/M–cuối tháng không tách kỳ)
      }
      periods.add(new Period(start, rawEnd));
      start = rawEnd.plusDays(1);
    }
    return periods;
  }

  /** Tổng ngày làm việc của kỳ (T2–T6 + T7 theo policy; CN nghỉ; lễ KHÔNG trừ). */
  public static BigDecimal workingDays(Period period, LeaveSettings s, LocalDate fyStart) {
    return countWorkingDays(period.start(), period.end(), s, fyStart);
  }

  /** Ngày làm việc của kỳ trong khoảng [from, to] (đã clip by ngày vào/về). */
  public static BigDecimal workingDaysIn(
      LocalDate from, LocalDate to, LeaveSettings s, LocalDate fyStart) {
    if (from.isAfter(to)) {
      return BigDecimal.ZERO;
    }
    return countWorkingDays(from, to, s, fyStart);
  }

  // ---- internals ----

  private static LocalDate endOfWrap(LocalDate start, LeaveSettings s) {
    // Kỳ [S/M, E/(M+1)] với S = E+1 — E luôn < 28 nên luôn thuộc tháng sau hợp lệ.
    return start.plusMonths(1).withDayOfMonth(s.getPeriodEndDay());
  }

  private static LocalDate endOfMonth(LocalDate start) {
    return start.withDayOfMonth(start.lengthOfMonth());
  }

  private static BigDecimal countWorkingDays(
      LocalDate from, LocalDate to, LeaveSettings s, LocalDate fyStart) {
    BigDecimal days = BigDecimal.ZERO;
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      DayOfWeek dow = d.getDayOfWeek();
      if (dow == DayOfWeek.SUNDAY) {
        continue;
      }
      if (dow == DayOfWeek.SATURDAY) {
        days = days.add(saturdayWeight(d, s, fyStart));
      } else {
        days = days.add(BigDecimal.ONE);
      }
    }
    return days.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal saturdayWeight(LocalDate sat, LeaveSettings s, LocalDate fyStart) {
    return switch (s.getSaturdayPolicy()) {
      case OFF -> BigDecimal.ZERO;
      case HALF -> new BigDecimal("0.5");
      case ALL -> BigDecimal.ONE;
      case ALTERNATING -> saturdayIndex(sat, fyStart) % 2 == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
    };
  }

  /** Số thứ tự của T7 {@code sat} tính từ T7 đầu tiên của năm TC (Sat #0 = đi làm). */
  static int saturdayIndex(LocalDate sat, LocalDate fyStart) {
    int toFirstSat = (DayOfWeek.SATURDAY.getValue() - fyStart.getDayOfWeek().getValue() + 7) % 7;
    long offset = ChronoUnit.DAYS.between(fyStart, sat) - toFirstSat;
    return (int) (offset / 7);
  }
}
