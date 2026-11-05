package com.hrms.attendance.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.audit.AuditService;
import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeAccessResolver;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.leave.dto.LeaveEntitlementResponse;
import com.hrms.attendance.leave.dto.LeaveGrantRequest;
import com.hrms.attendance.leave.dto.LeaveQuotaResponse;
import com.hrms.attendance.leave.seniority.LinearSeniorityRule;
import com.hrms.attendance.leave.seniority.SeniorityRuleRegistry;
import com.hrms.attendance.leave.seniority.VietnamTieredSeniorityRule;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.security.EmployeeScope;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaveQuotaServiceTest {

  @Mock
  private LeavePolicyService policyService;

  @Mock
  private LeaveRoleQuotaRepository roleQuotaRepository;

  @Mock
  private LeaveSeniorityTierRepository tierRepository;

  @Mock
  private LeaveEntitlementRepository entitlementRepository;

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private EmployeeAccessResolver accessResolver;

  @Mock
  private LeaveSettingsService settingsService;

  @Mock
  private LeaveEmployeeNoteRepository noteRepository;

  @Spy
  private SeniorityRuleRegistry seniorityRuleRegistry = new SeniorityRuleRegistry(
      List.of(new VietnamTieredSeniorityRule(), new LinearSeniorityRule()));

  @Mock
  private AuditService auditService;

  @InjectMocks
  private LeaveQuotaService service;

  private LeavePolicy policy;
  private Employee employee;

  @BeforeEach
  void setUp() {
    policy = new LeavePolicy();
    policy.setId(UUID.randomUUID());
    policy.setYear(2026);
    policy.setVersionNo(1);
    policy.setStatus(PolicyStatus.ACTIVE);
    policy.setBaseDays(new BigDecimal("12"));
    policy.setSeniorityStepYears(1);
    policy.setSeniorityBonusDays(new BigDecimal("1"));
    policy.setSeniorityMaxBonus(new BigDecimal("5"));
    policy.setCarryoverMax(new BigDecimal("5"));

    employee = new Employee();
    employee.setEmployeeId("e-1");
    employee.setEmployeeCode("NV001");
    employee.setFullName("Nguyễn Văn A");
    employee.setRoles("DEPT_LEAD");
    employee.setStatus("ACTIVE");
    employee.setHiredAt(Instant.parse("2020-06-01T00:00:00Z"));

    // Phase 2: mẫu số (ngày công của kỳ) lấy từ roster — test không mock roster →
    // fullDaysByPeriod trả kết quả PeriodEngine như cũ (lenient: test grant không gọi tới).
    lenient()
        .when(settingsService.fullDaysByPeriod(any(), any(), any(), any()))
        .thenAnswer(
            inv -> {
              // Guard: when(mock.method(any())) gọi answer với args null lúc re-stub.
              List<PeriodEngine.Period> periods = inv.getArgument(1);
              if (periods == null) {
                return java.util.Map.of();
              }
              LeaveSettings s = inv.getArgument(2);
              java.time.LocalDate fyStart = inv.getArgument(3);
              Map<java.time.LocalDate, BigDecimal> map = new java.util.HashMap<>();
              for (PeriodEngine.Period p : periods) {
                map.put(p.start(), PeriodEngine.workingDays(p, s, fyStart));
              }
              return map;
            });
  }

  @Test
  void ensureAuto_createsBaseSeniorityRoleRows() {
    stubAllPeriodsEligible();
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of(roleQuota("DEPT_LEAD", "2")));

    service.ensureAuto(employee, 2026);

    ArgumentCaptor<LeaveEntitlement> captor = ArgumentCaptor.forClass(LeaveEntitlement.class);
    verify(entitlementRepository, org.mockito.Mockito.times(3)).save(captor.capture());
    Map<LeaveSource, BigDecimal> saved = new java.util.HashMap<>();
    captor.getAllValues().forEach(e -> saved.put(e.getSource(), e.getDays()));
    assertEquals(new BigDecimal("12.0000"), saved.get(LeaveSource.BASE));
    // hired 2020 → 6 năm → 6 bonus, trần 5
    assertEquals(new BigDecimal("5.0000"), saved.get(LeaveSource.SENIORITY));
    assertEquals(new BigDecimal("2.0000"), saved.get(LeaveSource.POSITION));
    assertTrue(captor.getAllValues().stream().allMatch(LeaveEntitlement::isAuto),
        "dòng ensureAuto phải là is_auto=true");
    // Snapshot truy vết: dòng auto gắn policy_id + calculated_at.
    assertTrue(captor.getAllValues().stream().allMatch(
        e -> policy.getId().equals(e.getPolicyId()) && e.getCalculatedAt() != null),
        "dòng auto phải snapshot policy_id + calculated_at");
  }

  /** Phase 2: mẫu số (full days) theo roster —1 kỳ không đạt minWorkPercent → BASE giảm. */
  @Test
  void ensureAuto_periodBelowMinWorkPercent_excludedFromBase() {
    LeaveSettings settings = defaultSettings();
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(settingsService.ensureDefaultWorkDays(any(), anyInt(), any(), any(), any()))
        .thenAnswer(inv -> {
          List<PeriodEngine.Period> periods = inv.getArgument(2);
          Map<java.time.LocalDate, BigDecimal> map = new java.util.HashMap<>();
          for (int i = 0; i < periods.size(); i++) {
            // Kỳ đầu NV chỉ có 10 ngày công (DEFAULT theo vào/về), 11 kỳ còn lại đủ 31.
            map.put(periods.get(i).start(),
                i == 0 ? new BigDecimal("10") : new BigDecimal("31"));
          }
          return map;
        });
    when(settingsService.fullDaysByPeriod(any(), any(), any(), any()))
        .thenAnswer(inv -> {
          List<PeriodEngine.Period> periods = inv.getArgument(1);
          Map<java.time.LocalDate, BigDecimal> map = new java.util.HashMap<>();
          for (PeriodEngine.Period p : periods) {
            map.put(p.start(), new BigDecimal("31"));
          }
          return map;
        });
    when(policyService.getInForce(2026)).thenReturn(policy);
    when(policyService.versionHistory(2026)).thenReturn(List.of(policy));
    policy.setEffectiveFrom(java.time.LocalDate.of(2026, 1, 1));
    policy.setEffectiveTo(java.time.LocalDate.of(2026, 12, 31));
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of());

    service.ensureAuto(employee, 2026);

    ArgumentCaptor<LeaveEntitlement> captor = ArgumentCaptor.forClass(LeaveEntitlement.class);
    verify(entitlementRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    LeaveEntitlement base = captor.getAllValues().stream()
        .filter(e -> e.getSource() == LeaveSource.BASE).findFirst().orElseThrow();
    assertEquals(new BigDecimal("11.0000"), base.getDays(),
        "kỳ 1: 10×100 < 31×50 → không đạt, BASE = 12 × 11/12 = 11.0000");
  }

  @Test
  void ensureAuto_existingRow_updatesInPlaceKeepsId() {
    stubAllPeriodsEligible();
    UUID existingId = UUID.randomUUID();
    LeaveEntitlement existing = new LeaveEntitlement();
    existing.setId(existingId);
    existing.setEmployeeId("e-1");
    existing.setYear(2026);
    existing.setSource(LeaveSource.BASE);
    existing.setDays(new BigDecimal("10.0000"));
    when(entitlementRepository.findByEmployeeIdAndYearAndSource("e-1", 2026, LeaveSource.BASE))
        .thenReturn(Optional.of(existing));
    when(entitlementRepository.findByEmployeeIdAndYearAndSource("e-1", 2026, LeaveSource.SENIORITY))
        .thenReturn(Optional.empty());
    when(entitlementRepository.findByEmployeeIdAndYearAndSource("e-1", 2026, LeaveSource.POSITION))
        .thenReturn(Optional.empty());
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of());

    service.ensureAuto(employee, 2026);

    ArgumentCaptor<LeaveEntitlement> captor = ArgumentCaptor.forClass(LeaveEntitlement.class);
    verify(entitlementRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    LeaveEntitlement base = captor.getAllValues().stream()
        .filter(e -> e.getSource() == LeaveSource.BASE).findFirst().orElseThrow();
    assertEquals(existingId, base.getId(), "dòng auto update tại chỗ, không sinh id mới");
    assertEquals(new BigDecimal("12.0000"), base.getDays());
  }

  @Test
  void ensureAuto_multipleRoles_takesMaxBonus() {
    stubAllPeriodsEligible();
    employee.setRoles(" ADMIN , DEPT_LEAD ");
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(eq("e-1"), eq(2026), any()))
        .thenReturn(Optional.empty());
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of(
            roleQuota("DEPT_LEAD", "1.5"),
            roleQuota("ADMIN", "3")));

    service.ensureAuto(employee, 2026);

    ArgumentCaptor<LeaveEntitlement> captor = ArgumentCaptor.forClass(LeaveEntitlement.class);
    verify(entitlementRepository, org.mockito.Mockito.times(3)).save(captor.capture());
    assertTrue(captor.getAllValues().stream()
        .anyMatch(e -> e.getSource() == LeaveSource.POSITION
            && e.getDays().equals(new BigDecimal("3.0000"))),
        "nhiều role → lấy bonus cao nhất (ADMIN=3)");
  }

  @Test
  void ensureAuto_noMatchingRole_deletesPositionRow() {
    stubAllPeriodsEligible();
    employee.setRoles("EMPLOYEE");
    LeaveEntitlement stalePosition = auto(LeaveSource.POSITION, new BigDecimal("3.0000"));
    when(entitlementRepository.findByEmployeeIdAndYearAndSource("e-1", 2026, LeaveSource.BASE))
        .thenReturn(Optional.empty());
    when(entitlementRepository.findByEmployeeIdAndYearAndSource("e-1", 2026, LeaveSource.SENIORITY))
        .thenReturn(Optional.empty());
    when(entitlementRepository.findByEmployeeIdAndYearAndSource("e-1", 2026, LeaveSource.POSITION))
        .thenReturn(Optional.of(stalePosition));
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of(roleQuota("ADMIN", "3")));

    service.ensureAuto(employee, 2026);

    verify(entitlementRepository).delete(stalePosition);
  }

  @Test
  void ensureAuto_twoVersions_sumsBasePerPeriod() {
    // Mid-year: v1 baseDays=12 cover 6 kỳ đầu, v2 baseDays=14 cover 6 kỳ sau (12 kỳ, FY 1/1).
    LeaveSettings settings = new LeaveSettings();
    settings.setId(UUID.randomUUID());
    settings.setFyStartMonth(1);
    settings.setFyStartDay(1);
    settings.setPeriodStartDay(1);
    settings.setPeriodEndDay(31);
    settings.setSaturdayPolicy(SaturdayPolicy.OFF);
    settings.setMinWorkPercent(new BigDecimal("50"));
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(settingsService.ensureDefaultWorkDays(any(), anyInt(), any(), any(), any()))
        .thenAnswer(inv -> {
          List<PeriodEngine.Period> periods = inv.getArgument(2);
          Map<java.time.LocalDate, BigDecimal> map = new java.util.HashMap<>();
          for (PeriodEngine.Period p : periods) {
            map.put(p.start(), new BigDecimal("31"));
          }
          return map;
        });
    when(policyService.getInForce(2026)).thenReturn(policy);

    LeavePolicy v2 = new LeavePolicy();
    v2.setId(UUID.randomUUID());
    v2.setYear(2026);
    v2.setVersionNo(2);
    v2.setStatus(PolicyStatus.EXPIRED);
    v2.setBaseDays(new BigDecimal("14"));
    // v1 hiệu lực 01/01–30/06, v2 từ 01/07 (EXPIRED giả lập do V3 thay — resolve theo ngày).
    policy.setEffectiveFrom(java.time.LocalDate.of(2026, 1, 1));
    policy.setEffectiveTo(java.time.LocalDate.of(2026, 6, 30));
    policy.setStatus(PolicyStatus.EXPIRED);
    v2.setEffectiveFrom(java.time.LocalDate.of(2026, 7, 1));
    v2.setEffectiveTo(java.time.LocalDate.of(2026, 12, 31));
    when(policyService.versionHistory(2026)).thenReturn(List.of(policy, v2));
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of(roleQuota("DEPT_LEAD", "1")));
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(eq("e-1"), eq(2026), any()))
        .thenReturn(Optional.empty());

    service.ensureAuto(employee, 2026);

    ArgumentCaptor<LeaveEntitlement> captor = ArgumentCaptor.forClass(LeaveEntitlement.class);
    verify(entitlementRepository, org.mockito.Mockito.times(3)).save(captor.capture());
    LeaveEntitlement base = captor.getAllValues().stream()
        .filter(e -> e.getSource() == LeaveSource.BASE).findFirst().orElseThrow();
    // Σ baseDays(6 kỳ v1 + 6 kỳ v2)/12 = (12×6 + 14×6)/12 = 13.0000
    assertEquals(new BigDecimal("13.0000"), base.getDays(),
        "BASE per-period: các kỳ trước giữ version cũ, kỳ sau theo version mới");
    assertNotNull(base.getPolicyId(), "snapshot policy_id theo in-force lúc tính");
  }

  @Test
  void seniorityBonus_stepDisabled_returnsZero() {
    policy.setSeniorityStepYears(0);
    assertEquals(LeaveQuotaService.ZERO, service.seniorityBonus(employee, 2026, policy));
  }

  @Test
  void seniorityBonus_vnTiered_usesLawTiersAtAsOf() {
    // NV đủ 11 năm tại ngày tính → bậc ≥10 năm → +4 (luật VN: tổng 16 = base 12 + 4).
    policy.setSeniorityRule(SeniorityRuleType.VN_TIERED);
    employee.setHiredAt(java.time.LocalDate.now(ZoneOffset.UTC).minusYears(11)
        .atStartOfDay(ZoneOffset.UTC).toInstant());
    when(tierRepository.findByPolicyIdOrderByMinYearsAsc(policy.getId()))
        .thenReturn(lawTiers());

    assertEquals(new BigDecimal("4.0000"), service.seniorityBonus(employee, 2026, policy));
  }

  @Test
  void seniorityBonus_vnTiered_emptyTiers_selfHealsToLinear() {
    // Chưa seed bậc → self-heal LINEAR (6 năm × stepYears 1 × bonusDays 1, trần 5).
    policy.setSeniorityRule(SeniorityRuleType.VN_TIERED);
    when(tierRepository.findByPolicyIdOrderByMinYearsAsc(policy.getId()))
        .thenReturn(List.of());

    assertEquals(new BigDecimal("5.0000"), service.seniorityBonus(employee, 2026, policy));
  }

  @Test
  void grant_bonus_savesWithActor() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(policyService.getInForce(2026)).thenReturn(policy);
    when(settingsService.getOrCreateSettings()).thenReturn(defaultSettings());
    when(entitlementRepository.save(any(LeaveEntitlement.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    LeaveEntitlementResponse response = service.grant(
        "e-1", 2026,
        new LeaveGrantRequest("BONUS", new BigDecimal("0.1"), "Xuất sắc", null), "hr");

    assertEquals(new BigDecimal("0.1000"), response.days());
    assertEquals("hr", response.grantedBy());
    assertEquals("BONUS", response.source());
    assertFalse(response.isAuto(), "cấp tay là is_auto=false");
    assertEquals(null, response.policyId(), "BONUS không phụ thuộc policy → policy_id null");
    assertNotNull(response.calculatedAt(), "cấp tay vẫn ghi calculated_at");
    // Không truyền expiresAt → mặc định cuối năm TC (FY 1/1 → 31/12/2026).
    assertEquals(java.time.LocalDate.of(2026, 12, 31), response.expiresAt(),
        "BONUS không truyền hạn → hết hạn cuối năm tài chính");
    assertFalse(response.expired());
  }

  @Test
  void grant_bonus_customExpiresAt_savedAndEchoed() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(policyService.getInForce(2026)).thenReturn(policy);
    when(settingsService.getOrCreateSettings()).thenReturn(defaultSettings());
    when(entitlementRepository.save(any(LeaveEntitlement.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    java.time.LocalDate custom = java.time.LocalDate.now(ZoneOffset.UTC).plusDays(30);

    LeaveEntitlementResponse response = service.grant(
        "e-1", 2026,
        new LeaveGrantRequest("BONUS", new BigDecimal("1"), "Voucher mid-year", custom), "hr");

    assertEquals(custom, response.expiresAt(),
        "BONUS được tùy chỉnh thời hạn (kể cả vượt cuối năm TC)");
    assertFalse(response.expired());
  }

  @Test
  void grant_bonus_expiresAtInPast_throws() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(policyService.getInForce(2026)).thenReturn(policy);
    assertThrows(BusinessRuleException.class, () -> service.grant(
        "e-1", 2026,
        new LeaveGrantRequest("BONUS", new BigDecimal("1"), null,
            java.time.LocalDate.now(ZoneOffset.UTC).minusDays(1)),
        "hr"));
  }

  @Test
  void grant_adjustmentWithExpiresAt_throws() {
    // Chỉ BONUS được tùy chỉnh — ADJUSTMENT/CARRYOVER luôn mặc định cuối năm TC.
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(policyService.getInForce(2026)).thenReturn(policy);
    assertThrows(BusinessRuleException.class, () -> service.grant(
        "e-1", 2026,
        new LeaveGrantRequest("ADJUSTMENT", new BigDecimal("1"), null,
            java.time.LocalDate.now(ZoneOffset.UTC).plusDays(30)),
        "hr"));
  }

  @Test
  void grant_carryover_exceedsMax_throws() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(policyService.getInForce(2026)).thenReturn(policy);
    when(settingsService.getOrCreateSettings()).thenReturn(defaultSettings());
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(
        "e-1", 2026, LeaveSource.CARRYOVER)).thenReturn(Optional.empty());

    // carryoverMax = 5 → 6 ngày bị chặn
    assertThrows(BusinessRuleException.class, () -> service.grant(
        "e-1", 2026,
        new LeaveGrantRequest("CARRYOVER", new BigDecimal("6"), null, null), "hr"));
  }

  @Test
  void grant_carryover_upsertSingleRow_manualNotOverwrittenByRecalc() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(policyService.getInForce(2026)).thenReturn(policy);
    when(settingsService.getOrCreateSettings()).thenReturn(defaultSettings());
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(
        "e-1", 2026, LeaveSource.CARRYOVER)).thenReturn(Optional.empty());
    when(entitlementRepository.save(any(LeaveEntitlement.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    LeaveEntitlementResponse response = service.grant(
        "e-1", 2026,
        new LeaveGrantRequest("CARRYOVER", new BigDecimal("3"), null, null), "hr");

    assertEquals(new BigDecimal("3.0000"), response.days());
    assertFalse(response.isAuto(), "CARRYOVER do ADMIN cấp → is_auto=false, recalc không đè");
    assertEquals(policy.getId(), response.policyId(),
        "CARRYOVER validate theo carryoverMax → snapshot policy in-force");
    assertEquals(java.time.LocalDate.of(2026, 12, 31), response.expiresAt(),
        "CARRYOVER luôn hết hạn cuối năm TC (không tùy chỉnh được)");
  }

  @Test
  void grant_autoSource_throws() {
    assertThrows(BusinessRuleException.class, () -> service.grant(
        "e-1", 2026,
        new LeaveGrantRequest("BASE", new BigDecimal("1"), null, null), "hr"));
  }

  @Test
  void grant_zeroDays_throws() {
    assertThrows(BusinessRuleException.class,
        () -> LeaveQuotaService.validateGrantDays(BigDecimal.ZERO));
  }

  @Test
  void grant_decimalPrecision_kept() {
    BigDecimal days = LeaveQuotaService.validateGrantDays(new BigDecimal("0.125"));
    assertEquals(new BigDecimal("0.1250"), days);
    assertEquals(0, new BigDecimal("0.1").add(new BigDecimal("0.2"))
        .compareTo(new BigDecimal("0.3")), "BigDecimal không có sai số nhị phân");
  }

  @Test
  void deleteEntitlement_autoSource_throws() {
    LeaveEntitlement auto = new LeaveEntitlement();
    auto.setId(UUID.randomUUID());
    auto.setSource(LeaveSource.BASE);
    when(entitlementRepository.findById(auto.getId())).thenReturn(Optional.of(auto));

    assertThrows(BusinessRuleException.class, () -> service.deleteEntitlement(auto.getId()));
  }

  @Test
  void deleteEntitlement_manual_deletes() {
    LeaveEntitlement manual = new LeaveEntitlement();
    manual.setId(UUID.randomUUID());
    manual.setSource(LeaveSource.BONUS);
    when(entitlementRepository.findById(manual.getId())).thenReturn(Optional.of(manual));

    service.deleteEntitlement(manual.getId());

    verify(entitlementRepository).delete(manual);
  }

  @Test
  void getQuota_totalsSumAllSources() {
    stubAllPeriodsEligible();
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(eq("e-1"), eq(2026), any()))
        .thenReturn(Optional.empty());
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of());
    when(entitlementRepository.findDetail(eq("e-1"), eq(2026), eq(LeaveSource.BONUS), any()))
        .thenReturn(List.of(
            auto(LeaveSource.BASE, new BigDecimal("12.0000")),
            auto(LeaveSource.SENIORITY, new BigDecimal("5.0000")),
            manual(LeaveSource.BONUS, new BigDecimal("0.5000")),
            manual(LeaveSource.CARRYOVER, new BigDecimal("2.0000")),
            manual(LeaveSource.ADJUSTMENT, new BigDecimal("-1.0000"))));

    LeaveQuotaResponse quota = service.getQuota("e-1", 2026, "hr", EmployeeScope.ALL);

    assertEquals(new BigDecimal("18.5000"), quota.totalDays());
    assertEquals(new BigDecimal("18.5000"), quota.remainingDays());
    assertEquals(5, quota.entitlements().size());
    assertEquals("DEPT_LEAD", quota.roles(), "response trả roles mirror");
    assertEquals(java.time.LocalDate.of(2026, 1, 1), quota.fyStart(),
        "response trả range năm TC (settings FY 1/1) — FE label bộ chọn năm TC");
    assertEquals(java.time.LocalDate.of(2026, 12, 31), quota.fyEnd());
  }

  @Test
  void getQuota_expiredBonus_excludedFromTotals_shownInDetail() {
    stubAllPeriodsEligible();
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(eq("e-1"), eq(2026), any()))
        .thenReturn(Optional.empty());
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of());
    LeaveEntitlement expiredBonus = manual(LeaveSource.BONUS, new BigDecimal("3.0000"));
    expiredBonus.setExpiresAt(java.time.LocalDate.now(ZoneOffset.UTC).minusDays(1));
    LeaveEntitlement aliveBase = auto(LeaveSource.BASE, new BigDecimal("12.0000"));
    aliveBase.setExpiresAt(java.time.LocalDate.of(2026, 12, 31));
    when(entitlementRepository.findDetail(eq("e-1"), eq(2026), eq(LeaveSource.BONUS), any()))
        .thenReturn(List.of(aliveBase, expiredBonus));

    LeaveQuotaResponse quota = service.getQuota("e-1", 2026, "hr", EmployeeScope.ALL);

    // BONUS hết hạn bị loại khỏi Tổng/Còn lại (hết hạn từ 0h hôm sau ngày expires_at).
    assertEquals(new BigDecimal("12.0000"), quota.totalDays());
    assertEquals(2, quota.entitlements().size(), "chi tiết vẫn hiện dòng hết hạn để đối chiếu");
    LeaveEntitlementResponse expiredRow = quota.entitlements().stream()
        .filter(LeaveEntitlementResponse::expired).findFirst().orElseThrow();
    assertEquals("BONUS", expiredRow.source());
    assertEquals(java.time.LocalDate.now(ZoneOffset.UTC).minusDays(1), expiredRow.expiresAt());
  }

  @Test
  void getQuota_spillBonusFromPrevYear_countedAndMarkedWithItsYear() {
    stubAllPeriodsEligible();
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(eq("e-1"), eq(2026), any()))
        .thenReturn(Optional.empty());
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of());
    // BONUS cấp năm 2025 với hạn vượt năm TC (vd 31/3/2027) → còn hạn → spill sang 2026.
    LeaveEntitlement spill = manual(LeaveSource.BONUS, new BigDecimal("2.0000"));
    spill.setYear(2025);
    spill.setExpiresAt(java.time.LocalDate.now(ZoneOffset.UTC).plusDays(60));
    LeaveEntitlement aliveBase = auto(LeaveSource.BASE, new BigDecimal("12.0000"));
    aliveBase.setExpiresAt(java.time.LocalDate.of(2026, 12, 31));
    when(entitlementRepository.findDetail(eq("e-1"), eq(2026), eq(LeaveSource.BONUS), any()))
        .thenReturn(List.of(aliveBase, spill));

    LeaveQuotaResponse quota = service.getQuota("e-1", 2026, "hr", EmployeeScope.ALL);

    assertEquals(new BigDecimal("14.0000"), quota.totalDays(),
        "BONUS năm trước còn hạn tự cộng sang năm mới (không cần bấm Chuyển năm)");
    LeaveEntitlementResponse spillRow = quota.entitlements().stream()
        .filter(r -> r.year() == 2025).findFirst().orElseThrow();
    assertEquals("BONUS", spillRow.source());
    assertFalse(spillRow.expired());
  }

  @Test
  void ensureAuto_rowsExpireAtFyEnd() {
    stubAllPeriodsEligible();
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(eq("e-1"), eq(2026), any()))
        .thenReturn(Optional.empty());
    when(roleQuotaRepository.findByPolicyIdOrderByRoleAsc(policy.getId()))
        .thenReturn(List.of(roleQuota("DEPT_LEAD", "2")));

    service.ensureAuto(employee, 2026);

    ArgumentCaptor<LeaveEntitlement> captor = ArgumentCaptor.forClass(LeaveEntitlement.class);
    verify(entitlementRepository, org.mockito.Mockito.times(3)).save(captor.capture());
    assertTrue(captor.getAllValues().stream().allMatch(
        e -> java.time.LocalDate.of(2026, 12, 31).equals(e.getExpiresAt())),
        "dòng auto mặc định hết hạn cuối năm tài chính (FY 1/1/2026 → 31/12/2026)");
  }

  private LeaveRoleQuota roleQuota(String role, String days) {
    LeaveRoleQuota q = new LeaveRoleQuota();
    q.setId(UUID.randomUUID());
    q.setPolicyId(policy.getId());
    q.setRole(role);
    q.setBonusDays(new BigDecimal(days));
    return q;
  }

  /** Bậc mặc định theo luật VN: 0/5/10/15/20 năm → +0/2/4/6/8. */
  private List<LeaveSeniorityTier> lawTiers() {
    List<LeaveSeniorityTier> tiers = new java.util.ArrayList<>();
    for (int[] t : new int[][] {{0, 0}, {5, 2}, {10, 4}, {15, 6}, {20, 8}}) {
      LeaveSeniorityTier tier = new LeaveSeniorityTier();
      tier.setPolicyId(policy.getId());
      tier.setMinYears(t[0]);
      tier.setBonusDays(new BigDecimal(t[1]));
      tiers.add(tier);
    }
    return tiers;
  }

  /**
   * Stub settings FY 1/1 kỳ 1/31 T7 OFF — mọi kỳ đạt đủ điều kiện (actual=31 ≥ full)
   * → computeBase trả BASE = baseDays × 12/12 = 12.0000.
   */
  private void stubAllPeriodsEligible() {
    LeaveSettings settings = defaultSettings();
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(settingsService.ensureDefaultWorkDays(any(), anyInt(), any(), any(), any()))
        .thenAnswer(inv -> {
          List<PeriodEngine.Period> periods = inv.getArgument(2);
          Map<java.time.LocalDate, BigDecimal> map = new java.util.HashMap<>();
          for (PeriodEngine.Period p : periods) {
            map.put(p.start(), new BigDecimal("31"));
          }
          return map;
        });
    when(policyService.getInForce(2026)).thenReturn(policy);
    when(policyService.versionHistory(2026)).thenReturn(List.of(policy));
    policy.setEffectiveFrom(java.time.LocalDate.of(2026, 1, 1));
    policy.setEffectiveTo(java.time.LocalDate.of(2026, 12, 31));
  }

  /** Settings FY 1/1 kỳ 1/31 T7 OFF — mặc định cho test grant (fyEnd 2026 = 31/12/2026). */
  private LeaveSettings defaultSettings() {
    LeaveSettings settings = new LeaveSettings();
    settings.setId(UUID.randomUUID());
    settings.setFyStartMonth(1);
    settings.setFyStartDay(1);
    settings.setPeriodStartDay(1);
    settings.setPeriodEndDay(31);
    settings.setSaturdayPolicy(SaturdayPolicy.OFF);
    settings.setMinWorkPercent(new BigDecimal("50"));
    return settings;
  }

  private LeaveEntitlement auto(LeaveSource source, BigDecimal days) {
    LeaveEntitlement e = new LeaveEntitlement();
    e.setId(UUID.randomUUID());
    e.setEmployeeId("e-1");
    e.setYear(2026);
    e.setSource(source);
    e.setDays(days);
    e.setAuto(true);
    return e;
  }

  private LeaveEntitlement manual(LeaveSource source, BigDecimal days) {
    LeaveEntitlement e = auto(source, days);
    e.setAuto(false);
    return e;
  }
}
