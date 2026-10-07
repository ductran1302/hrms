package com.hrms.identity.employee;

import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nhân viên — HR-facing. Mọi endpoint yêu cầu JWT hợp lệ;
 * ghi (create/update/terminate) chỉ {@code HR_ADMIN}, đọc thêm {@code MANAGER}.
 */
@Tag(name = "Employees")
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

  private final EmployeeService employeeService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Tạo nhân viên mới")
  public EmployeeResponse create(@Valid @RequestBody EmployeeCreateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return employeeService.create(request, jwt.getSubject());
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Danh sách nhân viên (lọc theo status tùy chọn)")
  public List<EmployeeResponse> list(
      @RequestParam(name = "status", required = false) EmployeeStatus status) {
    return employeeService.list(status);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Chi tiết nhân viên")
  public EmployeeResponse get(@PathVariable UUID id) {
    return employeeService.get(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Cập nhật một phần (null = không đổi)")
  public EmployeeResponse update(@PathVariable UUID id,
      @Valid @RequestBody EmployeeUpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
    return employeeService.update(id, request, jwt.getSubject());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Nghỉ việc (soft delete — phát employee.terminated)")
  public void terminate(@PathVariable UUID id,
      @RequestParam(name = "reason", required = false) String reason,
      @AuthenticationPrincipal Jwt jwt) {
    employeeService.terminate(id, jwt.getSubject(), reason);
  }
}
