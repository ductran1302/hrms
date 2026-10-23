package com.hrms.attendance.leave;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeAccess;
import com.hrms.attendance.employee.EmployeeAccessResolver;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.leave.PeriodEngine.Period;
import com.hrms.attendance.leave.dto.LeaveEntitlementResponse;
import com.hrms.attendance.leave.dto.LeaveGrantRequest;
import com.hrms.attendance.leave.dto.LeaveQuotaResponse;
import com.hrms.attendance.leave.seniority.SeniorityContext;
import com.hrms.attendance.leave.seniority.SeniorityRuleRegistry;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.EmployeeScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quỹ phép — chính sách theo năm (effective-dated versioning) + ledger entitlement + tính tự
 * động theo kỳ công.
 *
 * <p>Nguyên tắc: ledger {@code leave_entitlements} — mỗi nguồn 1 dòng (audit rõ). Tính tự động
 * BASE/SENIORITY/POSITION {@code upsert at-place} khi ensure/recalculate; HR cấp tay
 * BONUS/CARRYOVER/ADJUSTMENT. Số ngày là {@link BigDecimal} (numeric(8,4)).
 *
 * <p><b>Versioning (Phase 2):</b> chính sách đọc theo version in-force ({@link
 * LeavePolicyService#getInForce}); BASE tính <b>per-period</b> — kỳ p dùng version hiệu lực tại
 * {@code p.start} (mid-year change không tính lại các kỳ trước); SENIORITY/role/carryover theo
 * in-force tại ngày tính. Mỗi dòng auto snapshot {@code policy_id + calculated_at} để truy vết.
 * Recalculate là hành động explicit của ADMIN — đổi chính sách KHÔNG tự tính lại (expert #5).
 *
 * <p>BASE: {@code Σ baseDays(version của kỳ đạt) / tổng_số_kỳ_trong_năm_TC} (kỳ đầu/cuối bị clip
 * vẫn tính 1 kỳ — Q&amp;A #2/#7). Role quota: lấy bonus CAO NHẤT trong các role của NV (Q&amp;A
 * #4). Carryover auto: {@code min(còn_lại_năm trước, carryoverMax)} — dòng tay
 * ({@code is_auto=false}) recalc không đè (Q&amp;A #5).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveQuotaService {

  static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
  private static final Set<LeaveSource> AUTO_SOURCES =
      EnumSet.of(LeaveSource.BASE, LeaveSource.SENIORITY, LeaveSource.POSITION);
  private static final Set<LeaveSource> MANUAL_SOURCES =
      EnumSet.of(LeaveSource.BONUS, LeaveSource.CARRYOVER, LeaveSource.ADJUSTMENT);

  private final LeavePolicyService policyService;
  private final LeaveRoleQuotaRepository roleQuotaRepository;
  private final LeaveSeniorityTierRepository tierRepository;
  private final LeaveEntitlementRepository entitlementRepository;
  private final EmployeeRepository employeeRepository;
  private final EmployeeAccessResolver accessResolver;
  private final LeaveSettingsService settingsService;
  private final LeaveEmployeeNoteRepository noteRepository;
  private final SeniorityRuleRegistry seniorityRuleRegistry;

  // ---- Tính tự động + ledger ----

  /**
   * Tính lại quota tự động cho toàn bộ NV ACTIVE trong năm:
   *
   * <ul>
   *   <li>Prefill work-days DEFAULT từ ngày vào/về (MANUAL giữ nguyên).
   *   <li>BASE = Σ baseDays(version hiệu lực tại kỳ) / tổng_số_kỳ — chỉ tính các kỳ đạt điều
   *       kiện (mid-year change áp dụng từ version mới, các kỳ trước giữ nguyên).
   *   <li>SENIORITY / role bonus / carryover: theo version in-force tại ngày tính.
   *   <li>Dòng auto snapshot policy_id + calculated_at.
   * </ul>
   */
  @Transactional
  public int recalculate(int year) {
    LeavePolicy inForce = policyService.getInForce(year);
    LeaveSettings settings = settingsService.getOrCreateSettings();
    List<Period> periods = PeriodEngine.fiscalPeriods(settings, year);
    if (periods.isEmpty()) {
      throw new BusinessRuleException("Không sinh được kỳ công cho năm " + year);
    }
    List<LeavePolicy> history = policyService.versionHistory(year);
    if (history.isEmpty()) {
      history = List.of(inForce);
    }
    Map<String, BigDecimal> prevRemaining = remainingByEmployee(year - 1);

    List<Employee> employees = employeeRepository.findByStatusOrderByFullNameAsc("ACTIVE");
    for (Employee employee : employees) {
      BigDecimal base = computeBase(employee, year, settings, periods, history, inForce);
      upsertAuto(employee, year, LeaveSource.BASE, base, inForce);
      upsertAuto(employee, year, LeaveSource.SENIORITY,
          seniorityBonus(employee, year, inForce), inForce);
      BigDecimal roleBonus = roleBonus(employee, inForce);
      if (roleBonus.signum() > 0) {
        upsertAuto(employee, year, LeaveSource.POSITION, roleBonus, inForce);
      } else {
        entitlementRepository
            .findByEmployeeIdAndYearAndSource(employee.getEmployeeId(), year, LeaveSource.POSITION)
            .ifPresent(entitlementRepository::delete);
      }
      carryoverAuto(employee, year, inForce, prevRemaining.getOrDefault(
          employee.getEmployeeId(), ZERO));
    }
    log.info(
        "Tính lại quỹ phép năm {}: {} nhân viên, {} kỳ/năm, policy v{} ({}) — FY start {}/{}",
        year, employees.size(), periods.size(), inForce.getVersionNo(), inForce.getStatus(),
        settings.getFyStartMonth(), settings.getFyStartDay());
    return employees.size();
  }

  /** Đảm bảo dòng auto BASE/SENIORITY/POSITION (không đụng carryover) — lazy self-heal. */
  @Transactional
  public void ensureAuto(Employee employee, int year) {
    LeavePolicy inForce = policyService.getInForce(year);
    List<LeavePolicy> history = policyService.versionHistory(year);
    if (history.isEmpty()) {
      history = List.of(inForce);
    }
    ensureAuto(employee, year, inForce, history);
  }

  private void ensureAuto(
      Employee employee, int year, LeavePolicy inForce, List<LeavePolicy> history) {
    LeaveSettings settings = settingsService.getOrCreateSettings();
    List<Period> periods = PeriodEngine.fiscalPeriods(settings, year);
    if (periods.isEmpty()) {
      throw new BusinessRuleException("Không sinh được kỳ công cho năm " + year);
    }
    // BASE phải tính theo số kỳ đạt — KHÔNG dùng policy.baseDays thô (sẽ ghi đè BASE recalc).
    upsertAuto(employee, year, LeaveSource.BASE,
        computeBase(employee, year, settings, periods, history, inForce), inForce);
    upsertAuto(employee, year, LeaveSource.SENIORITY,
        seniorityBonus(employee, year, inForce), inForce);
    BigDecimal roleBonus = roleBonus(employee, inForce);
    if (roleBonus.signum() > 0) {
      upsertAuto(employee, year, LeaveSource.POSITION, roleBonus, inForce);
    } else {
      entitlementRepository
          .findByEmployeeIdAndYearAndSource(employee.getEmployeeId(), year, LeaveSource.POSITION)
          .ifPresent(entitlementRepository::delete);
    }
  }

  /**
   * BASE = Σ baseDays(version hiệu lực tại từng kỳ đạt điều kiện) / tổng số kỳ
   * (scale 4, HALF_UP).
   *
   * <p>Điều kiện 1 kỳ: ngày công thực tế × 100 ≥ ngày công trong kỳ × minWorkPercent;
   * pre-fill DEFAULT từ ngày vào/về (MANUAL giữ nguyên). Chia một lần cuối — không round giữa
   * kỳ → kết quả 1 version trùng khớp với công thức cũ {@code baseDays × kỳ_đạt / tổng_kỳ}.
   */
  private BigDecimal computeBase(
      Employee employee, int year, LeaveSettings settings, List<Period> periods,
      List<LeavePolicy> history, LeavePolicy fallback) {
    LocalDate fyStart =
        LocalDate.of(year, settings.getFyStartMonth(), settings.getFyStartDay());
    Map<LocalDate, BigDecimal> workDays = settingsService.ensureDefaultWorkDays(
        employee, year, periods, settings, fyStart);
    BigDecimal sum = ZERO;
    for (Period period : periods) {
      if (isEligible(period, workDays, settings, fyStart)) {
        sum = sum.add(resolveAt(history, period.start(), fallback).getBaseDays());
      }
    }
    return sum.divide(BigDecimal.valueOf(periods.size()), 4, RoundingMode.HALF_UP);
  }

  /** Version hiệu lực tại ngày trong năm từ lịch sử (ACTIVE|EXPIRED) — không cover → fallback. */
  private static LeavePolicy resolveAt(
      List<LeavePolicy> history, LocalDate date, LeavePolicy fallback) {
    LeavePolicy best = null;
    for (LeavePolicy p : history) {
      if (!p.getEffectiveFrom().isAfter(date) && !p.getEffectiveTo().isBefore(date)) {
        if (best == null || p.getVersionNo() > best.getVersionNo()) {
          best = p;
        }
      }
    }
    return best != null ? best : fallback;
  }

  private boolean isEligible(
      Period period, Map<LocalDate, BigDecimal> workDays, LeaveSettings settings,
      LocalDate fyStart) {
    BigDecimal full = PeriodEngine.workingDays(period, settings, fyStart);
    BigDecimal actual = workDays.getOrDefault(period.start(), ZERO);
    // Kỳ 0 ngày công (NV chưa vào/về hết kỳ) không đủ điều kiện.
    return full.signum() > 0
        && actual.multiply(new BigDecimal("100"))
            .compareTo(full.multiply(settings.getMinWorkPercent())) >= 0;
  }

  /**
   * Bonus thâm niên theo rule của policy: VN_TIERED — bậc luật VN theo đủ năm tại ngày
   * tính (asOf = hôm nay); LINEAR — legacy (năm dương lịch × stepYears, trần maxBonus).
   * Bậc rỗng (data chưa seed) → self-heal tạm tính LINEAR.
   */
  BigDecimal seniorityBonus(Employee employee, int year, LeavePolicy policy) {
    if (employee.getHiredAt() == null) {
      return ZERO;
    }
    List<LeaveSeniorityTier> tiers =
        tierRepository.findByPolicyIdOrderByMinYearsAsc(policy.getId());
    return seniorityRuleRegistry.resolve(policy)
        .bonus(new SeniorityContext(
            employee.getHiredAt(), year, LocalDate.now(ZoneOffset.UTC), policy, tiers));
  }

  /** Bonus role: cao nhất trong các role của NV (comma-joined từ KC mirror). */
  private BigDecimal roleBonus(Employee employee, LeavePolicy policy) {
    if (employee.getRoles() == null || employee.getRoles().isBlank()) {
      return ZERO;
    }
    Set<String> employeeRoles = new java.util.HashSet<>();
    for (String role : employee.getRoles().split(",")) {
      String trimmed = role.trim();
      if (!trimmed.isEmpty()) {
        employeeRoles.add(trimmed);
      }
    }
    return roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()).stream()
        .filter(q -> employeeRoles.contains(q.getRole()))
        .map(LeaveRoleQuota::getBonusDays)
        .map(LeaveQuotaService::scale4)
        .max(BigDecimal::compareTo)
        .orElse(ZERO);
  }

  /** CARRYOVER auto — dòng tay (is_auto=false) giữ nguyên, recalc không đè. */
  private void carryoverAuto(
      Employee employee, int year, LeavePolicy policy, BigDecimal prevRemaining) {
    var existing = entitlementRepository.findByEmployeeIdAndYearAndSource(
        employee.getEmployeeId(), year, LeaveSource.CARRYOVER);
    if (existing.isPresent() && !existing.get().isAuto()) {
      return; // ADMIN sửa tay — không đè.
    }
    BigDecimal autoDays = prevRemaining.min(policy.getCarryoverMax()).setScale(4, RoundingMode.HALF_UP);
    if (autoDays.signum() <= 0) {
      existing.ifPresent(entitlementRepository::delete);
      return;
    }
    LeaveEntitlement entitlement = existing.orElseGet(() -> {
      LeaveEntitlement created = new LeaveEntitlement();
      created.setId(UUID.randomUUID());
      created.setEmployeeId(employee.getEmployeeId());
      created.setYear(year);
      created.setSource(LeaveSource.CARRYOVER);
      created.setAuto(true);
      return created;
    });
    entitlement.setDays(autoDays);
    entitlement.setAuto(true);
    applySnapshot(entitlement, policy);
    entitlementRepository.save(entitlement);
  }

  /** Còn lại theo NV của năm (tổng ledger − đã nghỉ; GĐ1 chưa có đơn → dùng = 0). */
  private Map<String, BigDecimal> remainingByEmployee(int year) {
    Map<String, BigDecimal> result = new HashMap<>();
    for (Object[] row : entitlementRepository.sumDaysByEmployeeAndSource(year)) {
      result.merge((String) row[0], scale4((BigDecimal) row[2]), BigDecimal::add);
    }
    return result;
  }

  private void upsertAuto(
      Employee employee, int year, LeaveSource source, BigDecimal days, LeavePolicy policy) {
    BigDecimal scaled = scale4(days);
    if (scaled.signum() <= 0 && source != LeaveSource.BASE) {
      entitlementRepository
          .findByEmployeeIdAndYearAndSource(employee.getEmployeeId(), year, source)
          .ifPresent(entitlementRepository::delete);
      return;
    }
    LeaveEntitlement entitlement = entitlementRepository
        .findByEmployeeIdAndYearAndSource(employee.getEmployeeId(), year, source)
        .orElseGet(() -> {
          LeaveEntitlement created = new LeaveEntitlement();
          created.setId(UUID.randomUUID());
          created.setEmployeeId(employee.getEmployeeId());
          created.setYear(year);
          created.setSource(source);
          created.setAuto(true);
          return created;
        });
    entitlement.setDays(scaled);
    entitlement.setAuto(true);
    applySnapshot(entitlement, policy);
    entitlementRepository.save(entitlement);
  }

  /** Snapshot truy vết: dòng tính theo version chính sách nào, khi nào. */
  private static void applySnapshot(LeaveEntitlement entitlement, LeavePolicy policy) {
    entitlement.setPolicyId(policy != null ? policy.getId() : null);
    entitlement.setCalculatedAt(Instant.now());
  }

  // ---- Cấp tay / xóa dòng manual ----

  @Transactional
  public LeaveEntitlementResponse grant(
      String employeeId, int year, LeaveGrantRequest request, String grantedBy) {
    LeaveSource source = parseManualSource(request.source());
    Employee employee = requireEmployee(employeeId);
    BigDecimal days = validateGrantDays(request.days());
    LeavePolicy inForce = policyService.getInForce(year);

    if (source == LeaveSource.CARRYOVER) {
      // Upsert 1 dòng CARRYOVER/NV/năm — ADMIN sửa tay, recalc không đè.
      LeaveEntitlement carryover = entitlementRepository
          .findByEmployeeIdAndYearAndSource(employeeId, year, LeaveSource.CARRYOVER)
          .orElseGet(() -> {
            LeaveEntitlement created = new LeaveEntitlement();
            created.setId(UUID.randomUUID());
            created.setEmployeeId(employeeId);
            created.setYear(year);
            created.setSource(LeaveSource.CARRYOVER);
            created.setAuto(false);
            return created;
          });
      if (days.compareTo(inForce.getCarryoverMax()) > 0) {
        throw new BusinessRuleException(
            "Phép chuyển năm vượt trần carryoverMax = " + inForce.getCarryoverMax());
      }
      carryover.setDays(days);
      carryover.setAuto(false);
      carryover.setReason(request.reason());
      carryover.setGrantedBy(grantedBy);
      applySnapshot(carryover, inForce);
      entitlementRepository.save(carryover);
      log.info("Cập nhật CARRYOVER tay {} ngày cho NV {} năm {}", days, employeeId, year);
      return LeaveEntitlementResponse.from(carryover);
    }

    LeaveEntitlement entitlement = new LeaveEntitlement();
    entitlement.setId(UUID.randomUUID());
    entitlement.setEmployeeId(employee.getEmployeeId());
    entitlement.setYear(year);
    entitlement.setSource(source);
    entitlement.setDays(days);
    entitlement.setReason(request.reason());
    entitlement.setGrantedBy(grantedBy);
    entitlement.setAuto(false);
    // BONUS/ADJUSTMENT không phụ thuộc chính sách → chỉ ghi thời điểm cấp.
    applySnapshot(entitlement, null);
    entitlementRepository.save(entitlement);
    log.info("Cấp {} {} ngày cho NV {} năm {}", source, days, employeeId, year);
    return LeaveEntitlementResponse.from(entitlement);
  }

  @Transactional
  public void deleteEntitlement(UUID id) {
    LeaveEntitlement entitlement = entitlementRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Entitlement", id));
    if (AUTO_SOURCES.contains(entitlement.getSource())) {
      throw new BusinessRuleException(
          "Không xóa được dòng tự động (" + entitlement.getSource() + ") — dùng tính lại/thay đổi chính sách");
    }
    entitlementRepository.delete(entitlement);
  }

  // ---- Truy vấn quota ----

  /**
   * Tổng hợp quota của 1 NV — ensure dòng auto trước khi tính (lazy self-heal).
   * Ngoài scope của người gọi (trừ ADMIN/GLOBAL_LEAD) → 403 (chống IDOR).
   */
  @Transactional
  public LeaveQuotaResponse getQuota(String employeeId, int year, String username,
      EmployeeScope scope) {
    Employee employee = requireEmployee(employeeId);
    if (scope != EmployeeScope.ALL) {
      accessResolver.resolve(username, scope).assertCanAccess(employee);
    }
    ensureAuto(employee, year);
    return buildQuota(employee, year, Map.of());
  }

  /**
   * Bảng tổng hợp cho HR/MANAGER — chỉ NV trong scope người gọi (MANAGER: self ∪ trực thuộc;
   * DEPT_LEAD: phòng quản lý ∪ trực thuộc ∪ self), ensure dòng auto cho NV khớp từ khóa.
   */
  @Transactional
  public List<LeaveQuotaResponse> listQuotas(int year, String q, String username,
      EmployeeScope scope) {
    policyService.getInForce(year); // self-heal FY chưa cấu hình trước khi list.
    EmployeeAccess access = scope == EmployeeScope.ALL
        ? EmployeeAccess.all() : accessResolver.resolve(username, scope);
    String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    List<LeaveQuotaResponse> result = new ArrayList<>();
    for (Employee employee : employeeRepository.findByStatusOrderByFullNameAsc("ACTIVE")) {
      if (!access.canAccess(employee)) {
        continue;
      }
      boolean matches = keyword.isEmpty()
          || employee.getEmployeeCode().toLowerCase(Locale.ROOT).contains(keyword)
          || employee.getFullName().toLowerCase(Locale.ROOT).contains(keyword)
          || (employee.getDepartmentName() != null
              && employee.getDepartmentName().toLowerCase(Locale.ROOT).contains(keyword));
      if (!matches) {
        continue;
      }
      ensureAuto(employee, year);
      result.add(buildQuota(employee, year, sumsByYear(year)));
    }
    return result;
  }

  private LeaveQuotaResponse buildQuota(
      Employee employee, int year, Map<String, Map<LeaveSource, BigDecimal>> yearSums) {
    Map<LeaveSource, BigDecimal> sums = yearSums.containsKey(employee.getEmployeeId())
        ? yearSums.get(employee.getEmployeeId())
        : sumsForEmployee(employee.getEmployeeId(), year);
    BigDecimal base = sums.getOrDefault(LeaveSource.BASE, ZERO);
    BigDecimal seniority = sums.getOrDefault(LeaveSource.SENIORITY, ZERO);
    BigDecimal roleDays = sums.getOrDefault(LeaveSource.POSITION, ZERO);
    BigDecimal bonus = sums.getOrDefault(LeaveSource.BONUS, ZERO);
    BigDecimal carryover = sums.getOrDefault(LeaveSource.CARRYOVER, ZERO);
    BigDecimal adjustment = sums.getOrDefault(LeaveSource.ADJUSTMENT, ZERO);
    BigDecimal total = base.add(seniority).add(roleDays).add(bonus).add(carryover).add(adjustment)
        .setScale(4, RoundingMode.HALF_UP);
    List<LeaveEntitlementResponse> entitlements = entitlementRepository
        .findByEmployeeIdAndYearOrderBySourceAsc(employee.getEmployeeId(), year).stream()
        .map(LeaveEntitlementResponse::from).toList();
    String note = noteRepository.findByEmployeeIdAndYear(employee.getEmployeeId(), year)
        .map(LeaveEmployeeNote::getNote)
        .orElse(null);
    // GĐ1: chưa có đơn nghỉ phép → used = 0, remaining = total.
    return new LeaveQuotaResponse(
        employee.getEmployeeId(), employee.getEmployeeCode(), employee.getFullName(),
        employee.getDepartmentName(), employee.getPosition(), employee.getRoles(),
        employee.getStatus(),
        base, seniority, roleDays, bonus, carryover, adjustment, total, total, note, entitlements);
  }

  private Map<String, Map<LeaveSource, BigDecimal>> sumsByYear(int year) {
    Map<String, Map<LeaveSource, BigDecimal>> result = new HashMap<>();
    for (Object[] row : entitlementRepository.sumDaysByEmployeeAndSource(year)) {
      result.computeIfAbsent((String) row[0], k -> new HashMap<>())
          .put((LeaveSource) row[1], scale4((BigDecimal) row[2]));
    }
    return result;
  }

  private Map<LeaveSource, BigDecimal> sumsForEmployee(String employeeId, int year) {
    Map<LeaveSource, BigDecimal> sums = new HashMap<>();
    for (LeaveEntitlement e : entitlementRepository.findByEmployeeIdAndYearOrderBySourceAsc(employeeId, year)) {
      sums.merge(e.getSource(), e.getDays(), BigDecimal::add);
    }
    sums.replaceAll((s, d) -> scale4(d));
    return sums;
  }

  // ---- Helpers ----

  private Employee requireEmployee(String employeeId) {
    return employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
  }

  private static LeaveSource parseManualSource(String source) {
    LeaveSource parsed;
    try {
      parsed = LeaveSource.valueOf(source == null ? "" : source.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new BusinessRuleException("Nguồn cấp phép không hợp lệ: " + source);
    }
    if (!MANUAL_SOURCES.contains(parsed)) {
      throw new BusinessRuleException(
          "Chỉ cấp tay được BONUS, CARRYOVER, ADJUSTMENT — nguồn " + parsed + " tính tự động");
    }
    return parsed;
  }

  static BigDecimal validateGrantDays(BigDecimal days) {
    if (days == null || days.signum() == 0) {
      throw new BusinessRuleException("Số ngày phải khác 0");
    }
    if (days.scale() > 4) {
      throw new BusinessRuleException("Số ngày tối đa 4 chữ số thập phân");
    }
    if (days.abs().compareTo(new BigDecimal("366")) > 0) {
      throw new BusinessRuleException("Số ngày ngoài khoảng cho phép (±366)");
    }
    return scale4(days);
  }

  static BigDecimal scale4(BigDecimal value) {
    return value.setScale(4, RoundingMode.HALF_UP);
  }
}
