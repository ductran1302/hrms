package com.hrms.attendance.leave;

import com.hrms.attendance.leave.dto.LeavePolicyRequest;
import com.hrms.attendance.leave.dto.LeavePolicyResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.Roles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phiên bản chính sách quỹ phép theo năm tài chính (effective-dated, 6 states).
 *
 * <p>Vòng đời: DRAFT → PENDING_APPROVAL → APPROVED → ACTIVE → EXPIRED; CANCELLED (hủy trước khi
 * áp dụng). Một năm nhiều version (unique year+versionNo); activate version mới → version ACTIVE
 * cũ tự EXPIRED với {@code effective_to = ngày hiệu lực mới − 1} — version đã dùng để tính quyền
 * lợi không bị ghi đè/xóa. Chỉ DRAFT sửa/xóa được; thay đổi giữa năm = tạo version mới.
 *
 * <p>{@link #getInForce}: ACTIVE hiệu lực tại asOf → lazy expire ACTIVE quá hạn → ACTIVE chờ
 * ngày → DRAFT mới nhất (self-heal FY chưa cấu hình).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeavePolicyService {

  private final LeavePolicyRepository policyRepository;
  private final LeaveRoleQuotaRepository roleQuotaRepository;
  private final LeaveSeniorityTierRepository tierRepository;
  private final LeaveEntitlementRepository entitlementRepository;
  private final LeaveSettingsService settingsService;

  // ---- Đọc / self-heal ----

  /**
   * Đọc version in-force của năm tại {@code asOf} — tự tạo V1 DRAFT mặc định nếu FY chưa có
   * version nào (self-healing).
   *
   * <p>Write tx: lazy expire + self-heal cần commit (self-invocation qua proxy — xem AGENTS §7.9).
   */
  @Transactional
  public LeavePolicy getInForce(int year, LocalDate asOf) {
    lazyExpire(year, asOf);
    List<LeavePolicy> versions = policyRepository.findByYearOrderByVersionNoDesc(year);
    if (versions.isEmpty()) {
      return createDefaultDraft(year);
    }
    // 1) ACTIVE hiệu lực tại asOf.
    for (LeavePolicy p : versions) {
      if (p.getStatus() == PolicyStatus.ACTIVE
          && !p.getEffectiveFrom().isAfter(asOf)
          && !p.getEffectiveTo().isBefore(asOf)) {
        return p;
      }
    }
    // 2) ACTIVE đã activate nhưng chưa đến ngày hiệu lực (chờ áp dụng).
    for (LeavePolicy p : versions) {
      if (p.getStatus() == PolicyStatus.ACTIVE) {
        return p;
      }
    }
    // 3) FY chưa activate (chỉ DRAFT) → dùng DRAFT (mới nhất) để GET luôn có dữ liệu.
    for (LeavePolicy p : versions) {
      if (p.getStatus() == PolicyStatus.DRAFT) {
        return p;
      }
    }
    // 4) Fallback: version mới nhất (EXPIRED/CANCELLED/APPROVED/PENDING).
    return versions.get(0);
  }

  public LeavePolicy getInForce(int year) {
    return getInForce(year, LocalDate.now(ZoneOffset.UTC));
  }

  /** Khi tới ngày hết hiệu lực (kỳ lạ do activate version sau cắt ngắn) → EXPIRED lười. */
  private void lazyExpire(int year, LocalDate asOf) {
    for (LeavePolicy p : policyRepository.findByYearAndStatusOrderByVersionNoDesc(
        year, PolicyStatus.ACTIVE)) {
      if (p.getEffectiveTo().isBefore(asOf)) {
        p.setStatus(PolicyStatus.EXPIRED);
        policyRepository.save(p);
        log.info("Lazy expire chính sách {} v{} (hiệu lực đến {})", year, p.getVersionNo(),
            p.getEffectiveTo());
      }
    }
  }

  @Transactional
  public LeavePolicyResponse getInForceResponse(int year) {
    return toResponse(getInForce(year));
  }

  @Transactional(readOnly = true)
  public List<LeavePolicyResponse> listVersions(Integer year) {
    List<LeavePolicy> versions = year == null
        ? policyRepository.findAllByOrderByYearDescVersionNoDesc()
        : policyRepository.findByYearOrderByVersionNoDesc(year);
    return versions.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public LeavePolicyResponse getVersion(UUID id) {
    return toResponse(require(id));
  }

  /**
   * Lịch sử hiệu lực (ACTIVE|EXPIRED) của năm — resolve theo ngày cho BASE per-period.
   */
  @Transactional(readOnly = true)
  public List<LeavePolicy> versionHistory(int year) {
    return policyRepository.findByYearAndStatusInOrderByEffectiveFromAsc(
        year, EnumSet.of(PolicyStatus.ACTIVE, PolicyStatus.EXPIRED));
  }

  // ---- Tạo / sửa bản nháp ----

  /** Tạo version mới (DRAFT): versionNo = max+1; copy roleQuotas từ sourcePolicyId nếu có. */
  @Transactional
  public LeavePolicyResponse createVersion(LeavePolicyRequest request, String actor) {
    validateValues(request);
    int year = request.year();
    LeaveSettings settings = settingsService.getOrCreateSettings();
    int versionNo = policyRepository.findMaxVersionNo(year).map(v -> v + 1).orElse(1);
    List<LeavePolicy> existing = policyRepository.findByYearOrderByVersionNoDesc(year);

    LocalDate effectiveFrom = request.effectiveFrom() != null
        ? request.effectiveFrom()
        : (existing.isEmpty() ? fyStart(year, settings) : LocalDate.now(ZoneOffset.UTC));
    validateEffectiveFrom(year, effectiveFrom, settings, existing);

    LeavePolicy policy = new LeavePolicy();
    policy.setId(UUID.randomUUID());
    policy.setYear(year);
    policy.setVersionNo(versionNo);
    policy.setStatus(PolicyStatus.DRAFT);
    policy.setEffectiveFrom(effectiveFrom);
    policy.setEffectiveTo(fyEnd(year, settings));
    applyValues(policy, request);
    policy.setSeniorityRule(request.seniorityRule() != null
        ? request.seniorityRule()
        : SeniorityRuleType.VN_TIERED);
    policy.setCreatedBy(actor);
    policyRepository.save(policy);

    // roleQuotas: gửi theo request; nếu null + có source → copy từ version nguồn.
    if (request.roleQuotas() != null) {
      saveRoleQuotas(policy, request.roleQuotas());
    } else if (request.sourcePolicyId() != null) {
      LeavePolicy source = require(request.sourcePolicyId());
      for (LeaveRoleQuota q : roleQuotaRepository.findByPolicyIdOrderByRoleAsc(source.getId())) {
        LeaveRoleQuota copy = new LeaveRoleQuota();
        copy.setPolicyId(policy.getId());
        copy.setRole(q.getRole());
        copy.setBonusDays(q.getBonusDays());
        roleQuotaRepository.save(copy);
      }
    }
    // Bậc thâm niên: gửi theo request; nếu null → copy từ source; VN_TIERED chưa có bậc
    // → seed bậc mặc định theo luật VN (0/5/10/15/20 → +0/2/4/6/8).
    if (request.seniorityTiers() != null) {
      saveSeniorityTiers(policy, request.seniorityTiers());
    } else if (request.sourcePolicyId() != null) {
      LeavePolicy source = require(request.sourcePolicyId());
      List<LeaveSeniorityTier> sourceTiers =
          tierRepository.findByPolicyIdOrderByMinYearsAsc(source.getId());
      for (LeaveSeniorityTier tier : sourceTiers) {
        LeaveSeniorityTier copy = new LeaveSeniorityTier();
        copy.setPolicyId(policy.getId());
        copy.setMinYears(tier.getMinYears());
        copy.setBonusDays(tier.getBonusDays());
        tierRepository.save(copy);
      }
      if (sourceTiers.isEmpty() && policy.getSeniorityRule() == SeniorityRuleType.VN_TIERED) {
        saveSeniorityTiers(policy, defaultVietnamTiers());
      }
    } else if (policy.getSeniorityRule() == SeniorityRuleType.VN_TIERED) {
      saveSeniorityTiers(policy, defaultVietnamTiers());
    }
    log.info("Tạo chính sách phép {} v{} (DRAFT, {}, hiệu lực từ {}) bởi {}", year, versionNo,
        policy.getSeniorityRule(), effectiveFrom, actor);
    return toResponse(policy);
  }

  /** Sửa version — CHỈ DRAFT (version đã áp dụng không ghi đè → tạo version mới). */
  @Transactional
  public LeavePolicyResponse updateDraft(UUID id, LeavePolicyRequest request, String actor) {
    LeavePolicy policy = require(id);
    if (policy.getStatus() != PolicyStatus.DRAFT) {
      throw new BusinessRuleException(
          "Chỉ sửa được bản nháp — phiên bản " + policy.getStatus()
              + " đã áp dụng không bị ghi đè. Hãy tạo phiên bản mới thay đổi.");
    }
    validateValues(request);
    LeaveSettings settings = settingsService.getOrCreateSettings();
    if (request.effectiveFrom() != null && !request.effectiveFrom().equals(policy.getEffectiveFrom())) {
      validateEffectiveFrom(
          policy.getYear(), request.effectiveFrom(), settings,
          policyRepository.findByYearOrderByVersionNoDesc(policy.getYear()));
      policy.setEffectiveFrom(request.effectiveFrom());
    }
    applyValues(policy, request);
    if (request.seniorityRule() != null) {
      policy.setSeniorityRule(request.seniorityRule());
    }
    if (request.roleQuotas() != null) {
      roleQuotaRepository.deleteByPolicyId(policy.getId());
      saveRoleQuotas(policy, request.roleQuotas());
    }
    if (request.seniorityTiers() != null) {
      tierRepository.deleteByPolicyId(policy.getId());
      saveSeniorityTiers(policy, request.seniorityTiers());
    } else if (policy.getSeniorityRule() == SeniorityRuleType.VN_TIERED
        && tierRepository.findByPolicyIdOrderByMinYearsAsc(policy.getId()).isEmpty()) {
      // Đổi sang VN_TIERED mà chưa gửi bậc → seed bậc luật VN (self-heal).
      saveSeniorityTiers(policy, defaultVietnamTiers());
    }
    policyRepository.save(policy);
    log.info("Sửa bản nháp chính sách phép {} v{} bởi {}", policy.getYear(),
        policy.getVersionNo(), actor);
    return toResponse(policy);
  }

  // ---- Workflow ----

  @Transactional
  public LeavePolicyResponse submit(UUID id, String actor) {
    LeavePolicy policy = require(id);
    requireStatus(policy, PolicyStatus.DRAFT, "gửi phê duyệt");
    policy.setStatus(PolicyStatus.PENDING_APPROVAL);
    policyRepository.save(policy);
    log.info("Gửi phê duyệt chính sách {} v{} bởi {}", policy.getYear(), policy.getVersionNo(),
        actor);
    return toResponse(policy);
  }

  @Transactional
  public LeavePolicyResponse approve(UUID id, String actor) {
    LeavePolicy policy = require(id);
    requireStatus(policy, PolicyStatus.PENDING_APPROVAL, "phê duyệt");
    policy.setStatus(PolicyStatus.APPROVED);
    policy.setApprovedBy(actor);
    policy.setApprovedAt(java.time.Instant.now());
    policyRepository.save(policy);
    log.info("Phê duyệt chính sách {} v{} bởi {}", policy.getYear(), policy.getVersionNo(),
        actor);
    return toResponse(policy);
  }

  @Transactional
  public LeavePolicyResponse reject(UUID id, String actor) {
    LeavePolicy policy = require(id);
    requireStatus(policy, PolicyStatus.PENDING_APPROVAL, "từ chối");
    policy.setStatus(PolicyStatus.DRAFT);
    policy.setApprovedBy(null);
    policy.setApprovedAt(null);
    policyRepository.save(policy);
    log.info("Từ chối chính sách {} v{} bởi {}", policy.getYear(), policy.getVersionNo(), actor);
    return toResponse(policy);
  }

  /**
   * Kích hoạt: APPROVED → ACTIVE (hoặc DRAFT nếu tắt bắt buộc phê duyệt). Version ACTIVE cũ
   * trong năm tự EXPIRED với effective_to = ngày hiệu lực mới − 1 (không ghi đè giá trị).
   * Ngày hiệu lực trong quá khứ được phép (áp dụng retroactive) — FE cảnh báo trước khi gọi.
   */
  @Transactional
  public LeavePolicyResponse activate(UUID id, String actor) {
    LeavePolicy policy = require(id);
    boolean requireApproval = settingsService.getOrCreateSettings().isRequirePolicyApproval();
    if (policy.getStatus() == PolicyStatus.DRAFT && !requireApproval) {
      // Bỏ qua phê duyệt khi settings tắt.
    } else if (policy.getStatus() == PolicyStatus.APPROVED) {
      // OK.
    } else if (policy.getStatus() == PolicyStatus.DRAFT) {
      throw new BusinessRuleException(
          "Chính sách chưa phê duyệt — gửi/phê duyệt trước khi kích hoạt"
              + " (hoặc tắt 'Bắt buộc phê duyệt' ở Tab Tham số global)");
    } else {
      throw new BusinessRuleException(
          "Chỉ kích hoạt được bản nháp đã phê duyệt — trạng thái hiện tại: "
              + policy.getStatus());
    }

    LeaveSettings settings = settingsService.getOrCreateSettings();
    List<LeavePolicy> existing = policyRepository.findByYearOrderByVersionNoDesc(policy.getYear());
    for (LeavePolicy other : existing) {
      if (other.getId().equals(policy.getId())
          || other.getStatus() != PolicyStatus.ACTIVE) {
        continue;
      }
      if (!policy.getEffectiveFrom().isAfter(other.getEffectiveFrom())) {
        throw new BusinessRuleException(
            "Phiên bản mới phải có hiệu lực SAU phiên bản đang áp dụng (v"
                + other.getVersionNo() + " từ " + other.getEffectiveFrom() + ")");
      }
      other.setEffectiveTo(policy.getEffectiveFrom().minusDays(1));
      other.setStatus(PolicyStatus.EXPIRED);
      policyRepository.save(other);
    }
    if (policy.getEffectiveFrom().isBefore(fyStart(policy.getYear(), settings))
        || policy.getEffectiveFrom().isAfter(fyEnd(policy.getYear(), settings))) {
      throw new BusinessRuleException(
          "Ngày hiệu lực phải nằm trong năm tài chính "
              + fyStart(policy.getYear(), settings) + " – " + fyEnd(policy.getYear(), settings));
    }
    policy.setStatus(PolicyStatus.ACTIVE);
    policy.setActivatedBy(actor);
    policy.setActivatedAt(java.time.Instant.now());
    policyRepository.save(policy);
    log.info("Kích hoạt chính sách {} v{} (hiệu lực {}–{}) bởi {}", policy.getYear(),
        policy.getVersionNo(), policy.getEffectiveFrom(), policy.getEffectiveTo(), actor);
    return toResponse(policy);
  }

  @Transactional
  public LeavePolicyResponse cancel(UUID id, String actor) {
    LeavePolicy policy = require(id);
    if (policy.getStatus() != PolicyStatus.PENDING_APPROVAL
        && policy.getStatus() != PolicyStatus.APPROVED) {
      throw new BusinessRuleException(
          "Chỉ hủy được bản chờ phê duyệt/đã phê duyệt (DRAFT dùng Xóa, ACTIVE dùng phiên bản mới)"
              + " — trạng thái hiện tại: " + policy.getStatus());
    }
    policy.setStatus(PolicyStatus.CANCELLED);
    policyRepository.save(policy);
    log.info("Hủy chính sách {} v{} bởi {}", policy.getYear(), policy.getVersionNo(), actor);
    return toResponse(policy);
  }

  /** Xóa version — CHỈ DRAFT (chưa từng áp dụng → không có entitlements tham chiếu). */
  @Transactional
  public void deleteDraft(UUID id) {
    LeavePolicy policy = require(id);
    if (policy.getStatus() != PolicyStatus.DRAFT) {
      throw new BusinessRuleException(
          "Chỉ xóa được bản nháp — trạng thái hiện tại: " + policy.getStatus()
              + " (version đã áp dụng không xóa để giữ lịch sử truy vết)");
    }
    roleQuotaRepository.deleteByPolicyId(policy.getId());
    tierRepository.deleteByPolicyId(policy.getId());
    policyRepository.delete(policy);
    log.info("Xóa bản nháp chính sách {} v{}", policy.getYear(), policy.getVersionNo());
  }

  /** Dòng cấp phép năm đó (cho kiểm tra xóa — nếu cần ở nơi khác). */
  public long countEntitlements(int year) {
    return entitlementRepository.countByYear(year);
  }

  // ---- Helpers ----

  /** Self-heal: FY chưa có version nào → tạo V1 DRAFT với giá trị mặc định. */
  private LeavePolicy createDefaultDraft(int year) {
    LeaveSettings settings = settingsService.getOrCreateSettings();
    LeavePolicy policy = new LeavePolicy();
    policy.setId(UUID.randomUUID());
    policy.setYear(year);
    policy.setVersionNo(1);
    policy.setStatus(PolicyStatus.DRAFT);
    policy.setEffectiveFrom(fyStart(year, settings));
    policy.setEffectiveTo(fyEnd(year, settings));
    policy.setBaseDays(new BigDecimal("12"));
    policy.setSeniorityRule(SeniorityRuleType.VN_TIERED);
    policy.setSeniorityStepYears(1);
    policy.setSeniorityBonusDays(new BigDecimal("1"));
    policy.setSeniorityMaxBonus(new BigDecimal("5"));
    policy.setCarryoverMax(new BigDecimal("5"));
    log.info("Tạo leave policy mặc định {} v1 (DRAFT)", year);
    LeavePolicy created = policyRepository.save(policy);
    // Bậc mặc định theo luật VN — base 12 → tổng 12/14/16/18/20 ngày.
    saveSeniorityTiers(created, defaultVietnamTiers());
    return created;
  }

  static LocalDate fyStart(int year, LeaveSettings settings) {
    return LocalDate.of(year, settings.getFyStartMonth(), settings.getFyStartDay());
  }

  static LocalDate fyEnd(int year, LeaveSettings settings) {
    return fyStart(year, settings).plusYears(1).minusDays(1);
  }

  private void validateEffectiveFrom(
      int year, LocalDate effectiveFrom, LeaveSettings settings, List<LeavePolicy> existing) {
    LocalDate start = fyStart(year, settings);
    LocalDate end = fyEnd(year, settings);
    if (effectiveFrom.isBefore(start) || effectiveFrom.isAfter(end)) {
      throw new BusinessRuleException(
          "Ngày hiệu lực phải nằm trong năm tài chính " + start + " – " + end);
    }
    for (LeavePolicy other : existing) {
      if (!effectiveFrom.isAfter(other.getEffectiveFrom())) {
        throw new BusinessRuleException(
            "Ngày hiệu lực phải SAU phiên bản hiện có (v" + other.getVersionNo() + " từ "
                + other.getEffectiveFrom() + ")");
      }
    }
  }

  private void applyValues(LeavePolicy policy, LeavePolicyRequest request) {
    policy.setBaseDays(scale4(request.baseDays()));
    policy.setSeniorityStepYears(request.seniorityStepYears());
    policy.setSeniorityBonusDays(scale4(request.seniorityBonusDays()));
    policy.setSeniorityMaxBonus(scale4(request.seniorityMaxBonus()));
    policy.setCarryoverMax(scale4(request.carryoverMax()));
  }

  private void validateValues(LeavePolicyRequest request) {
    validateDays(request.baseDays(), "baseDays");
    validateDays(request.seniorityBonusDays(), "seniorityBonusDays");
    validateDays(request.seniorityMaxBonus(), "seniorityMaxBonus");
    validateDays(request.carryoverMax(), "carryoverMax");
    if (request.seniorityStepYears() < 0) {
      throw new BusinessRuleException("seniorityStepYears phải ≥ 0");
    }
    if (request.roleQuotas() != null) {
      for (LeavePolicyRequest.RoleQuota q : request.roleQuotas()) {
        if (q.role() == null || q.role().isBlank()) {
          throw new BusinessRuleException("role không được để trống");
        }
        String role = q.role().trim().toUpperCase(Locale.ROOT);
        if (!Roles.all().contains(role)) {
          throw new BusinessRuleException(
              "Role không hợp lệ: " + q.role() + " — chỉ nhận " + Roles.all());
        }
        validateDays(q.bonusDays(), "bonusDays");
        if (q.bonusDays().signum() <= 0) {
          throw new BusinessRuleException("bonusDays của role phải > 0");
        }
      }
    }
    if (request.seniorityTiers() != null) {
      boolean vietnam = request.seniorityRule() == null
          || request.seniorityRule() == SeniorityRuleType.VN_TIERED;
      if (vietnam && request.seniorityTiers().isEmpty()) {
        throw new BusinessRuleException("VN_TIERED cần ít nhất 1 bậc thâm niên (seniorityTiers)");
      }
      java.util.Set<Integer> mins = new java.util.HashSet<>();
      for (LeavePolicyRequest.SeniorityTier tier : request.seniorityTiers()) {
        if (tier.minYears() < 0) {
          throw new BusinessRuleException("minYears bậc thâm niên phải ≥ 0");
        }
        if (!mins.add(tier.minYears())) {
          throw new BusinessRuleException("minYears bậc thâm niên trùng lặp: " + tier.minYears());
        }
        validateDays(tier.bonusDays(), "seniorityTiers.bonusDays");
      }
    }
  }

  private void saveSeniorityTiers(
      LeavePolicy policy, List<LeavePolicyRequest.SeniorityTier> tiers) {
    for (LeavePolicyRequest.SeniorityTier tier : tiers) {
      LeaveSeniorityTier entity = new LeaveSeniorityTier();
      entity.setPolicyId(policy.getId());
      entity.setMinYears(tier.minYears());
      entity.setBonusDays(scale4(tier.bonusDays()));
      tierRepository.save(entity);
    }
  }

  /**
   * Bậc mặc định theo luật VN (NĐ 145/2020): đủ 12 tháng → 12 ngày; đủ 5/10/15/20 năm →
   * 14/16/18/20 ngày — với base 12 thì bonus 0/2/4/6/8.
   */
  static List<LeavePolicyRequest.SeniorityTier> defaultVietnamTiers() {
    return List.of(
        new LeavePolicyRequest.SeniorityTier(0, new BigDecimal("0")),
        new LeavePolicyRequest.SeniorityTier(5, new BigDecimal("2")),
        new LeavePolicyRequest.SeniorityTier(10, new BigDecimal("4")),
        new LeavePolicyRequest.SeniorityTier(15, new BigDecimal("6")),
        new LeavePolicyRequest.SeniorityTier(20, new BigDecimal("8")));
  }

  private void saveRoleQuotas(LeavePolicy policy, List<LeavePolicyRequest.RoleQuota> quotas) {
    for (LeavePolicyRequest.RoleQuota q : quotas) {
      LeaveRoleQuota quota = new LeaveRoleQuota();
      quota.setPolicyId(policy.getId());
      quota.setRole(q.role().trim().toUpperCase(Locale.ROOT));
      quota.setBonusDays(scale4(q.bonusDays()));
      roleQuotaRepository.save(quota);
    }
  }

  private LeavePolicyResponse toResponse(LeavePolicy policy) {
    List<LeavePolicyResponse.RoleQuotaDto> quotas = roleQuotaRepository
        .findByPolicyIdOrderByRoleAsc(policy.getId()).stream()
        .map(q -> new LeavePolicyResponse.RoleQuotaDto(q.getRole(), q.getBonusDays()))
        .toList();
    List<LeavePolicyResponse.SeniorityTierDto> tiers = tierRepository
        .findByPolicyIdOrderByMinYearsAsc(policy.getId()).stream()
        .map(t -> new LeavePolicyResponse.SeniorityTierDto(t.getMinYears(), t.getBonusDays()))
        .toList();
    return LeavePolicyResponse.from(policy, quotas, tiers);
  }

  private LeavePolicy require(UUID id) {
    return policyRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Chính sách phép", id));
  }

  private static void requireStatus(LeavePolicy policy, PolicyStatus expected, String action) {
    if (policy.getStatus() != expected) {
      throw new BusinessRuleException(
          "Không " + action + " được khi trạng thái là " + policy.getStatus()
              + " (cần " + expected + ")");
    }
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
}
