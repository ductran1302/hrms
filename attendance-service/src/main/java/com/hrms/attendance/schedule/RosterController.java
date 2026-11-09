package com.hrms.attendance.schedule;

import com.hrms.attendance.schedule.dto.RosterEntryResponse;
import com.hrms.attendance.schedule.dto.RosterOverrideRequest;
import com.hrms.common.security.EmployeeScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lịch làm việc suy ra từ policy tuần: xem theo scope, sửa tay từng ngày (ADMIN). */
@Tag(name = "Attendance — Shift roster")
@RestController
@RequestMapping("/api/v1/attendance/rosters")
@RequiredArgsConstructor
public class RosterController {

  private final RosterQueryService queryService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Lịch làm việc theo scope (mặc định 7 ngày trước → 30 ngày sau, tối đa 62 ngày)")
  public List<RosterEntryResponse> list(
      @RequestParam(required = false) String employeeId,
      @RequestParam(required = false) List<String> employeeIds,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    String username = jwt == null ? null : jwt.getClaimAsString("preferred_username");
    return queryService.list(employeeId, employeeIds, from, to, username,
        EmployeeScope.fromAuthorities(authentication.getAuthorities()));
  }

  @GetMapping("/{employeeId}/{date}")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Lịch 1 ngày của NV (suy ra từ policy tuần, ưu tiên sửa tay)")
  public RosterEntryResponse get(
      @PathVariable String employeeId,
      @PathVariable LocalDate date,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    String username = jwt == null ? null : jwt.getClaimAsString("preferred_username");
    return queryService.get(employeeId, date, username,
        EmployeeScope.fromAuthorities(authentication.getAuthorities()));
  }

  @PutMapping("/{employeeId}/{date}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Sửa tay 1 ngày (shiftId null = nghỉ) — chỉ từ hôm nay")
  public RosterEntryResponse override(
      @PathVariable String employeeId,
      @PathVariable LocalDate date,
      @Valid @RequestBody RosterOverrideRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return queryService.override(employeeId, date, request.shiftId(), request.note(), actor(jwt));
  }

  @DeleteMapping("/{employeeId}/{date}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Bỏ sửa tay → sinh lại theo policy (chỉ từ hôm nay)")
  public RosterEntryResponse clearOverride(
      @PathVariable String employeeId,
      @PathVariable LocalDate date,
      @AuthenticationPrincipal Jwt jwt) {
    return queryService.clearOverride(employeeId, date, actor(jwt));
  }

  private static String actor(Jwt jwt) {
    return jwt == null ? "system" : jwt.getClaimAsString("preferred_username");
  }
}
