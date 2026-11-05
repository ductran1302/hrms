package com.hrms.attendance.schedule;

import com.hrms.attendance.schedule.dto.ScheduleAssignmentRequest;
import com.hrms.attendance.schedule.dto.ScheduleAssignmentResponse;
import com.hrms.common.security.EmployeeScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gán policy lịch cho nhân viên theo ngày hiệu lực — ghi chỉ ADMIN. */
@Tag(name = "Attendance — Schedule assignments")
@RestController
@RequestMapping("/api/v1/attendance/schedule-assignments")
@RequiredArgsConstructor
public class ScheduleAssignmentController {

  private final ScheduleAssignmentService assignmentService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Lịch sử gán policy theo scope (ngoài scope → 403)")
  public List<ScheduleAssignmentResponse> list(
      @RequestParam(required = false) String employeeId,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    String username = jwt == null ? null : jwt.getClaimAsString("preferred_username");
    return assignmentService.list(employeeId, username,
        EmployeeScope.fromAuthorities(authentication.getAuthorities()));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Gán policy lịch cho nhân viên từ effectiveFrom")
  public ScheduleAssignmentResponse assign(
      @Valid @RequestBody ScheduleAssignmentRequest request, @AuthenticationPrincipal Jwt jwt) {
    return assignmentService.assign(request, actor(jwt));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Gỡ bản gán policy")
  public void remove(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    assignmentService.remove(id, actor(jwt));
  }

  private static String actor(Jwt jwt) {
    return jwt == null ? "system" : jwt.getClaimAsString("preferred_username");
  }
}
