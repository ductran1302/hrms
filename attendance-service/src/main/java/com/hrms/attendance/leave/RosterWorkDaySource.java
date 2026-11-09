package com.hrms.attendance.leave;

import com.hrms.attendance.schedule.EmployeeShiftRoster;
import com.hrms.attendance.schedule.EmployeeShiftRosterRepository;
import com.hrms.attendance.schedule.RosterSource;
import com.hrms.attendance.schedule.ScheduleResolver;
import com.hrms.attendance.schedule.ScheduleResolver.DayPlan;
import com.hrms.attendance.shift.WorkShift;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Trọng số ngày làm việc suy ra từ lịch policy tuần ({@link ScheduleResolver}) — nguồn sự thật cho
 * ngày công; {@link PeriodEngine} chỉ là fallback khi NV chưa có policy lịch.
 *
 * <ul>
 *   <li>Ngày <b>resolve được</b>: {@code dayOff} → 0; ngược lại = phút làm việc của ca /
 *       {@code hrms.leave.standard-work-minutes} (default 480), cap 1 — ca 240p = 0.5 đúng
 *       ngữ nghĩa Thứ 7 nửa ngày, ca 7h = 0.875.
 *   <li>Ngày <b>không resolve được</b> (NV chưa có policy/assignment) → không trả về trong map —
 *       người gọi dùng {@link PeriodEngine#dayWeight}.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RosterWorkDaySource {

  private final ScheduleResolver resolver;
  private final EmployeeShiftRosterRepository rosterRepository;

  @Value("${hrms.leave.standard-work-minutes:480}")
  private int standardWorkMinutes;

  /**
   * Trọng số từng ngày trong {@code [from..to]} — lịch policy tuần, ưu tiên dòng sửa tay
   * ({@code MANUAL_OVERRIDE}) trong range (dayOff → 0, ngày làm → theo ca). Map rỗng = NV chưa
   * có lịch → fallback PeriodEngine.
   */
  public Map<LocalDate, BigDecimal> weights(
      String employeeId, LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      return Map.of();
    }
    ScheduleResolver.Context context = resolver.loadContext(List.of(employeeId));
    Map<LocalDate, DayPlan> plans = resolver.plans(employeeId, from, to, context);
    List<EmployeeShiftRoster> overrides = rosterRepository
        .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to).stream()
        .filter(row -> row.getSource() == RosterSource.MANUAL_OVERRIDE)
        .toList();
    if (plans.isEmpty() && overrides.isEmpty()) {
      return Map.of();
    }
    Map<LocalDate, BigDecimal> out = new HashMap<>(plans.size() + overrides.size());
    for (Map.Entry<LocalDate, DayPlan> entry : plans.entrySet()) {
      DayPlan plan = entry.getValue();
      WorkShift shift = plan.shiftId() == null ? null : context.shifts().get(plan.shiftId());
      out.put(entry.getKey(), weightOf(plan.dayOff(), shift));
    }
    for (EmployeeShiftRoster override : overrides) {
      WorkShift shift = override.getShiftId() == null
          ? null : context.shifts().get(override.getShiftId());
      out.put(override.getWorkDate(), weightOf(override.isDayOff(), shift));
    }
    log.debug("Schedule weights NV {} [{}..{}]: {} ngày ({} override)",
        employeeId, from, to, out.size(), overrides.size());
    return out;
  }

  /**
   * Σ trọng số ngày làm việc {@code [from..to]}: ngày có lịch → theo policy; ngày thiếu →
   * fallback {@link PeriodEngine#dayWeight}. Kết quả scale 2 (giống {@code PeriodEngine}).
   */
  public BigDecimal sum(
      Map<LocalDate, BigDecimal> weights,
      LocalDate from,
      LocalDate to,
      LeaveSettings settings,
      LocalDate fyStart) {
    BigDecimal total = BigDecimal.ZERO;
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      BigDecimal w = weights.get(d);
      total = total.add(w != null ? w : PeriodEngine.dayWeight(d, settings, fyStart));
    }
    return total.setScale(2, RoundingMode.HALF_UP);
  }

  /** Trọng số 1 ngày lịch. */
  private BigDecimal weightOf(boolean dayOff, WorkShift shift) {
    if (dayOff) {
      return BigDecimal.ZERO;
    }
    // Ngày đi làm nhưng ca đã bị xóa (dữ liệu lệch) → tính đủ 1 ngày, không bỏ ngày.
    if (shift == null) {
      return BigDecimal.ONE;
    }
    Integer minutes = shift.getWorkingTime();
    if (minutes == null || minutes <= 0) {
      return BigDecimal.ZERO;
    }
    int standard = Math.max(1, standardWorkMinutes);
    return BigDecimal.valueOf(minutes)
        .divide(BigDecimal.valueOf(standard), 4, RoundingMode.HALF_UP)
        .min(BigDecimal.ONE);
  }
}
