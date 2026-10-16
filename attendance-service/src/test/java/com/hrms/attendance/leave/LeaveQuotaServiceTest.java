package com.hrms.attendance.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.leave.dto.LeaveEntitlementResponse;
import com.hrms.attendance.leave.dto.LeaveGrantRequest;
import com.hrms.attendance.leave.dto.LeaveQuotaResponse;
import com.hrms.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaveQuotaServiceTest {

  @Mock
  private LeavePolicyRepository policyRepository;

  @Mock
  private LeavePositionQuotaRepository positionQuotaRepository;

  @Mock
  private LeaveEntitlementRepository entitlementRepository;

  @Mock
  private EmployeeRepository employeeRepository;

  @InjectMocks
  private LeaveQuotaService service;

  private LeavePolicy policy;
  private Employee employee;

  @BeforeEach
  void setUp() {
    policy = new LeavePolicy();
    policy.setId(UUID.randomUUID());
    policy.setYear(2026);
    policy.setBaseDays(new BigDecimal("12"));
    policy.setSeniorityStepYears(1);
    policy.setSeniorityBonusDays(new BigDecimal("1"));
    policy.setSeniorityMaxBonus(new BigDecimal("5"));
    policy.setCarryoverMax(new BigDecimal("5"));

    employee = new Employee();
    employee.setEmployeeId("e-1");
    employee.setEmployeeCode("NV001");
    employee.setFullName("Nguyễn Văn A");
    employee.setPosition("Trưởng phòng");
    employee.setStatus("ACTIVE");
    employee.setHiredAt(Instant.parse("2020-06-01T00:00:00Z"));
  }

  @Test
  void getOrCreatePolicy_firstTime_createsDefault() {
    when(policyRepository.findByYear(2026)).thenReturn(Optional.empty());
    when(policyRepository.save(any(LeavePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

    LeavePolicy created = service.getOrCreatePolicy(2026);

    assertEquals(new BigDecimal("12"), created.getBaseDays());
    assertEquals(1, created.getSeniorityStepYears());
    assertEquals(new BigDecimal("5"), created.getSeniorityMaxBonus());
  }

  @Test
  void ensureAuto_createsBaseSeniorityPositionRows() {
    when(positionQuotaRepository.findByPolicyIdOrderByPositionNameAsc(policy.getId()))
        .thenReturn(List.of(positionQuota("Trưởng phòng", "2")));

    service.ensureAuto(employee, 2026, policy);

    ArgumentCaptor<LeaveEntitlement> captor = ArgumentCaptor.forClass(LeaveEntitlement.class);
    verify(entitlementRepository, org.mockito.Mockito.times(3)).save(captor.capture());
    Map<LeaveSource, BigDecimal> saved = new java.util.HashMap<>();
    captor.getAllValues().forEach(e -> saved.put(e.getSource(), e.getDays()));
    assertEquals(new BigDecimal("12.0000"), saved.get(LeaveSource.BASE));
    // hired 2020 → 6 năm → 6 bonus, trần 5
    assertEquals(new BigDecimal("5.0000"), saved.get(LeaveSource.SENIORITY));
    assertEquals(new BigDecimal("2.0000"), saved.get(LeaveSource.POSITION));
  }

  @Test
  void ensureAuto_existingRow_updatesInPlaceKeepsId() {
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
    when(positionQuotaRepository.findByPolicyIdOrderByPositionNameAsc(policy.getId()))
        .thenReturn(List.of());

    service.ensureAuto(employee, 2026, policy);

    ArgumentCaptor<LeaveEntitlement> captor = ArgumentCaptor.forClass(LeaveEntitlement.class);
    verify(entitlementRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    LeaveEntitlement base = captor.getAllValues().stream()
        .filter(e -> e.getSource() == LeaveSource.BASE).findFirst().orElseThrow();
    assertEquals(existingId, base.getId(), "dòng auto update tại chỗ, không sinh id mới");
    assertEquals(new BigDecimal("12.0000"), base.getDays());
  }

  @Test
  void ensureAuto_positionMatch_caseInsensitive() {
    employee.setPosition("  TRƯỞNG PHÒNG ");
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(eq("e-1"), eq(2026), any()))
        .thenReturn(Optional.empty());
    when(positionQuotaRepository.findByPolicyIdOrderByPositionNameAsc(policy.getId()))
        .thenReturn(List.of(positionQuota("trưởng phòng", "1.5")));

    service.ensureAuto(employee, 2026, policy);

    ArgumentCaptor<LeaveEntitlement> captor = ArgumentCaptor.forClass(LeaveEntitlement.class);
    verify(entitlementRepository, org.mockito.Mockito.times(3)).save(captor.capture());
    assertTrue(captor.getAllValues().stream()
        .anyMatch(e -> e.getSource() == LeaveSource.POSITION
            && e.getDays().equals(new BigDecimal("1.5000"))));
  }

  @Test
  void seniorityBonus_stepDisabled_returnsZero() {
    policy.setSeniorityStepYears(0);
    assertEquals(LeaveQuotaService.ZERO,
        LeaveQuotaService.seniorityBonus(employee, 2026, policy));
  }

  @Test
  void grant_bonus_savesWithActor() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(policyRepository.findByYear(2026)).thenReturn(Optional.of(policy));
    when(entitlementRepository.save(any(LeaveEntitlement.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    LeaveEntitlementResponse response = service.grant(
        "e-1", 2026, new LeaveGrantRequest("BONUS", new BigDecimal("0.1"), "Xuất sắc"), "hr");

    assertEquals(new BigDecimal("0.1000"), response.days());
    assertEquals("hr", response.grantedBy());
    assertEquals("BONUS", response.source());
  }

  @Test
  void grant_carryover_exceedsMax_throws() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(policyRepository.findByYear(2026)).thenReturn(Optional.of(policy));
    when(entitlementRepository.findByEmployeeIdAndYearOrderBySourceAsc("e-1", 2026))
        .thenReturn(List.of(carryover(new BigDecimal("4.0000"))));

    assertThrows(BusinessRuleException.class, () -> service.grant(
        "e-1", 2026, new LeaveGrantRequest("CARRYOVER", new BigDecimal("2"), null), "hr"));
  }

  @Test
  void grant_autoSource_throws() {
    assertThrows(BusinessRuleException.class, () -> service.grant(
        "e-1", 2026, new LeaveGrantRequest("BASE", new BigDecimal("1"), null), "hr"));
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
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(policyRepository.findByYear(2026)).thenReturn(Optional.of(policy));
    when(entitlementRepository.findByEmployeeIdAndYearAndSource(eq("e-1"), eq(2026), any()))
        .thenReturn(Optional.empty());
    when(positionQuotaRepository.findByPolicyIdOrderByPositionNameAsc(policy.getId()))
        .thenReturn(List.of());
    when(entitlementRepository.findByEmployeeIdAndYearOrderBySourceAsc("e-1", 2026))
        .thenReturn(List.of(
            auto(LeaveSource.BASE, new BigDecimal("12.0000")),
            auto(LeaveSource.SENIORITY, new BigDecimal("5.0000")),
            manual(LeaveSource.BONUS, new BigDecimal("0.5000")),
            manual(LeaveSource.CARRYOVER, new BigDecimal("2.0000")),
            manual(LeaveSource.ADJUSTMENT, new BigDecimal("-1.0000"))));

    LeaveQuotaResponse quota = service.getQuota("e-1", 2026);

    assertEquals(new BigDecimal("18.5000"), quota.totalDays());
    assertEquals(new BigDecimal("18.5000"), quota.remainingDays());
    assertEquals(5, quota.entitlements().size());
  }

  private LeavePositionQuota positionQuota(String name, String days) {
    LeavePositionQuota q = new LeavePositionQuota();
    q.setId(UUID.randomUUID());
    q.setPolicyId(policy.getId());
    q.setPositionName(name);
    q.setBonusDays(new BigDecimal(days));
    return q;
  }

  private LeaveEntitlement auto(LeaveSource source, BigDecimal days) {
    LeaveEntitlement e = new LeaveEntitlement();
    e.setId(UUID.randomUUID());
    e.setEmployeeId("e-1");
    e.setYear(2026);
    e.setSource(source);
    e.setDays(days);
    return e;
  }

  private LeaveEntitlement manual(LeaveSource source, BigDecimal days) {
    return auto(source, days);
  }

  private LeaveEntitlement carryover(BigDecimal days) {
    return auto(LeaveSource.CARRYOVER, days);
  }
}
