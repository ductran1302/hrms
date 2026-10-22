package com.hrms.identity.account;

import com.hrms.common.web.PageResponse;
import com.hrms.identity.account.dto.AccountResponse;
import com.hrms.identity.account.dto.AccountRolesRequest;
import com.hrms.identity.account.dto.ChangePasswordRequest;
import com.hrms.identity.account.dto.ResetPasswordRequest;
import com.hrms.identity.account.dto.SetEnabledRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Danh sách tài khoản đăng nhập (tìm theo username/họ tên/email)")
  public PageResponse<AccountResponse> search(
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return accountService.search(q, page, size);
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER','EMPLOYEE')")
  @Operation(summary = "Đổi mật khẩu của chính mình (xác thực mật khẩu hiện tại trước)")
  public void changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    accountService.changeOwnPassword(actorOf(jwt), request);
  }

  @PatchMapping("/{username}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Admin đặt lại mật khẩu cho tài khoản")
  public void resetPassword(@PathVariable String username,
      @Valid @RequestBody ResetPasswordRequest request) {
    accountService.resetPassword(username, request.newPassword());
  }

  @PatchMapping("/{username}/enabled")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Bật/tắt tài khoản đăng nhập (không cho tắt của chính mình)")
  public void setEnabled(@PathVariable String username,
      @RequestBody SetEnabledRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    accountService.setEnabled(username, request.enabled(), actorOf(jwt));
  }

  @PatchMapping("/{username}/roles")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Đổi roles của tài khoản (chặn tự hạ role của chính mình)")
  public void updateRoles(@PathVariable String username,
      @RequestBody AccountRolesRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    accountService.updateRoles(username, request.roles(), actorOf(jwt));
  }

  @PostMapping("/resync-role-events")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Backfill: phát lại EmployeeUpdatedEvent kèm roles cho toàn bộ user (1 lần sau deploy)")
  public java.util.Map<String, Integer> resyncRoleEvents() {
    return java.util.Map.of("emitted", accountService.resyncRoleEvents());
  }

  private static String actorOf(Jwt jwt) {
    if (jwt == null) {
      return null;
    }
    String preferred = jwt.getClaimAsString("preferred_username");
    return preferred != null && !preferred.isBlank() ? preferred : jwt.getSubject();
  }
}
