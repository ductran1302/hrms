package com.hrms.attendance.leave;

import com.hrms.attendance.schedule.EmployeeShiftRoster;
import com.hrms.attendance.schedule.EmployeeShiftRosterRepository;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Trọng số ngày làm việc theo {@code employee_shift_rosters} — Phase 2 nối lịch làm việc vào
 * tính quỹ phép (lịch = nguồn sự thật cho ngày công; {@link PeriodEngine} chỉ là fallback).
 *
 * <ul>
 *   <li>Ngày <b>CÓ</b> row roster: {@code dayOff} → 0; ngược lại = phút làm việc của ca /
 *       {@code hrms.leave.standard-work-minutes} (default 480), cap 1 — ca 240p = 0.5 đúng
 *       ngữ nghĩa Thứ 7 nửa ngày, ca 7h = 0.875.
 *   <li>Ngày <b>THIẾU</b> row (ngoài cửa sổ sinh roster của job, hoặc chưa generate) →
 *       không trả về trong map — người gọi dùng {@link PeriodEngine#dayWeight} (giữ nguyên
 *       kết quả của data cũ).
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RosterWorkDaySource {

  private final EmployeeShiftRosterRepository rosterRepository;
  private final WorkShiftRepository workShiftRepository;

  @Value("${hrms.leave.standard-work-minutes:480}")
  private int standardWorkMinutes;

  /**
   * Trọng số từng ngày trong {@code [from..to]} — chỉ ngày <b>có</b> row roster (dayOff → 0,
   * ngày làm → theo ca). Map rỗng = không có lịch nào trong khoảng → fallback PeriodEngine.
   */
  public Map<LocalDate, BigDecimal> weights(
      String employeeId, LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      return Map.of();
    }
    List<EmployeeShiftRoster> rows =
        rosterRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            employeeId, from, to);
    if (rows.isEmpty()) {
      return Map.of();
    }
    Map<UUID, WorkShift> shifts = loadShifts(rows);
    Map<LocalDate, BigDecimal> out = new HashMap<>(rows.size());
    for (EmployeeShiftRoster row : rows) {
      out.put(row.getWorkDate(), weightOf(row, shifts.get(row.getShiftId())));
    }
    log.debug("Roster weights NV {} [{}..{}]: {} ngày ({} row)",
        employeeId, from, to, out.size(), rows.size());
    return out;
  }

  /**
   * Σ trọng số ngày làm việc {@code [from..to]}: ngày có row → theo roster; ngày thiếu →
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

  /** Trọng số 1 row roster. */
  private BigDecimal weightOf(EmployeeShiftRoster row, WorkShift shift) {
    if (row.isDayOff()) {
      return BigDecimal.ZERO;
    }
    // Row báo đi làm nhưng ca đã bị xóa (dữ liệu lệch) → tính đủ 1 ngày, không bỏ ngày.
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

  private Map<UUID, WorkShift> loadShifts(List<EmployeeShiftRoster> rows) {
    List<UUID> shiftIds =
        rows.stream()
            .map(EmployeeShiftRoster::getShiftId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    if (shiftIds.isEmpty()) {
      return Map.of();
    }
    return workShiftRepository.findAllById(shiftIds).stream()
        .collect(Collectors.toMap(WorkShift::getId, Function.identity()));
  }
}
