package com.hrms.identity.account;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.account.dto.AccountResponse;
import com.hrms.identity.account.dto.ChangePasswordRequest;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import com.hrms.identity.keycloak.KeycloakAdminClient.AccountSnapshot;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Quản lý tài khoản đăng nhập (Keycloak) cho màn Cài đặt:
 * tìm kiếm tài khoản, tự đổi mật khẩu (xác thực mật khẩu cũ), admin đặt lại mật khẩu
 * và bật/tắt tài khoản. Không có bảng riêng — dữ liệu sống trong Keycloak,
 * audit (createdBy/updatedBy/updatedAt) lưu ở user attributes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

  private static final int MAX_SIZE = 100;

  private final KeycloakAdminClient keycloakAdminClient;

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
