package com.hrms.attendance.employee;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đọc projection nhân sự (phục vụ trang chấm công) — dữ liệu đến từ Kafka, không gọi identity. */
@Tag(name = "Attendance — Employees")
@RestController
@RequestMapping("/api/v1/attendance/employees")
@RequiredArgsConstructor
public class EmployeeController {

  private final EmployeeService employeeService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Danh sách nhân sự trong attendance (DEPT_LEAD: scope phòng mình quản lý)")
  public List<EmployeeResponse> list(@RequestParam(required = false) String status,
      @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    EmployeeScope scope = EmployeeScope.fromAuthorities(authentication.getAuthorities());
    return employeeService.list(status, scope, usernameOf(jwt)).stream()
        .map(EmployeeResponse::from)
        .toList();
  }

  @GetMapping("/{employeeCode}")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Chi tiết nhân sự theo mã nhân viên (DEPT_LEAD: ngoài scope → 403)")
  public EmployeeResponse getByCode(@PathVariable String employeeCode,
      @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    EmployeeScope scope = EmployeeScope.fromAuthorities(authentication.getAuthorities());
    return EmployeeResponse.from(
        employeeService.getByCode(employeeCode, scope, usernameOf(jwt)));
  }

  private static String usernameOf(Jwt jwt) {
    return jwt == null ? null : jwt.getClaimAsString("preferred_username");
  }
}
