package com.hrms.attendance.shift;

import com.hrms.attendance.shift.dto.ShiftAssignmentRequest;
import com.hrms.attendance.shift.dto.ShiftAssignmentResponse;
import com.hrms.common.security.EmployeeScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

/** Gán ca cho nhân viên theo ngày hiệu lực (lịch sử — mỗi lần gán 1 dòng mới). */
@Tag(name = "Attendance — Shift assignments")
@RestController
@RequestMapping("/api/v1/attendance/shift-assignments")
@RequiredArgsConstructor
public class ShiftAssignmentController {

  private final ShiftAssignmentService shiftAssignmentService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Lịch sử gán ca theo scope (lọc nhân viên / từ khóa mã-tên-phòng; ngoài scope → 403)")
  public List<ShiftAssignmentResponse> list(
      @RequestParam(required = false) String employeeId,
      @RequestParam(required = false) String q,
      @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    String username = jwt == null ? null : jwt.getClaimAsString("preferred_username");
    return shiftAssignmentService.list(employeeId, q, username,
        EmployeeScope.fromAuthorities(authentication.getAuthorities()));
  }

  @GetMapping("/my")
  @Operation(summary = "Ca làm việc của chính mình tại 1 ngày (mặc định hôm nay)")
  public ShiftAssignmentResponse my(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) LocalDate date) {
    String username = jwt.getClaimAsString("preferred_username");
    return shiftAssignmentService.getMy(username, date);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Gán ca cho nhân viên từ ngày effectiveFrom")
  public ShiftAssignmentResponse assign(
      @Valid @RequestBody ShiftAssignmentRequest request, @AuthenticationPrincipal Jwt jwt) {
    return shiftAssignmentService.assign(request, jwt.getClaimAsString("preferred_username"));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Gỡ bản gán (chỉ bản mới nhất của nhân viên)")
  public void remove(@PathVariable UUID id) {
    shiftAssignmentService.remove(id);
  }
}
