package com.hrms.attendance.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.attendance.leave.dto.LeavePolicyRequest;
import com.hrms.attendance.leave.dto.LeavePolicyResponse;
import com.hrms.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
class LeavePolicyServiceTest {

  @Mock
  private LeavePolicyRepository policyRepository;

  @Mock
  private LeaveRoleQuotaRepository roleQuotaRepository;

  @Mock
  private LeaveSeniorityTierRepository tierRepository;

  @Mock
  private LeaveEntitlementRepository entitlementRepository;

  @Mock
  private LeaveSettingsService settingsService;

  @InjectMocks
  private LeavePolicyService service;

  private LeaveSettings settings;
  private LocalDate today;

  @BeforeEach
  void setUp() {
    settings = new LeaveSettings();
    settings.setId(UUID.randomUUID());
    settings.setFyStartMonth(1);
    settings.setFyStartDay(1);
    settings.setPeriodStartDay(1);
    settings.setPeriodEndDay(31);
    settings.setSaturdayPolicy(SaturdayPolicy.OFF);
    settings.setMinWorkPercent(new BigDecimal("50"));
    settings.setRequirePolicyApproval(true);
    today = LocalDate.now(ZoneOffset.UTC);
  }

  private LeavePolicy policy(int versionNo, PolicyStatus status,
      LocalDate from, LocalDate to) {
    LeavePolicy p = new LeavePolicy();
    p.setId(UUID.randomUUID());
    p.setYear(2026);
    p.setVersionNo(versionNo);
    p.setStatus(status);
    p.setEffectiveFrom(from);
    p.setEffectiveTo(to);
    p.setBaseDays(new BigDecimal("12"));
    p.setSeniorityStepYears(1);
    p.setSeniorityBonusDays(new BigDecimal("1"));
    p.setSeniorityMaxBonus(new BigDecimal("5"));
    p.setCarryoverMax(new BigDecimal("5"));
    return p;
  }

  private LeavePolicyRequest request(int year, String baseDays, LocalDate effectiveFrom) {
    return new LeavePolicyRequest(
        year, new BigDecimal(baseDays), 1, new BigDecimal("1"), new BigDecimal("5"),
        new BigDecimal("5"), List.of(), effectiveFrom, null, null, null);
  }

  // ---- getInForce ----

  @Test
  void getInForce_fiscalYearEmpty_createsDefaultDraft() {
    when(policyRepository.findByYearAndStatusOrderByVersionNoDesc(2026, PolicyStatus.ACTIVE))
        .thenReturn(List.of());
    when(policyRepository.findByYearOrderByVersionNoDesc(2026)).thenReturn(List.of());
    when(policyRepository.save(any(LeavePolicy.class))).thenAnswer(inv -> inv.getArgument(0));
    when(settingsService.getOrCreateSettings()).thenReturn(settings);

    LeavePolicy created = service.getInForce(2026);

    assertEquals(PolicyStatus.DRAFT, created.getStatus());
    assertEquals(1, created.getVersionNo());
    assertEquals(new BigDecimal("12"), created.getBaseDays());
    assertEquals(LocalDate.of(2026, 1, 1), created.getEffectiveFrom());
    assertEquals(LocalDate.of(2026, 12, 31), created.getEffectiveTo());
  }

  @Test
  void getInForce_activeCoveringToday_returnsIt() {
    LeavePolicy active = policy(1, PolicyStatus.ACTIVE,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findByYearAndStatusOrderByVersionNoDesc(2026, PolicyStatus.ACTIVE))
        .thenReturn(List.of(active));
    when(policyRepository.findByYearOrderByVersionNoDesc(2026)).thenReturn(List.of(active));

    assertEquals(active, service.getInForce(2026));
    verify(policyRepository, never()).save(any(LeavePolicy.class));
  }

  @Test
  void getInForce_activePastEffectiveTo_lazyExpiresAndFallsBackToDraft() {
    LeavePolicy expired = policy(1, PolicyStatus.ACTIVE,
        LocalDate.of(2026, 1, 1), today.minusDays(20));
    LeavePolicy draftV2 = policy(2, PolicyStatus.DRAFT,
        today.minusDays(5), LocalDate.of(2026, 12, 31));
    when(policyRepository.findByYearAndStatusOrderByVersionNoDesc(2026, PolicyStatus.ACTIVE))
        .thenReturn(List.of(expired));
    when(policyRepository.findByYearOrderByVersionNoDesc(2026))
        .thenReturn(List.of(draftV2, expired));
    when(policyRepository.save(any(LeavePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

    LeavePolicy result = service.getInForce(2026);

    assertEquals(PolicyStatus.EXPIRED, expired.getStatus(), "ACTIVE quá hạn → lazy EXPIRED");
    assertEquals(draftV2, result, "không còn ACTIVE → fallback DRAFT mới nhất");
  }

  // ---- Tạo / sửa bản nháp ----

  @Test
  void createVersion_secondVersion_effectiveFromAfterExisting() {
    LeavePolicy existing = policy(1, PolicyStatus.ACTIVE,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(policyRepository.findMaxVersionNo(2026)).thenReturn(Optional.of(1));
    when(policyRepository.findByYearOrderByVersionNoDesc(2026)).thenReturn(List.of(existing));
    when(policyRepository.save(any(LeavePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

    LeavePolicyResponse response = service.createVersion(
        request(2026, "14", today), "hr");

    assertEquals(2, response.versionNo());
    assertEquals(PolicyStatus.DRAFT, response.status());
    assertEquals(today, response.effectiveFrom());
    assertEquals(LocalDate.of(2026, 12, 31), response.effectiveTo());
    assertEquals("hr", response.createdBy());
    assertEquals(0, new BigDecimal("14").compareTo(response.baseDays()),
        "baseDays scale-4: " + response.baseDays());
  }

  @Test
  void createVersion_invalidRole_throws422() {
    LeavePolicyRequest bad = new LeavePolicyRequest(
        2026, new BigDecimal("12"), 1, new BigDecimal("1"), new BigDecimal("5"),
        new BigDecimal("5"),
        List.of(new LeavePolicyRequest.RoleQuota("SUPER_ADMIN", new BigDecimal("2"))),
        null, null, null, null);

    assertThrows(BusinessRuleException.class, () -> service.createVersion(bad, "hr"));
  }

  @Test
  void createVersion_vnTiersNull_seedsLawTiers() {
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(policyRepository.findMaxVersionNo(2026)).thenReturn(Optional.empty());
    when(policyRepository.findByYearOrderByVersionNoDesc(2026)).thenReturn(List.of());
    when(policyRepository.save(any(LeavePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

    LeavePolicyResponse response = service.createVersion(request(2026, "12", today), "hr");

    assertEquals(SeniorityRuleType.VN_TIERED, response.seniorityRule(),
        "rule mặc định = bậc luật VN");
    ArgumentCaptor<LeaveSeniorityTier> captor = ArgumentCaptor.forClass(LeaveSeniorityTier.class);
    verify(tierRepository, org.mockito.Mockito.times(5)).save(captor.capture());
    assertEquals(
        List.of(0, 5, 10, 15, 20),
        captor.getAllValues().stream().map(LeaveSeniorityTier::getMinYears).toList(),
        "seed bậc luật VN 0/5/10/15/20");
    assertEquals(0, new BigDecimal("2").compareTo(
        captor.getAllValues().stream()
            .filter(t -> t.getMinYears() == 5).findFirst().orElseThrow().getBonusDays()),
        "bậc đủ 5 năm → +2 (tổng 14 với base 12)");
  }

  @Test
  void createVersion_vnEmptyTiersRequest_throws422() {
    LeavePolicyRequest bad = new LeavePolicyRequest(
        2026, new BigDecimal("12"), 1, new BigDecimal("1"), new BigDecimal("5"),
        new BigDecimal("5"), List.of(), null, null,
        SeniorityRuleType.VN_TIERED, List.of());

    assertThrows(BusinessRuleException.class, () -> service.createVersion(bad, "hr"));
  }

  @Test
  void createVersion_duplicateTierMinYears_throws422() {
    LeavePolicyRequest bad = new LeavePolicyRequest(
        2026, new BigDecimal("12"), 1, new BigDecimal("1"), new BigDecimal("5"),
        new BigDecimal("5"), List.of(), null, null,
        SeniorityRuleType.VN_TIERED,
        List.of(
            new LeavePolicyRequest.SeniorityTier(5, new BigDecimal("2")),
            new LeavePolicyRequest.SeniorityTier(5, new BigDecimal("4"))));

    assertThrows(BusinessRuleException.class, () -> service.createVersion(bad, "hr"));
  }

  @Test
  void createVersion_effectiveFromNotAfterExisting_throws422() {
    LeavePolicy existing = policy(1, PolicyStatus.ACTIVE,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(policyRepository.findMaxVersionNo(2026)).thenReturn(Optional.of(1));
    when(policyRepository.findByYearOrderByVersionNoDesc(2026)).thenReturn(List.of(existing));

    assertThrows(BusinessRuleException.class, () -> service.createVersion(
        request(2026, "12", LocalDate.of(2026, 1, 1)), "hr"),
        "effective_from phải sau version hiện có");
  }

  @Test
  void updateDraft_activeVersion_throws422() {
    LeavePolicy active = policy(1, PolicyStatus.ACTIVE,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(active.getId())).thenReturn(Optional.of(active));

    assertThrows(BusinessRuleException.class, () -> service.updateDraft(
        active.getId(), request(2026, "10", null), "hr"),
        "version ACTIVE không ghi đè — tạo version mới");
  }

  @Test
  void updateDraft_draft_updatesValues() {
    LeavePolicy draft = policy(1, PolicyStatus.DRAFT,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

    LeavePolicyResponse response = service.updateDraft(
        draft.getId(), request(2026, "15", null), "hr");

    assertEquals(0, new BigDecimal("15").compareTo(response.baseDays()),
        "baseDays scale-4: " + response.baseDays());
    assertEquals(PolicyStatus.DRAFT, response.status());
  }

  // ---- Workflow ----

  @Test
  void submit_draftToPending() {
    LeavePolicy draft = policy(1, PolicyStatus.DRAFT,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

    LeavePolicyResponse response = service.submit(draft.getId(), "hr");

    assertEquals(PolicyStatus.PENDING_APPROVAL, response.status());
  }

  @Test
  void approve_pendingToApproved_setsAudit() {
    LeavePolicy pending = policy(1, PolicyStatus.PENDING_APPROVAL,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    LeavePolicyResponse response = service.approve(pending.getId(), "manager");

    assertEquals(PolicyStatus.APPROVED, response.status());
    assertEquals("manager", response.approvedBy());
    assertTrue(response.approvedAt() != null);
  }

  @Test
  void approve_fromDraft_throws() {
    LeavePolicy draft = policy(1, PolicyStatus.DRAFT,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

    assertThrows(BusinessRuleException.class, () -> service.approve(draft.getId(), "manager"));
  }

  @Test
  void reject_pendingBackToDraft() {
    LeavePolicy pending = policy(1, PolicyStatus.PENDING_APPROVAL,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertEquals(PolicyStatus.DRAFT, service.reject(pending.getId(), "manager").status());
  }

  @Test
  void activate_approvedToActive_setsAudit() {
    LeavePolicy approved = policy(2, PolicyStatus.APPROVED, today, LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(approved.getId())).thenReturn(Optional.of(approved));
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(policyRepository.findByYearOrderByVersionNoDesc(2026)).thenReturn(List.of(approved));

    LeavePolicyResponse response = service.activate(approved.getId(), "hr");

    assertEquals(PolicyStatus.ACTIVE, response.status());
    assertEquals("hr", response.activatedBy());
    assertTrue(response.activatedAt() != null);
  }

  @Test
  void activate_draftWhenApprovalRequired_throws() {
    LeavePolicy draft = policy(1, PolicyStatus.DRAFT,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
    when(settingsService.getOrCreateSettings()).thenReturn(settings);

    assertThrows(BusinessRuleException.class, () -> service.activate(draft.getId(), "hr"));
  }

  @Test
  void activate_draftWhenApprovalDisabled_activatesDirectly() {
    settings.setRequirePolicyApproval(false);
    LeavePolicy draft = policy(1, PolicyStatus.DRAFT, today, LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(policyRepository.findByYearOrderByVersionNoDesc(2026)).thenReturn(List.of(draft));

    assertEquals(PolicyStatus.ACTIVE, service.activate(draft.getId(), "hr").status());
  }

  @Test
  void activate_secondVersion_expiresPreviousWithDayBefore() {
    LeavePolicy activeV1 = policy(1, PolicyStatus.ACTIVE,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    LeavePolicy approvedV2 = policy(2, PolicyStatus.APPROVED, today, LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(approvedV2.getId())).thenReturn(Optional.of(approvedV2));
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(policyRepository.findByYearOrderByVersionNoDesc(2026))
        .thenReturn(List.of(approvedV2, activeV1));
    when(policyRepository.save(any(LeavePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

    LeavePolicyResponse response = service.activate(approvedV2.getId(), "hr");

    assertEquals(PolicyStatus.EXPIRED, activeV1.getStatus(), "version cũ → EXPIRED");
    assertEquals(today.minusDays(1), activeV1.getEffectiveTo(),
        "effective_to version cũ = ngày hiệu lực mới − 1");
    assertEquals(PolicyStatus.ACTIVE, response.status());
  }

  @Test
  void activate_pastEffectiveFrom_succeedsRetroactive() {
    LeavePolicy activeV1 = policy(1, PolicyStatus.ACTIVE,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    LeavePolicy approvedV2 = policy(2, PolicyStatus.APPROVED,
        today.minusDays(10), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(approvedV2.getId())).thenReturn(Optional.of(approvedV2));
    when(settingsService.getOrCreateSettings()).thenReturn(settings);
    when(policyRepository.findByYearOrderByVersionNoDesc(2026))
        .thenReturn(List.of(approvedV2, activeV1));
    when(policyRepository.save(any(LeavePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

    LeavePolicyResponse response = service.activate(approvedV2.getId(), "hr");

    assertEquals(PolicyStatus.ACTIVE, response.status(), "quá khứ vẫn activate được (retroactive)");
    assertEquals(PolicyStatus.EXPIRED, activeV1.getStatus(), "version cũ → EXPIRED");
    assertEquals(today.minusDays(11), activeV1.getEffectiveTo(),
        "effective_to version cũ = ngày hiệu lực mới − 1 (cũng trong quá khứ)");
    verify(policyRepository, atLeastOnce()).save(any(LeavePolicy.class));
  }

  @Test
  void cancel_pendingToCancelled() {
    LeavePolicy pending = policy(1, PolicyStatus.PENDING_APPROVAL,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertEquals(PolicyStatus.CANCELLED, service.cancel(pending.getId(), "hr").status());
  }

  @Test
  void cancel_draft_throws() {
    LeavePolicy draft = policy(1, PolicyStatus.DRAFT,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

    assertThrows(BusinessRuleException.class, () -> service.cancel(draft.getId(), "hr"));
  }

  // ---- Xóa ----

  @Test
  void deleteDraft_activeVersion_throws422() {
    LeavePolicy active = policy(1, PolicyStatus.ACTIVE,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(active.getId())).thenReturn(Optional.of(active));

    assertThrows(BusinessRuleException.class, () -> service.deleteDraft(active.getId()));
    verify(policyRepository, never()).delete(any(LeavePolicy.class));
  }

  @Test
  void deleteDraft_draft_deletesRowAndQuotas() {
    LeavePolicy draft = policy(3, PolicyStatus.DRAFT,
        today.plusDays(1), LocalDate.of(2026, 12, 31));
    when(policyRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

    service.deleteDraft(draft.getId());

    verify(roleQuotaRepository).deleteByPolicyId(draft.getId());
    verify(tierRepository).deleteByPolicyId(draft.getId());
    verify(policyRepository).delete(draft);
  }

  @Test
  void versionHistory_returnsActiveAndExpiredOnly() {
    LeavePolicy active = policy(1, PolicyStatus.ACTIVE,
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
    when(policyRepository.findByYearAndStatusInOrderByEffectiveFromAsc(
        org.mockito.ArgumentMatchers.eq(2026), any()))
        .thenReturn(List.of(active));

    List<LeavePolicy> history = service.versionHistory(2026);

    assertEquals(1, history.size());
    assertEquals(PolicyStatus.ACTIVE, history.get(0).getStatus());
  }
}
