package com.hrms.attendance.leave;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeAccessResolver;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.leave.PeriodEngine.Period;
import com.hrms.attendance.leave.dto.LeaveNoteRequest;
import com.hrms.attendance.leave.dto.LeaveSettingsRequest;
import com.hrms.attendance.leave.dto.LeaveSettingsResponse;
import com.hrms.attendance.leave.dto.LeaveWorkDayResponse;
import com.hrms.attendance.leave.dto.LeaveWorkDayUpsertRequest;
import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.EmployeeScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tham số global + ghi chú NV/năm + ngày công theo kỳ (nguồn điều kiện x%).
 *
 * <p>Nhận xét: ngày lễ KHÔNG trừ khỏi ngày công; ngày công chỉ tính T2–T6 + T7 theo
 * saturday_policy (CN luôn nghỉ). Ngày công DEFAULT auto-fill từ ngày vào/về khi Tính lại;
 * HR nhập tay → MANUAL, recalc không đè.
 *
 * <p><b>Phase 2 (2026-11-05):</b> ngày công (cả mẫu số "ngày công của kỳ" và tử số thực tế)
 * lấy theo {@code employee_shift_rosters} khi ngày có row (lịch làm việc = nguồn sự thật),
 * ngày thiếu row fallback {@link PeriodEngine} — xem {@link RosterWorkDaySource}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveSettingsService {

  private final LeaveSettingsRepository settingsRepository;
  private final LeaveEmployeeNoteRepository noteRepository;
  private final LeaveWorkDayRepository workDayRepository;
  private final EmployeeRepository employeeRepository;
  private final EmployeeAccessResolver accessResolver;
  private final AuditService auditService;
  private final RosterWorkDaySource rosterWorkDaySource;

  // ---- Settings ----

  /** Đọc settings — tự tạo row mặc định nếu thiếu (self-healing). */
  @Transactional
  public LeaveSettings getOrCreateSettings() {
    return settingsRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
      LeaveSettings settings = new LeaveSettings();
      settings.setId(UUID.randomUUID());
      settings.setFyStartMonth(1);
      settings.setFyStartDay(1);
      settings.setPeriodStartDay(1);
      settings.setPeriodEndDay(31);
      settings.setSaturdayPolicy(SaturdayPolicy.OFF);
      settings.setMinWorkPercent(new BigDecimal("50"));
      log.info("Tạo leave settings mặc định");
      return settingsRepository.save(settings);
    });
  }

  /**
   * Đọc settings — tự tạo row mặc định nếu thiếu (self-healing).
   *
   * <p>Phải là write tx: self-invocation getOrCreateSettings không qua proxy, nếu caller
   * readOnly thì save KHÔNG commit (self-heal biến mất sau response).
   */
  @Transactional
  public LeaveSettingsResponse getSettings() {
    return LeaveSettingsResponse.from(getOrCreateSettings());
  }

  @Transactional
  public LeaveSettingsResponse updateSettings(LeaveSettingsRequest request) {
    LeaveSettings settings = getOrCreateSettings();
    settings.setFyStartMonth(request.fyStartMonth());
    settings.setFyStartDay(request.fyStartDay());
    settings.setPeriodStartDay(request.periodStartDay());
    settings.setPeriodEndDay(request.periodEndDay());
    settings.setSaturdayPolicy(request.saturdayPolicy());
    settings.setMinWorkPercent(request.minWorkPercent().setScale(2, RoundingMode.HALF_UP));
    if (request.requirePolicyApproval() != null) {
      settings.setRequirePolicyApproval(request.requirePolicyApproval());
    }
    PeriodEngine.validate(settings);
    settingsRepository.save(settings);
    log.info(
        "Cập nhật leave settings: FY {}/{} kỳ {→} {}, T7={}, ngưỡng {}%",
        settings.getFyStartMonth(), settings.getFyStartDay(),
        settings.getPeriodStartDay(), settings.getPeriodEndDay(),
        settings.getSaturdayPolicy(), settings.getMinWorkPercent());
    auditService.success("LEAVE_SETTINGS_UPDATE", "LeaveSettings", settings.getId(),
        "fy=" + settings.getFyStartDay() + "/" + settings.getFyStartMonth()
            + " saturday=" + settings.getSaturdayPolicy()
            + " minWorkPercent=" + settings.getMinWorkPercent());
    return LeaveSettingsResponse.from(settings);
  }

  // ---- Ghi chú NV/năm ----

  @Transactional(readOnly = true)
  public String getNote(String employeeId, int year, String username, EmployeeScope scope) {
    if (scope != EmployeeScope.ALL) {
      accessResolver.resolve(username, scope).assertCanAccess(requireEmployee(employeeId));
    }
    return noteRepository.findByEmployeeIdAndYear(employeeId, year)
        .map(LeaveEmployeeNote::getNote)
        .orElse(null);
  }

  @Transactional
  public void saveNote(String employeeId, int year, LeaveNoteRequest request, String actor) {
    requireEmployee(employeeId);
    LeaveEmployeeNote note = noteRepository.findByEmployeeIdAndYear(employeeId, year)
        .orElseGet(() -> {
          LeaveEmployeeNote created = new LeaveEmployeeNote();
          created.setId(UUID.randomUUID());
          created.setEmployeeId(employeeId);
          created.setYear(year);
          return created;
        });
    String text = request.note() == null ? null : request.note().trim();
    if (text != null && text.isEmpty()) {
      text = null;
    }
    if (text != null && text.length() > 500) {
      throw new BusinessRuleException("Ghi chú tối đa 500 ký tự");
    }
    note.setNote(text);
    note.setUpdatedBy(actor);
    noteRepository.save(note);
    auditService.success("LEAVE_NOTE_UPDATE", "LeaveEmployeeNote", note.getId(),
        "employeeId=" + employeeId + " year=" + year);
  }

  // ---- Ngày công theo kỳ ----

  /**
   * Danh sách ngày công các kỳ trong năm TC — merge period engine với row đã lưu.
   *
   * <p>Write tx (không phải readOnly): self-invocation getOrCreateSettings cần commit self-heal.
   */
  @Transactional
  public List<LeaveWorkDayResponse> listWorkDays(
      String employeeId, int year, String username, EmployeeScope scope) {
    Employee employee = requireEmployee(employeeId);
    if (scope != EmployeeScope.ALL) {
      accessResolver.resolve(username, scope).assertCanAccess(employee);
    }
    LeaveSettings settings = getOrCreateSettings();
    List<Period> periods = PeriodEngine.fiscalPeriods(settings, year);
    if (periods.isEmpty()) {
      throw new BusinessRuleException("Không sinh được kỳ công cho năm " + year);
    }
    LocalDate fyStart =
        LocalDate.of(year, settings.getFyStartMonth(), settings.getFyStartDay());
    Map<LocalDate, LeaveWorkDay> stored =
        workDayRepository
            .findByEmployeeIdAndPeriodStartGreaterThanEqualAndPeriodStartLessThanEqualOrderByPeriodStartAsc(
                employeeId, periods.get(0).start(), periods.get(periods.size() - 1).end())
            .stream()
            .collect(Collectors.toMap(LeaveWorkDay::getPeriodStart, Function.identity()));
    Map<LocalDate, BigDecimal> fulls = fullDaysByPeriod(employeeId, periods, settings, fyStart);
    List<LeaveWorkDayResponse> result = new ArrayList<>();
    for (Period period : periods) {
      BigDecimal full = fulls.get(period.start());
      LeaveWorkDay row = stored.get(period.start());
      if (row != null) {
        result.add(LeaveWorkDayResponse.from(row, full));
      } else {
        result.add(new LeaveWorkDayResponse(
            null, period.start(), period.end(), full, null, null));
      }
    }
    return result;
  }

  /** HR nhập tay ngày công 1 kỳ → upsert MANUAL (recalc không đè). */
  @Transactional
  public LeaveWorkDayResponse upsertWorkDay(
      String employeeId, LeaveWorkDayUpsertRequest request, String actor) {
    requireEmployee(employeeId);
    if (request.workDays() == null || request.workDays().signum() < 0) {
      throw new BusinessRuleException("workDays phải ≥ 0");
    }
    if (request.workDays().scale() > 2) {
      throw new BusinessRuleException("workDays tối đa 2 chữ số thập phân");
    }
    // Kỳ WHOLE có mốc bắt đầu lệch năm DL so với năm TC (kỳ đầu 26/12/2025 ∈ FY 2026,
    // kỳ cuối 1/3/2027 ∈ FY 4/2026) → thử 3 ứng viên quanh year của periodStart.
    Period period = null;
    LeaveSettings settings = getOrCreateSettings();
    int startYear = request.periodStart().getYear();
    for (int candidateYear : new int[] {startYear, startYear - 1, startYear + 1}) {
      for (Period p : PeriodEngine.fiscalPeriods(settings, candidateYear)) {
        if (p.start().equals(request.periodStart())) {
          period = p;
          break;
        }
      }
      if (period != null) {
        break;
      }
    }
    if (period == null) {
      throw new ResourceNotFoundException("Kỳ công", request.periodStart().toString());
    }
    final Period matched = period;
    LocalDate rawFyStart = findRawFyStart(settings, matched);
    BigDecimal full = periodFullDays(employeeId, matched, settings, rawFyStart);
    if (request.workDays().compareTo(full) > 0) {
      throw new BusinessRuleException(
          "workDays (" + request.workDays() + ") vượt ngày làm việc của kỳ (" + full + ")");
    }
    LeaveWorkDay row = workDayRepository
        .findByEmployeeIdAndPeriodStart(employeeId, request.periodStart())
        .orElseGet(() -> {
          LeaveWorkDay created = new LeaveWorkDay();
          created.setId(UUID.randomUUID());
          created.setEmployeeId(employeeId);
          created.setPeriodStart(matched.start());
          created.setPeriodEnd(matched.end());
          return created;
        });
    row.setWorkDays(request.workDays().setScale(2, RoundingMode.HALF_UP));
    row.setSource(WorkDaySource.MANUAL);
    row.setUpdatedBy(actor);
    workDayRepository.save(row);
    auditService.success("LEAVE_WORK_DAY_UPDATE", "LeaveWorkDay", row.getId(),
        "employeeId=" + employeeId + " period=" + period.start() + " days=" + row.getWorkDays());
    log.info("Nhập tay ngày công NV {} kỳ {} = {}", employeeId, period.start(), row.getWorkDays());
    return LeaveWorkDayResponse.from(row, full);
  }

  /** Năm TC mà kỳ thuộc về — gán theo ngày CUỐI kỳ (kỳ WHOLE có thể bắt đầu trước fyStart). */
  private static LocalDate findRawFyStart(LeaveSettings settings, Period period) {
    LocalDate end = period.end();
    LocalDate fyStartEndYear =
        LocalDate.of(end.getYear(), settings.getFyStartMonth(), settings.getFyStartDay());
    return end.isBefore(fyStartEndYear) ? fyStartEndYear.minusYears(1) : fyStartEndYear;
  }

  /**
   * Ngày công chuẩn (mẫu số) của 1 kỳ cho 1 NV — theo roster nếu ngày có row, ngược lại
   * PeriodEngine. Load roster 1 lần cho khoảng kỳ.
   */
  @Transactional
  public BigDecimal periodFullDays(
      String employeeId, Period period, LeaveSettings settings, LocalDate fyStart) {
    Map<LocalDate, BigDecimal> weights =
        rosterWorkDaySource.weights(employeeId, period.start(), period.end());
    return rosterWorkDaySource.sum(weights, period.start(), period.end(), settings, fyStart);
  }

  /**
   * Ngày công chuẩn (mẫu số) từng kỳ của năm TC cho 1 NV — load roster 1 lần cho toàn bộ
   * khoảng các kỳ rồi cộng từng kỳ. Map key = {@code period.start()}.
   */
  @Transactional
  public Map<LocalDate, BigDecimal> fullDaysByPeriod(
      String employeeId, List<Period> periods, LeaveSettings settings, LocalDate fyStart) {
    if (periods.isEmpty()) {
      return Map.of();
    }
    Map<LocalDate, BigDecimal> weights =
        rosterWorkDaySource.weights(
            employeeId, periods.get(0).start(), periods.get(periods.size() - 1).end());
    Map<LocalDate, BigDecimal> result = new java.util.HashMap<>();
    for (Period period : periods) {
      result.put(
          period.start(),
          rosterWorkDaySource.sum(weights, period.start(), period.end(), settings, fyStart));
    }
    return result;
  }

  /**
   * Tính lại DEFAULT work-days cho NV (idempotent): row thiếu → tạo DEFAULT; DEFAULT cũ →
   * cập nhật theo ngày vào/về hiện tại; MANUAL giữ nguyên. Trả về map periodStart → workDays
   * (DEFAULT hoặc MANUAL) để recalculate dùng cho điều kiện x%.
   *
   * <p>Số ngày lấy theo roster (ngày có row) — ngày ngoài cửa sổ roster fallback PeriodEngine.
   */
  @Transactional
  public Map<LocalDate, BigDecimal> ensureDefaultWorkDays(
      Employee employee, int year, List<Period> periods, LeaveSettings settings, LocalDate fyStart) {
    LocalDate hire = employee.getHiredAt() == null ? null : toUtcDate(employee.getHiredAt());
    LocalDate term = employee.getTerminatedAt() == null ? null : toUtcDate(employee.getTerminatedAt());
    Map<LocalDate, BigDecimal> result = new java.util.HashMap<>();
    Map<LocalDate, BigDecimal> weights =
        periods.isEmpty()
            ? Map.of()
            : rosterWorkDaySource.weights(
                employee.getEmployeeId(), periods.get(0).start(), periods.get(periods.size() - 1).end());
    for (Period period : periods) {
      LocalDate from = hire != null && hire.isAfter(period.start()) ? hire : period.start();
      LocalDate to = term != null && term.isBefore(period.end()) ? term : period.end();
      BigDecimal defaultDays = rosterWorkDaySource.sum(weights, from, to, settings, fyStart);
      LeaveWorkDay row = workDayRepository
          .findByEmployeeIdAndPeriodStart(employee.getEmployeeId(), period.start())
          .orElseGet(() -> {
            LeaveWorkDay created = new LeaveWorkDay();
            created.setId(UUID.randomUUID());
            created.setEmployeeId(employee.getEmployeeId());
            created.setPeriodStart(period.start());
            created.setPeriodEnd(period.end());
            return created;
          });
      if (row.getSource() != WorkDaySource.MANUAL) {
        row.setWorkDays(defaultDays);
        row.setSource(WorkDaySource.DEFAULT);
        row.setPeriodEnd(period.end()); // đồng bộ end khi lưới kỳ đổi (grid cũ lưu end khác)
        workDayRepository.save(row);
      }
      result.put(period.start(), row.getWorkDays());
    }
    return result;
  }

  static LocalDate toUtcDate(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).toLocalDate();
  }

  private Employee requireEmployee(String employeeId) {
    return employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
  }
}
