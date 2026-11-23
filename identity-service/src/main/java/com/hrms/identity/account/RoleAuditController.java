package com.hrms.identity.account;

import com.hrms.identity.account.dto.RoleAuditResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit và reconcile role Keycloak hiện có. Chỉ ADMIN.
 *
 * <p>{@code GET} là dry-run; {@code POST /apply} mới ghi thay đổi. Chỉ sửa trường hợp
 * role đơn sai/không có role HRMS; ADMIN và tài khoản nhiều role HRMS được bỏ qua/review.
 */
@Tag(name = "Accounts")
@RestController
@RequestMapping("/api/v1/accounts/role-audit")
@RequiredArgsConstructor
public class RoleAuditController {

  private final RoleAuditService roleAuditService;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Audit role Keycloak hiện có (dry-run, không thay đổi dữ liệu)")
  public RoleAuditResponse audit(@RequestParam(required = false) String q) {
    return roleAuditService.audit(q, false);
  }

  @PostMapping("/apply")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Sửa các role đơn sai/thiếu (giữ ADMIN và role đa role để review)")
  public RoleAuditResponse apply(@RequestParam(required = false) String q) {
    return roleAuditService.audit(q, true);
  }
}
