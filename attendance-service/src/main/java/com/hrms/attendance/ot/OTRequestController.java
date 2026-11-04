package com.hrms.attendance.ot;

import com.hrms.common.security.EmployeeScope;
import com.hrms.common.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý OT — tuân thủ matrix role AGENTS §4.14.
 * - GET: mọi role đã đăng nhập (scope tự động lọc theo EmployeeScope).
 * - Tạo/Sửa/Hủy/Xóa: chủ đơn (xóa thêm ADMIN/GLOBAL_LEAD — check trong service).
 * - Approve/Reject: kiểm tra quan hệ duyệt trong service (không dựa role thuần).
 */
@RestController
@RequestMapping("/api/v1/attendance/ot")
@RequiredArgsConstructor
public class OTRequestController {

  private final OTConfigService otConfigService;
  private final OTRequestService otRequestService;

  // ========== CẤU HÌNH OT (Tab 1) ==========

  @GetMapping("/config")
  public OTConfigDTO getConfig(Authentication authentication) {
    EmployeeScope scope = EmployeeScope.fromAuthorities(authentication.getAuthorities());
    return otConfigService.getActive(scope);
  }

  @PutMapping("/config")
  @PreAuthorize("hasAnyRole('ADMIN', 'GLOBAL_LEAD')")
  @ResponseStatus(HttpStatus.OK)
  public OTConfigDTO updateConfig(@Valid @RequestBody OTConfigRequest request) {
    return otConfigService.update(request);
  }

  // ========== LỊCH SỬ CẤU HÌNH (Tab 2) ==========

  @GetMapping("/config/history")
  public PageResponse<OTConfigHistoryDTO> configHistory(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return otConfigService.history(page, size);
  }

  // ========== DANH SÁCH & QUẢN LÝ ĐƠN OT (Tab 3) ==========

  @GetMapping("/requests")
  public PageResponse<OTRequestDTO> listRequests(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @AuthenticationPrincipal Jwt jwt) {
    return otRequestService.list(page, size, status,
        jwt.getClaimAsString("preferred_username"));
  }

  /** Người duyệt trực tiếp (manager) của người gọi — FE hiển thị thẻ approver. */
  @GetMapping("/approver")
  public OTApproverInfo getApprover(@AuthenticationPrincipal Jwt jwt) {
    return otRequestService.findApproverInfo(jwt.getClaimAsString("preferred_username"));
  }

  @PostMapping("/requests")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public OTRequestDTO createRequest(
      @Valid @RequestBody OTRequestCreateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return otRequestService.create(jwt.getClaimAsString("preferred_username"), request);
  }

  @PatchMapping("/requests/{id}/approve")
  @PreAuthorize("hasAnyRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER')")
  public void approveRequest(@PathVariable UUID id,
                             @RequestParam boolean approved,
                             @RequestParam(required = false) String reason,
                             @AuthenticationPrincipal Jwt jwt) {
    otRequestService.approve(id, jwt.getClaimAsString("preferred_username"), approved, reason);
  }

  @PatchMapping("/requests/{id}/cancel")
  @PreAuthorize("isAuthenticated()")
  public void cancelRequest(@PathVariable UUID id,
                            @RequestParam String reason,
                            @AuthenticationPrincipal Jwt jwt) {
    otRequestService.cancel(id, jwt.getClaimAsString("preferred_username"), reason);
  }

  @PutMapping("/requests/{id}")
  @PreAuthorize("isAuthenticated()")
  public OTRequestDTO updateRequest(@PathVariable UUID id,
                                     @Valid @RequestBody OTRequestCreateRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
    return otRequestService.update(id, jwt.getClaimAsString("preferred_username"), request);
  }

  /** Xóa đơn — authz (chủ đơn hoặc ADMIN/GLOBAL_LEAD) + kiểm tra trạng thái trong service. */
  @DeleteMapping("/requests/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  public void deleteRequest(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    otRequestService.delete(id, jwt.getClaimAsString("preferred_username"));
  }
}
