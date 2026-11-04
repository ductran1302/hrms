package com.hrms.attendance.ot;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.security.Roles;
import com.hrms.common.security.EmployeeScope;
import com.hrms.common.audit.AuditService;
import com.hrms.common.web.PageResponse;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Service quản lý cấu hình OT & lịch sử.
 * - GET: mọi role đã đăng nhập.
 * - PUT: chỉ ADMIN/GLOBAL_LEAD.
 *
 * <p>Lưu ý: update đi qua JPA entity (KHÔNG native upsert) để {@code @Version}
 * hoạt động — native ghi đè version=0 làm mất optimistic locking.
 */
@Service
@RequiredArgsConstructor
public class OTConfigService {

  public static final String ACTION = "OT_CONFIG";
  public static final String TARGET_TYPE = "OTConfig";

  private final OTConfigRepository otConfigRepository;
  private final OTConfigHistoryRepository otConfigHistoryRepository;
  private final AuditService auditService;

  /**
   * Lấy config ACTIVE hiện tại. Nếu chưa có, trả về default (không tạo row).
   * @param scope phạm vi người gọi (dùng để audit, GET mở cho mọi role).
   */
  @Transactional(readOnly = true)
  public OTConfigDTO getActive(EmployeeScope scope) {
    OTConfig config = otConfigRepository.findActive().orElseGet(() -> createDefault());
    auditService.success(ACTION + "_VIEW", TARGET_TYPE, config.getId(),
        "actorScope=" + scope);
    return OTConfigDTO.from(config);
  }

  /**
   * Lấy lịch sử thay đổi (Tab 2). Admin/Global xem tất cả; các role khác xem được.
   */
  @Transactional(readOnly = true)
  public PageResponse<OTConfigHistoryDTO> history(int page, int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
        Sort.by("performedAt").descending());
    Page<OTConfigHistory> pageResult = otConfigHistoryRepository.findAllOrdered(pageable);
    List<OTConfigHistoryDTO> dtos = pageResult.stream()
        .map(OTConfigHistoryDTO::from)
        .toList();
    return PageResponse.of(dtos, pageResult.getNumber(), pageResult.getSize(),
        pageResult.getTotalElements());
  }

  /**
   * Cập nhật config (ADMIN/GLOBAL_LEAD). Row chưa có → persist (id/version sinh bởi JPA);
   * row đã có → dirty check + optimistic lock qua {@code @Version}.
   * @param request dữ liệu mới.
   * @return config sau khi cập nhật.
   */
  @Transactional
  public OTConfigDTO update(OTConfigRequest request) {
    assertAdminOrGlobalLead();

    validateTimeBounds(request.getDayStartTime(), request.getNightStartTime());

    OTConfig config = otConfigRepository.findActive().orElseGet(() -> {
      OTConfig fresh = new OTConfig();
      fresh.setStatus("ACTIVE");
      return fresh;
    });

    // Snapshot trước khi thay đổi để ghi audit
    OTConfig oldConfig = copyOf(config);

    config.setDayStartTime(request.getDayStartTime());
    config.setNightStartTime(request.getNightStartTime());
    config.setPostShiftBufferMinutes(request.getPostShiftBufferMinutes());
    config.setPreShiftBufferMinutes(request.getPreShiftBufferMinutes());
    config.setBreakDurationMinutes(request.getBreakDurationMinutes());
    config.setBreakDeductionThresholdMinutes(
        request.getBreakDeductionThresholdMinutes() != null
            ? request.getBreakDeductionThresholdMinutes()
            : 240);
    config.setRateWeekday(request.getRateWeekday());
    config.setRateWeekdayNight(request.getRateWeekdayNight());
    config.setRateWeekend(request.getRateWeekend());
    config.setRateWeekendNight(request.getRateWeekendNight());
    config.setRateHoliday(request.getRateHoliday());
    config.setRateHolidayNight(request.getRateHolidayNight());

    // JPA save: managed → flush UPDATE ... WHERE version=?; mới → persist
    // saveAndFlush: version tăng ngay trước khi build response (không trả version cũ)
    otConfigRepository.saveAndFlush(config);

    // Ghi lịch sử thay đổi (chỉ khi thực sự có field đổi — tránh history rác)
    String changesJson = diffJson(oldConfig, config);
    if (!"{}".equals(changesJson)) {
      OTConfigHistory history = new OTConfigHistory();
      history.setConfigId(config.getId());
      history.setAction("UPDATE");
      history.setChangesJson(changesJson);
      history.setPerformedBy(currentUsername());
      history.setPerformedAt(java.time.LocalDateTime.now());
      history.setDayStartTime(config.getDayStartTime().toString());
      history.setNightStartTime(config.getNightStartTime().toString());
      history.setPostShiftBufferMinutes(config.getPostShiftBufferMinutes());
      history.setPreShiftBufferMinutes(config.getPreShiftBufferMinutes());
      history.setBreakDurationMinutes(config.getBreakDurationMinutes());
      history.setBreakDeductionThresholdMinutes(config.getBreakDeductionThresholdMinutes());
      history.setRateWeekday(config.getRateWeekday());
      history.setRateWeekdayNight(config.getRateWeekdayNight());
      history.setRateWeekend(config.getRateWeekend());
      history.setRateWeekendNight(config.getRateWeekendNight());
      history.setRateHoliday(config.getRateHoliday());
      history.setRateHolidayNight(config.getRateHolidayNight());
      otConfigHistoryRepository.save(history);
    }

    auditService.success(ACTION + "_UPDATE", TARGET_TYPE, config.getId(),
        "actor=" + currentUsername() + ", changes=" + changesJson);

    return OTConfigDTO.from(config);
  }

  private OTConfig createDefault() {
    OTConfig config = new OTConfig();
    config.setId(UUID.randomUUID());
    config.setStatus("ACTIVE");
    config.setVersion(0L);
    return config;
  }

  private static OTConfig copyOf(OTConfig src) {
    OTConfig copy = new OTConfig();
    copy.setId(src.getId());
    copy.setDayStartTime(src.getDayStartTime());
    copy.setNightStartTime(src.getNightStartTime());
    copy.setPostShiftBufferMinutes(src.getPostShiftBufferMinutes());
    copy.setPreShiftBufferMinutes(src.getPreShiftBufferMinutes());
    copy.setBreakDurationMinutes(src.getBreakDurationMinutes());
    copy.setBreakDeductionThresholdMinutes(src.getBreakDeductionThresholdMinutes());
    copy.setRateWeekday(src.getRateWeekday());
    copy.setRateWeekdayNight(src.getRateWeekdayNight());
    copy.setRateWeekend(src.getRateWeekend());
    copy.setRateWeekendNight(src.getRateWeekendNight());
    copy.setRateHoliday(src.getRateHoliday());
    copy.setRateHolidayNight(src.getRateHolidayNight());
    copy.setStatus(src.getStatus());
    copy.setCreatedAt(src.getCreatedAt());
    copy.setUpdatedAt(src.getUpdatedAt());
    copy.setVersion(src.getVersion());
    return copy;
  }

  private static void validateTimeBounds(LocalTime dayStart, LocalTime nightStart) {
    if (dayStart == null || nightStart == null) {
      throw new BusinessRuleException("dayStartTime và nightStartTime không được để trống");
    }
    if (!dayStart.isBefore(nightStart)) {
      throw new BusinessRuleException(
          "dayStartTime (" + dayStart + ") phải nhỏ hơn nightStartTime (" + nightStart + ")");
    }
  }

  /** JSON hợp lệ: {"field":{"old":"..","new":".."},...} — BigDecimal so sánh theo compareTo. */
  private static String diffJson(OTConfig old, OTConfig newCfg) {
    StringBuilder sb = new StringBuilder();
    appendDiff(sb, "dayStartTime", old.getDayStartTime(), newCfg.getDayStartTime());
    appendDiff(sb, "nightStartTime", old.getNightStartTime(), newCfg.getNightStartTime());
    appendDiff(sb, "postShiftBufferMinutes", old.getPostShiftBufferMinutes(), newCfg.getPostShiftBufferMinutes());
    appendDiff(sb, "preShiftBufferMinutes", old.getPreShiftBufferMinutes(), newCfg.getPreShiftBufferMinutes());
    appendDiff(sb, "breakDurationMinutes", old.getBreakDurationMinutes(), newCfg.getBreakDurationMinutes());
    appendDiff(sb, "breakDeductionThresholdMinutes", old.getBreakDeductionThresholdMinutes(),
        newCfg.getBreakDeductionThresholdMinutes());
    appendDiff(sb, "rateWeekday", old.getRateWeekday(), newCfg.getRateWeekday());
    appendDiff(sb, "rateWeekdayNight", old.getRateWeekdayNight(), newCfg.getRateWeekdayNight());
    appendDiff(sb, "rateWeekend", old.getRateWeekend(), newCfg.getRateWeekend());
    appendDiff(sb, "rateWeekendNight", old.getRateWeekendNight(), newCfg.getRateWeekendNight());
    appendDiff(sb, "rateHoliday", old.getRateHoliday(), newCfg.getRateHoliday());
    appendDiff(sb, "rateHolidayNight", old.getRateHolidayNight(), newCfg.getRateHolidayNight());
    if (sb.isEmpty()) {
      return "{}";
    }
    return "{" + sb.substring(1) + "}";
  }

  private static void appendDiff(StringBuilder sb, String field, Object oldVal, Object newVal) {
    if (sameValue(oldVal, newVal)) {
      return;
    }
    sb.append(",\"").append(field).append("\":{\"old\":\"").append(oldVal)
        .append("\",\"new\":\"").append(newVal).append("\"}");
  }

  /** BigDecimal.equals so scale (1.50 ≠ 1.5) → dùng compareTo; null an toàn. */
  private static boolean sameValue(Object a, Object b) {
    if (a == null) {
      return b == null;
    }
    if (b == null) {
      return false;
    }
    if (a instanceof BigDecimal ba && b instanceof BigDecimal bb) {
      return ba.compareTo(bb) == 0;
    }
    return a.equals(b);
  }

  private static String currentUsername() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()
        || auth instanceof AnonymousAuthenticationToken) {
      return "system";
    }
    if (auth.getPrincipal() instanceof Jwt jwt) {
      String username = jwt.getClaimAsString("preferred_username");
      if (username != null && !username.isBlank()) {
        return username;
      }
      return jwt.getSubject() != null ? jwt.getSubject() : "system";
    }
    String name = auth.getName();
    return name != null && !name.isBlank() ? name : "system";
  }

  private void assertAdminOrGlobalLead() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getAuthorities().stream()
        .noneMatch(a -> a.getAuthority().equals("ROLE_" + Roles.ADMIN)
            || a.getAuthority().equals("ROLE_" + Roles.GLOBAL_LEAD))) {
      throw new AccessDeniedException("Chỉ ADMIN hoặc GLOBAL_LEAD có quyền cập nhật cấu hình OT");
    }
  }
}
