package com.hrms.identity.account;

import com.hrms.common.audit.AuditService;
import com.hrms.common.security.Roles;
import com.hrms.identity.account.dto.RoleAuditItem;
import com.hrms.identity.account.dto.RoleAuditResponse;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.employee.RoleAssignmentService;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import com.hrms.identity.keycloak.KeycloakAdminClient.AccountSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Audit role Keycloak hiện có so với role suy ra từ dữ liệu nhân sự hiện tại.
 *
 * <p>Mặc định chỉ báo cáo (dry-run). Khi {@code apply=true}:
 * <ul>
 *   <li>sửa tài khoản không có role HRMS hoặc có đúng 1 role HRMS sai;</li>
 *   <li>giữ nguyên các role ngoài HRMS (ví dụ offline_access);</li>
 *   <li>bỏ qua tài khoản có ADMIN;</li>
 *   <li>đánh dấu REVIEW nếu có nhiều role HRMS để admin quyết định thủ công.</li>
 * </ul>
 * Không tự động sửa các trường hợp mơ hồ để tránh mở quyền ngoài ý muốn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAuditService {

  private static final int BATCH_SIZE = 100;
  private static final Set<String> HRMS_ROLES = Roles.all();

  private final KeycloakAdminClient keycloakAdminClient;
  private final EmployeeRepository employeeRepository;
  private final RoleAssignmentService roleAssignmentService;
  private final AuditService auditService;

  public RoleAuditResponse audit(String q, boolean apply) {
    String search = q == null ? "" : q.trim();
    int first = 0;
    int scanned = 0;
    int mapped = 0;
    int ok = 0;
    int corrected = 0;
    int reviewRequired = 0;
    int skippedAdmin = 0;
    int unmapped = 0;
    int failed = 0;
    List<RoleAuditItem> items = new ArrayList<>();

    List<AccountSnapshot> batch;
    do {
      batch = keycloakAdminClient.listUsers(search, first, BATCH_SIZE);
      for (AccountSnapshot snapshot : batch) {
        scanned++;
        Employee employee = employeeRepository.findByEmployeeCode(snapshot.username())
            .or(() -> employeeRepository.findByEmployeeCodeIgnoreCase(snapshot.username()))
            .orElse(null);
        if (employee == null) {
          unmapped++;
          items.add(item(snapshot, null, List.of(), null, RoleAuditAction.NO_EMPLOYEE,
              "Username không khớp mã nhân viên"));
          continue;
        }
        mapped++;
        String expected = roleAssignmentService.resolveDefaultRole(employee.getId());
        Set<String> current = hrmsRoles(snapshot);
        if (current.size() == 1 && current.contains(expected)) {
          ok++;
          continue;
        }
        if (current.contains(Roles.ADMIN)) {
          skippedAdmin++;
          items.add(item(snapshot, employee, current, expected, RoleAuditAction.SKIP_ADMIN,
              "Tài khoản có ADMIN — không tự động sửa"));
          continue;
        }
        if (current.size() > 1) {
          reviewRequired++;
          items.add(item(snapshot, employee, current, expected, RoleAuditAction.REVIEW,
              "Có nhiều role HRMS — cần review thủ công"));
          continue;
        }

        List<String> desired = keepNonHrmsRoles(snapshot);
        desired.add(expected);
        if (!apply) {
          items.add(item(snapshot, employee, current, expected,
              RoleAuditAction.WOULD_CORRECT, "Sẽ đổi role nếu chạy apply"));
          continue;
        }
        try {
          keycloakAdminClient.updateRealmRoles(snapshot.username(), desired);
          corrected++;
          items.add(item(snapshot, employee, current, expected, RoleAuditAction.CORRECTED,
              "Đã cập nhật role"));
          audit("ACCOUNT_ROLES_RECONCILE", snapshot.username(), true,
              "from=" + current + " to=" + expected);
        } catch (RuntimeException ex) {
          failed++;
          log.warn("Không cập nhật được role cho '{}': {}", snapshot.username(), ex.getMessage());
          items.add(item(snapshot, employee, current, expected, RoleAuditAction.FAILED,
              "Cập nhật Keycloak thất bại: " + ex.getMessage()));
          audit("ACCOUNT_ROLES_RECONCILE", snapshot.username(), false,
              "from=" + current + " to=" + expected + " error=" + ex.getMessage());
        }
      }
      first += BATCH_SIZE;
    } while (batch.size() == BATCH_SIZE);

    if (apply) {
      audit("ACCOUNT_ROLES_AUDIT", null, true,
          "scanned=" + scanned + " mapped=" + mapped + " ok=" + ok + " corrected=" + corrected
              + " review=" + reviewRequired + " skippedAdmin=" + skippedAdmin
              + " unmapped=" + unmapped + " failed=" + failed);
    }
    return new RoleAuditResponse(apply, scanned, mapped, ok, corrected, reviewRequired,
        skippedAdmin, unmapped, failed, List.copyOf(items));
  }

  private static Set<String> hrmsRoles(AccountSnapshot snapshot) {
    Set<String> roles = new LinkedHashSet<>();
    if (snapshot.roles() == null) {
      return roles;
    }
    snapshot.roles().stream().filter(HRMS_ROLES::contains).forEach(roles::add);
    return roles;
  }

  /** Giữ các role ngoài HRMS (offline_access…) để không vô tình gỡ quyền hệ thống. */
  private static List<String> keepNonHrmsRoles(AccountSnapshot snapshot) {
    if (snapshot.roles() == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(snapshot.roles().stream()
        .filter(role -> !HRMS_ROLES.contains(role))
        .toList());
  }

  private static RoleAuditItem item(AccountSnapshot snapshot, Employee employee,
      Collection<String> currentRoles, String expectedRole, RoleAuditAction action, String message) {
    return new RoleAuditItem(
        snapshot.username(),
        employee == null ? null : employee.getEmployeeCode(),
        employee == null ? snapshot.fullName() : employee.getFullName(),
        List.copyOf(currentRoles),
        expectedRole,
        action,
        message);
  }

  private void audit(String action, String target, boolean success, String detail) {
    try {
      if (success) {
        auditService.success(action, "Account", target, detail);
      } else {
        auditService.failure(action, "Account", target, detail);
      }
    } catch (RuntimeException ex) {
      // Keycloak đã thay đổi; không để lỗi ghi audit làm hỏng toàn bộ lần audit.
      log.warn("Không ghi được audit {} cho '{}': {}", action, target, ex.getMessage());
    }
  }
}
