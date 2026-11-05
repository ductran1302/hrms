package com.hrms.attendance.schedule;

import com.hrms.attendance.schedule.dto.WorkSchedulePolicyRequest;
import com.hrms.attendance.schedule.dto.WorkSchedulePolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/** Mẫu lịch tuần (policy) — ghi chỉ ADMIN. */
@Tag(name = "Attendance — Schedule policies")
@RestController
@RequestMapping("/api/v1/attendance/schedule-policies")
@RequiredArgsConstructor
public class WorkSchedulePolicyController {

  private final WorkSchedulePolicyService policyService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Danh sách policy lịch (lọc active)")
  public List<WorkSchedulePolicyResponse> list(@RequestParam(required = false) Boolean active) {
    return policyService.list(active);
  }

  @GetMapping("/default")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Policy mặc định (dùng cho NV chưa gán)")
  public WorkSchedulePolicyResponse defaultPolicy() {
    return policyService.getDefault();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Chi tiết policy lịch")
  public WorkSchedulePolicyResponse get(@PathVariable UUID id) {
    return policyService.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Tạo policy lịch (defaultPolicy=true sẽ thay policy mặc định cũ)")
  public WorkSchedulePolicyResponse create(
      @Valid @RequestBody WorkSchedulePolicyRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return policyService.create(request, actor(jwt));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Thay thế toàn bộ policy (field null = nghỉ/không dùng)")
  public WorkSchedulePolicyResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody WorkSchedulePolicyRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return policyService.update(id, request, actor(jwt));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Xoá policy (422 nếu là mặc định hoặc còn assignment/roster)")
  public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    policyService.delete(id, actor(jwt));
  }

  private static String actor(Jwt jwt) {
    return jwt == null ? "system" : jwt.getClaimAsString("preferred_username");
  }
}
