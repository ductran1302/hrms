package com.hrms.attendance.leave;

import com.hrms.attendance.leave.dto.LeaveNoteRequest;
import com.hrms.attendance.leave.dto.LeaveSettingsRequest;
import com.hrms.attendance.leave.dto.LeaveSettingsResponse;
import com.hrms.attendance.leave.dto.LeaveWorkDayResponse;
import com.hrms.attendance.leave.dto.LeaveWorkDayUpsertRequest;
import com.hrms.common.security.EmployeeScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tham số global tính quỹ phép + ghi chú NV/năm + ngày công theo kỳ. */
@Tag(name = "Attendance — Leave settings")
@RestController
@RequestMapping("/api/v1/attendance/leave")
@RequiredArgsConstructor
public class LeaveSettingsController {

  private final LeaveSettingsService leaveSettingsService;

  @GetMapping("/settings")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Đọc tham số global (năm TC, kỳ công, T7, ngưỡng %)")
  public LeaveSettingsResponse getSettings() {
    return leaveSettingsService.getSettings();
  }

  @PutMapping("/settings")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Cập nhật tham số global (validate kỳ liên tục)")
  public LeaveSettingsResponse updateSettings(@RequestBody LeaveSettingsRequest request) {
    return leaveSettingsService.updateSettings(request);
  }

  @GetMapping("/quotas/{employeeId}/note")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Ghi chú HR của NV trong năm (ngoài scope → 403)")
  public String getNote(@PathVariable String employeeId, @RequestParam int year,
      @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    String note = leaveSettingsService.getNote(employeeId, year, usernameOf(jwt),
        scopeOf(authentication));
    return note == null ? "" : note;
  }

  @PutMapping("/quotas/{employeeId}/note")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Lưu ghi chú HR của NV trong năm")
  public void saveNote(
      @PathVariable String employeeId,
      @RequestParam int year,
      @RequestBody LeaveNoteRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    leaveSettingsService.saveNote(
        employeeId, year, request, jwt.getClaimAsString("preferred_username"));
  }

  @GetMapping("/quotas/{employeeId}/work-days")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Ngày công theo kỳ trong năm TC (DEFAULT/MANUAL; ngoài scope → 403)")
  public List<LeaveWorkDayResponse> listWorkDays(
      @PathVariable String employeeId, @RequestParam int year,
      @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    return leaveSettingsService.listWorkDays(employeeId, year, usernameOf(jwt),
        scopeOf(authentication));
  }

  @PutMapping("/quotas/{employeeId}/work-days")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Nhập tay ngày công 1 kỳ (set MANUAL — recalc không đè)")
  public LeaveWorkDayResponse upsertWorkDay(
      @PathVariable String employeeId,
      @RequestBody LeaveWorkDayUpsertRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return leaveSettingsService.upsertWorkDay(
        employeeId, request, jwt.getClaimAsString("preferred_username"));
  }

  private static String usernameOf(Jwt jwt) {
    return jwt == null ? null : jwt.getClaimAsString("preferred_username");
  }

  private static EmployeeScope scopeOf(Authentication authentication) {
    return EmployeeScope.fromAuthorities(authentication.getAuthorities());
  }
}
