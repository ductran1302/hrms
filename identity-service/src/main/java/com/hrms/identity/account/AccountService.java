package com.hrms.identity.account;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.kafka.EmployeeEventTypes;
import com.hrms.common.kafka.KafkaTopics;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxPayloadConverter;
import com.hrms.common.security.Roles;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.account.dto.AccountResponse;
import com.hrms.identity.account.dto.ChangePasswordRequest;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.event.EmployeeEventPayload;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import com.hrms.identity.keycloak.KeycloakAdminClient.AccountSnapshot;
import com.hrms.common.outbox.OutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Quản lý tài khoản đăng nhập (Keycloak) cho màn Cài đặt:
 * tìm kiếm tài khoản, tự đổi mật khẩu (xác thực mật khẩu cũ), admin đặt lại mật khẩu
 * và bật/tắt tài khoản. Không có bảng riêng — dữ liệu sống trong Keycloak,
 * audit (createdBy/updatedBy/updatedAt) lưu ở user attributes.
 *
 * <p>Đổi role → emit {@code EmployeeUpdatedEvent} kèm {@code roles} để attendance mirror
 * {@code employees.roles} (nguồn tính "Phép theo role" quỹ phép).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

  private static final int MAX_SIZE = 100;
  private static final String AGGREGATE_TYPE = "Employee";

  private final KeycloakAdminClient keycloakAdminClient;
  private final EmployeeRepository employeeRepository;
  private final OutboxRepository outboxRepository;

  /** Danh sách tài khoản phân trang — {@code q} khớp theo username/họ tên/email (KC search). */
  public PageResponse<AccountResponse> search(String q, int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), MAX_SIZE);
    String search = q == null ? "" : q.trim();
    long total = keycloakAdminClient.countUsers(search);
    int from = safePage * safeSize;
    List<AccountResponse> items = from >= total
        ? List.of()
        : keycloakAdminClient.listUsers(search, from, safeSize).stream()
            .map(AccountService::toResponse)
            .toList();
    return PageResponse.of(items, safePage, safeSize, total);
  }

  /** Đổi mật khẩu của chính mình: sai mật khẩu hiện tại → chặn (422), không ghi gì. */
  public void changeOwnPassword(String username, ChangePasswordRequest request) {
    if (!keycloakAdminClient.verifyUserPassword(username, request.currentPassword())) {
      throw new BusinessRuleException("Mật khẩu hiện tại không đúng");
    }
    keycloakAdminClient.resetPassword(username, request.newPassword());
    log.info("User '{}' đã đổi mật khẩu", username);
  }

  /** Admin đặt lại mật khẩu cho tài khoản bất kỳ. */
  public void resetPassword(String username, String newPassword) {
    keycloakAdminClient.resetPassword(username, newPassword);
    log.info("Admin đã đặt lại mật khẩu cho '{}'", username);
  }

  /** Bật/tắt tài khoản — không cho tắt tài khoản của chính mình (tự khóa mình ra ngoài). */
  public void setEnabled(String username, boolean enabled, String actor) {
    if (!enabled && username.equalsIgnoreCase(actor)) {
      throw new BusinessRuleException("Không thể tắt tài khoản của chính mình");
    }
    keycloakAdminClient.setUserEnabled(username, enabled);
  }

  /**
   * Đổi roles của tài khoản (màn Cài đặt → Tài khoản, chỉ ADMIN): validate subset của
   * {@code Roles.all()}, chặn tự hạ role của chính mình (giữ admin cuối khỏi tự khóa),
   * diff add/remove realm-role mappings trong KC.
   */
  public void updateRoles(String username, List<String> roles, String actor) {
    List<String> desired = roles == null ? List.of() : roles.stream()
        .filter(r -> r != null && !r.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
    if (desired.isEmpty()) {
      throw new BusinessRuleException("Phải chọn ít nhất một role");
    }
    for (String role : desired) {
      if (!Roles.all().contains(role)) {
        throw new BusinessRuleException("Role không hợp lệ: " + role);
      }
    }
    if (username.equalsIgnoreCase(actor)) {
      int currentLevel = Roles.maxLevel(keycloakAdminClient.realmRolesByUsername(username));
      if (Roles.maxLevel(desired) < currentLevel) {
        throw new BusinessRuleException("Không thể hạ cấp role của chính mình");
      }
    }
    keycloakAdminClient.updateRealmRoles(username, desired);
    emitRolesEvent(username, desired);
    log.info("Admin '{}' đã cập nhật roles {} cho '{}'", actor, desired, username);
  }

  /**
   * Backfill 1 lần (sau khi deploy feature roles): iterate toàn bộ user KC, emit
   * EmployeeUpdatedEvent kèm roles cho user khớp employee_code → attendance điền
   * cột roles đang null. Idempotent — chạy lại không sao.
   */
  public int resyncRoleEvents() {
    int emitted = 0;
    int first = 0;
    List<AccountSnapshot> batch;
    do {
      batch = keycloakAdminClient.listUsers("", first, MAX_SIZE);
      for (AccountSnapshot snapshot : batch) {
        if (emitRolesEvent(snapshot.username(), snapshot.roles())) {
          emitted++;
        }
      }
      first += MAX_SIZE;
    } while (batch.size() == MAX_SIZE);
    log.info("Resync role events: {} user phát hành event", emitted);
    return emitted;
  }

  /** Ghi outbox EmployeeUpdatedEvent (changedFields=[roles]) — false nếu không có employee. */
  private boolean emitRolesEvent(String username, List<String> roles) {
    Employee employee = employeeRepository.findByEmployeeCode(username).orElse(null);
    if (employee == null) {
      return false; // user không map nhân viên (admin realm, svc...) → bỏ qua
    }
    EmployeeEventPayload payload = EmployeeEventPayload.from(
        employee, null, List.of("roles"), null, null, roles);
    outboxRepository.save(OutboxEvent.of(
        AGGREGATE_TYPE,
        employee.getId().toString(),
        EmployeeEventTypes.UPDATED,
        KafkaTopics.EMPLOYEE_V1,
        OutboxPayloadConverter.toJson(payload)));
    return true;
  }

  private static AccountResponse toResponse(AccountSnapshot snapshot) {
    return new AccountResponse(
        snapshot.username(),
        snapshot.fullName(),
        snapshot.email(),
        snapshot.enabled(),
        snapshot.roles(),
        snapshot.createdTimestamp(),
        snapshot.createdBy(),
        snapshot.updatedBy(),
        snapshot.updatedAt());
  }
}
