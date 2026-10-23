package com.hrms.identity.keycloak;

import com.hrms.common.web.PageResponse;
import com.hrms.identity.keycloak.dto.KeycloakSyncTaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vận hành P0-6: ADMIN xem hàng đợi đồng bộ Keycloak và retry task FAILED.
 *
 * <p>Task FAILED = đã thử 5 lần vẫn lỗi (Keycloak down/misconfig) → tài khoản có thể chưa được
 * tạo. Không có API này thì HR không biết user nào chưa provisioned.
 */
@Tag(name = "Keycloak Sync Tasks")
@RestController
@RequestMapping("/api/v1/accounts/sync-tasks")
@RequiredArgsConstructor
public class KeycloakSyncTaskController {

  private final KeycloakSyncTaskService taskService;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Danh sách task đồng bộ Keycloak (lọc status?, size tối đa 100)")
  public PageResponse<KeycloakSyncTaskResponse> search(
      @RequestParam(required = false) KeycloakSyncStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return taskService.search(status, page, size);
  }

  @PostMapping("/{id}/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Retry task FAILED (reset PENDING, xử lý lại ngay sau commit)")
  public KeycloakSyncTaskResponse retry(@PathVariable UUID id) {
    return taskService.retry(id);
  }
}
