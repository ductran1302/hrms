package com.hrms.attendance.leave;

import com.hrms.attendance.leave.dto.LeaveEntitlementResponse;
import com.hrms.attendance.leave.dto.LeaveGrantRequest;
import com.hrms.attendance.leave.dto.LeaveQuotaResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Xem/cấp quỹ phép (GĐ1 — chưa có đơn nghỉ phép). */
@Tag(name = "Attendance — Leave quotas")
@RestController
@RequestMapping("/api/v1/attendance/leave")
@RequiredArgsConstructor
public class LeaveQuotaController {

  private final LeaveQuotaService leaveQuotaService;

  @GetMapping("/quotas")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Bảng tổng hợp quỹ phép năm (lọc từ khóa: mã/tên/phòng)")
  public List<LeaveQuotaResponse> list(
      @RequestParam int year, @RequestParam(required = false) String q) {
    return leaveQuotaService.listQuotas(year, q);
  }

  @GetMapping("/quotas/{employeeId}")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER','EMPLOYEE')")
  @Operation(summary = "Quỹ phép của 1 nhân viên (tự ensure dòng tính tự động)")
  public LeaveQuotaResponse get(@PathVariable String employeeId, @RequestParam int year) {
    return leaveQuotaService.getQuota(employeeId, year);
  }

  @PostMapping("/quotas/recalculate")
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Tính lại quota tự động (BASE/SENIORITY/POSITION) cho NV ACTIVE")
  public int recalculate(@RequestParam int year) {
    return leaveQuotaService.recalculate(year);
  }

  @PostMapping("/quotas/{employeeId}/entitlements")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Cấp tay BONUS/CARRYOVER/ADJUSTMENT (nguồn auto tính tự động)")
  public LeaveEntitlementResponse grant(
      @PathVariable String employeeId,
      @RequestParam int year,
      @RequestBody LeaveGrantRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    String actor = jwt.getClaimAsString("preferred_username");
    return leaveQuotaService.grant(employeeId, year, request, actor);
  }

  @DeleteMapping("/entitlements/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Xóa dòng cấp tay (không xóa được dòng tự động)")
  public void deleteEntitlement(@PathVariable UUID id) {
    leaveQuotaService.deleteEntitlement(id);
  }
}
