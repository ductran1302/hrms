package com.hrms.attendance.leave;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.leave.dto.LeaveEntitlementResponse;
import com.hrms.attendance.leave.dto.LeaveGrantRequest;
import com.hrms.attendance.leave.dto.LeavePolicyRequest;
import com.hrms.attendance.leave.dto.LeavePolicyResponse;
import com.hrms.attendance.leave.dto.LeaveQuotaResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Quỹ phép (GĐ1 — quota thuần, chưa trừ đơn nghỉ phép).
 *
 * <p>Nguyên tắc: ledger {@code leave_entitlements} — mỗi nguồn 1 dòng (audit rõ). Tính tự động
 * BASE/SENIORITY/POSITION {@code upsert at-place} khi ensure/recalculate; HR cấp tay
 * BONUS/CARRYOVER/ADJUSTMENT. Số ngày là {@link BigDecimal} (numeric(8,4)) — không bao giờ
 * float/double để tránh sai số thập phân (0.1 + 0.2 = 0.3 đúng).
 *
 * <p>Thâm niên: {@code years = year - hiredAt.year}, mỗi {@code seniorityStepYears} năm được
 * {@code seniorityBonusDays}, cộng dồn tới {@code seniorityMaxBonus}. Chức vụ: match
 * {@code employees.position} với {@code leave_position_quotas.position_name} theo tên
 * (position là free-text, không FK — GĐ2 có thể migrate).
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

  private final LeavePolicyRepository policyRepository;
  private final LeavePositionQuotaRepository positionQuotaRepository;
  private final LeaveEntitlementRepository entitlementRepository;
  private final EmployeeRepository employeeRepository;

  // ---- Chính sách ----

  /** Đọc policy theo năm — tự tạo policy mặc định nếu HR chưa cấu hình (self-healing). */
  @Transactional
  public LeavePolicy getOrCreatePolicy(int year) {
    return policyRepository.findByYear(year).orElseGet(() -> {
      LeavePolicy policy = new LeavePolicy();
      policy.setId(UUID.randomUUID());
      policy.setYear(year);
      policy.setBaseDays(new BigDecimal("12"));
      policy.setSeniorityStepYears(1);
      policy.setSeniorityBonusDays(new BigDecimal("1"));
      policy.setSeniorityMaxBonus(new BigDecimal("5"));
      policy.setCarryoverMax(new BigDecimal("5"));
      log.info("Tạo leave policy mặc định năm {}", year);
      return policyRepository.save(policy);
    });
  }

  @Transactional(readOnly = true)
  public LeavePolicyResponse getPolicy(int year) {
    LeavePolicy policy = getOrCreatePolicy(year);
    return toPolicyResponse(policy);
  }

  @Transactional(readOnly = true)
  public List<LeavePolicyResponse> listPolicies() {
    return policyRepository.findAll().stream().map(this::toPolicyResponse).toList();
  }

  @Transactional
  public LeavePolicyResponse savePolicy(LeavePolicyRequest request) {
    validateDays(request.baseDays(), "baseDays");
    validateDays(request.seniorityBonusDays(), "seniorityBonusDays");
    validateDays(request.seniorityMaxBonus(), "seniorityMaxBonus");
    validateDays(request.carryoverMax(), "carryoverMax");
    if (request.seniorityStepYears() < 0) {
      throw new BusinessRuleException("seniorityStepYears phải ≥ 0");
    }
    LeavePolicy policy = getOrCreatePolicy(request.year());
    policy.setBaseDays(scale4(request.baseDays()));
    policy.setSeniorityStepYears(request.seniorityStepYears());
    policy.setSeniorityBonusDays(scale4(request.seniorityBonusDays()));
    policy.setSeniorityMaxBonus(scale4(request.seniorityMaxBonus()));
    policy.setCarryoverMax(scale4(request.carryoverMax()));
    policyRepository.save(policy);

    positionQuotaRepository.deleteByPolicyId(policy.getId());
    if (request.positionQuotas() != null) {
      for (LeavePolicyRequest.PositionQuota q : request.positionQuotas()) {
        if (q.positionName() == null || q.positionName().isBlank()) {
          throw new BusinessRuleException("positionName không được để trống");
        }
        validateDays(q.bonusDays(), "bonusDays");
        if (q.bonusDays().signum() <= 0) {
          throw new BusinessRuleException("bonusDays của chức vụ phải > 0");
        }
        LeavePositionQuota quota = new LeavePositionQuota();
        quota.setPolicyId(policy.getId());
        quota.setPositionName(q.positionName().trim());
        quota.setBonusDays(scale4(q.bonusDays()));
        positionQuotaRepository.save(quota);
      }
    }
    log.info("Lưu chính sách quỹ phép năm {}", request.year());
    return toPolicyResponse(policy);
  }

  // ---- Tính tự động + ledger ----

  /**
   * Tính lại quota tự động (BASE/SENIORITY/POSITION) cho toàn bộ NV ACTIVE trong năm.
   * Dòng manual (BONUS/CARRYOVER/ADJUSTMENT) giữ nguyên. Trả về số nhân viên đã ensure.
   */
  @Transactional
  public int recalculate(int year) {
    LeavePolicy policy = getOrCreatePolicy(year);
    List<Employee> employees = employeeRepository.findByStatusOrderByFullNameAsc("ACTIVE");
    for (Employee employee : employees) {
      ensureAuto(employee, year, policy);
    }
    log.info("Tính lại quỹ phép năm {}: {} nhân viên", year, employees.size());
    return employees.size();
  }

  /** Đảm bảo các dòng auto tồn tại & đúng theo policy hiện tại (idempotent — update tại chỗ). */
  @Transactional
  public void ensureAuto(Employee employee, int year, LeavePolicy policy) {
    upsertAuto(employee, year, policy, LeaveSource.BASE, policy.getBaseDays());
    upsertAuto(employee, year, policy, LeaveSource.SENIORITY, seniorityBonus(employee, year, policy));
    BigDecimal positionBonus = positionBonus(employee, policy);
    if (positionBonus.signum() > 0) {
      upsertAuto(employee, year, policy, LeaveSource.POSITION, positionBonus);
    } else {
      entitlementRepository
          .findByEmployeeIdAndYearAndSource(employee.getEmployeeId(), year, LeaveSource.POSITION)
          .ifPresent(entitlementRepository::delete);
    }
  }

  /** Bonus thâm niên: mỗi stepYears được bonusDays, trần maxBonus. */
  static BigDecimal seniorityBonus(Employee employee, int year, LeavePolicy policy) {
    if (policy.getSeniorityStepYears() <= 0
        || policy.getSeniorityBonusDays().signum() <= 0
        || employee.getHiredAt() == null) {
      return ZERO;
    }
    int hiredYear = employee.getHiredAt().atZone(ZoneOffset.UTC).getYear();
    int years = year - hiredYear;
    if (years <= 0) {
      return ZERO;
    }
    int steps = years / policy.getSeniorityStepYears();
    BigDecimal bonus = policy.getSeniorityBonusDays().multiply(BigDecimal.valueOf(steps));
    return bonus.min(policy.getSeniorityMaxBonus()).setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal positionBonus(Employee employee, LeavePolicy policy) {
    String position = normalize(employee.getPosition());
    if (position.isEmpty()) {
      return ZERO;
    }
    return positionQuotaRepository.findByPolicyIdOrderByPositionNameAsc(policy.getId()).stream()
        .filter(q -> normalize(q.getPositionName()).equals(position))
        .map(LeavePositionQuota::getBonusDays)
        .findFirst()
        .map(LeaveQuotaService::scale4)
        .orElse(ZERO);
  }

  private void upsertAuto(
      Employee employee, int year, LeavePolicy policy, LeaveSource source, BigDecimal days) {
    BigDecimal scaled = scale4(days);
    if (scaled.signum() <= 0 && source != LeaveSource.BASE) {
      // BASE luôn có mặt kể cả 0 để hiển thị; nguồn khác 0 thì xóa dòng auto.
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
          return created;
        });
    entitlement.setDays(scaled);
    entitlementRepository.save(entitlement);
  }

  // ---- Cấp tay / xóa dòng manual ----

  @Transactional
  public LeaveEntitlementResponse grant(
      String employeeId, int year, LeaveGrantRequest request, String grantedBy) {
    LeaveSource source = parseManualSource(request.source());
    Employee employee = requireEmployee(employeeId);
    BigDecimal days = validateGrantDays(request.days());
    LeavePolicy policy = getOrCreatePolicy(year);

    if (source == LeaveSource.CARRYOVER) {
      BigDecimal existing = sumBySource(employeeId, year, LeaveSource.CARRYOVER);
      if (existing.add(days).compareTo(policy.getCarryoverMax()) > 0) {
        throw new BusinessRuleException(
            "Tổng phép chuyển năm vượt trần carryoverMax = " + policy.getCarryoverMax());
      }
    }
    LeaveEntitlement entitlement = new LeaveEntitlement();
    entitlement.setId(UUID.randomUUID());
    entitlement.setEmployeeId(employee.getEmployeeId());
    entitlement.setYear(year);
    entitlement.setSource(source);
    entitlement.setDays(days);
    entitlement.setReason(request.reason());
    entitlement.setGrantedBy(grantedBy);
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

  /** Tổng hợp quota của 1 NV — ensure dòng auto trước khi tính (lazy self-heal). */
  @Transactional
  public LeaveQuotaResponse getQuota(String employeeId, int year) {
    Employee employee = requireEmployee(employeeId);
    LeavePolicy policy = getOrCreatePolicy(year);
    ensureAuto(employee, year, policy);
    return buildQuota(employee, year, Map.of());
  }

  /** Bảng tổng hợp cho HR/MANAGER — ensure dòng auto cho mọi NV khớp từ khóa. */
  @Transactional
  public List<LeaveQuotaResponse> listQuotas(int year, String q) {
    LeavePolicy policy = getOrCreatePolicy(year);
    String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    List<LeaveQuotaResponse> result = new ArrayList<>();
    for (Employee employee : employeeRepository.findByStatusOrderByFullNameAsc("ACTIVE")) {
      boolean matches = keyword.isEmpty()
          || employee.getEmployeeCode().toLowerCase(Locale.ROOT).contains(keyword)
          || employee.getFullName().toLowerCase(Locale.ROOT).contains(keyword)
          || (employee.getDepartmentName() != null
              && employee.getDepartmentName().toLowerCase(Locale.ROOT).contains(keyword));
      if (!matches) {
        continue;
      }
      ensureAuto(employee, year, policy);
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
    BigDecimal position = sums.getOrDefault(LeaveSource.POSITION, ZERO);
    BigDecimal bonus = sums.getOrDefault(LeaveSource.BONUS, ZERO);
    BigDecimal carryover = sums.getOrDefault(LeaveSource.CARRYOVER, ZERO);
    BigDecimal adjustment = sums.getOrDefault(LeaveSource.ADJUSTMENT, ZERO);
    BigDecimal total = base.add(seniority).add(position).add(bonus).add(carryover).add(adjustment)
        .setScale(4, RoundingMode.HALF_UP);
    List<LeaveEntitlementResponse> entitlements = entitlementRepository
        .findByEmployeeIdAndYearOrderBySourceAsc(employee.getEmployeeId(), year).stream()
        .map(LeaveEntitlementResponse::from).toList();
    // GĐ1: chưa có đơn nghỉ phép → used = 0, remaining = total.
    return new LeaveQuotaResponse(
        employee.getEmployeeId(), employee.getEmployeeCode(), employee.getFullName(),
        employee.getDepartmentName(), employee.getPosition(), employee.getStatus(),
        base, seniority, position, bonus, carryover, adjustment, total, total, entitlements);
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

  private BigDecimal sumBySource(String employeeId, int year, LeaveSource source) {
    return entitlementRepository.findByEmployeeIdAndYearOrderBySourceAsc(employeeId, year).stream()
        .filter(e -> e.getSource() == source)
        .map(LeaveEntitlement::getDays)
        .reduce(ZERO, BigDecimal::add);
  }

  // ---- Helpers ----

  private LeavePolicyResponse toPolicyResponse(LeavePolicy policy) {
    List<LeavePolicyResponse.PositionQuotaDto> quotas = positionQuotaRepository
        .findByPolicyIdOrderByPositionNameAsc(policy.getId()).stream()
        .map(q -> new LeavePolicyResponse.PositionQuotaDto(q.getPositionName(), q.getBonusDays()))
        .toList();
    return LeavePolicyResponse.from(policy, quotas);
  }

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

  private static void validateDays(BigDecimal days, String field) {
    if (days == null || days.signum() < 0) {
      throw new BusinessRuleException(field + " phải ≥ 0");
    }
    if (days.scale() > 4) {
      throw new BusinessRuleException(field + " tối đa 4 chữ số thập phân");
    }
  }

  static BigDecimal scale4(BigDecimal value) {
    return value.setScale(4, RoundingMode.HALF_UP);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
