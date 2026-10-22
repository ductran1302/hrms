package com.hrms.attendance.leave;

import com.hrms.attendance.leave.dto.LeavePolicyRequest;
import com.hrms.attendance.leave.dto.LeavePolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phiên bản chính sách quỹ phép (effective-dated, 6 states).
 *
 * <p>Đọc: GET list (?year= history), GET /{year} = in-force. Viết: tạo/sửa bản nháp + workflow
 * submit → approve → activate (ADMIN; approve/reject thêm GLOBAL_LEAD — duyệt cuối chính sách
 * toàn công ty), cancel/delete draft = ADMIN.
 */
@Tag(name = "Attendance — Leave policies")
@RestController
@RequestMapping("/api/v1/attendance/leave/policies")
@RequiredArgsConstructor
public class LeavePolicyController {

  private final LeavePolicyService policyService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Lịch sử phiên bản chính sách (bỏ year = mọi năm, có year = versions năm đó)")
  public List<LeavePolicyResponse> list(@RequestParam(required = false) Integer year) {
    return policyService.listVersions(year);
  }

  @GetMapping("/{year:\\d+}")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Chính sách quỹ phép in-force của năm — tự tạo mặc định nếu chưa có")
  public LeavePolicyResponse getInForce(@PathVariable int year) {
    return policyService.getInForceResponse(year);
  }

  @GetMapping("/versions/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Chi tiết 1 phiên bản")
  public LeavePolicyResponse getVersion(@PathVariable UUID id) {
    return policyService.getVersion(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Tạo phiên bản mới (DRAFT) — versionNo tự tăng; copy roleQuotas từ sourcePolicyId nếu có")
  public LeavePolicyResponse create(
      @RequestBody LeavePolicyRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return policyService.createVersion(request, jwt.getSubject());
  }

  @PutMapping("/versions/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Sửa phiên bản — CHỈ bản nháp DRAFT (422 nếu đã áp dụng)")
  public LeavePolicyResponse update(
      @PathVariable UUID id,
      @RequestBody LeavePolicyRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return policyService.updateDraft(id, request, jwt.getSubject());
  }

  @PostMapping("/versions/{id}/submit")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Gửi phê duyệt: DRAFT → PENDING_APPROVAL")
  public LeavePolicyResponse submit(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return policyService.submit(id, jwt.getSubject());
  }

  @PostMapping("/versions/{id}/approve")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD')")
  @Operation(summary = "Phê duyệt: PENDING_APPROVAL → APPROVED (ADMIN hoặc GLOBAL_LEAD — duyệt cuối)")
  public LeavePolicyResponse approve(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return policyService.approve(id, jwt.getSubject());
  }

  @PostMapping("/versions/{id}/reject")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD')")
  @Operation(summary = "Từ chối: PENDING_APPROVAL → DRAFT (ADMIN hoặc GLOBAL_LEAD)")
  public LeavePolicyResponse reject(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return policyService.reject(id, jwt.getSubject());
  }

  @PostMapping("/versions/{id}/activate")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Kích hoạt: APPROVED (hoặc DRAFT nếu tắt bắt buộc phê duyệt) → ACTIVE; version cũ → EXPIRED")
  public LeavePolicyResponse activate(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return policyService.activate(id, jwt.getSubject());
  }

  @PostMapping("/versions/{id}/cancel")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Hủy: PENDING_APPROVAL/APPROVED → CANCELLED")
  public LeavePolicyResponse cancel(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt) {
    return policyService.cancel(id, jwt.getSubject());
  }

  @DeleteMapping("/versions/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Xóa phiên bản — CHỈ bản nháp DRAFT (422 nếu khác)")
  public void delete(@PathVariable UUID id) {
    policyService.deleteDraft(id);
  }
}
