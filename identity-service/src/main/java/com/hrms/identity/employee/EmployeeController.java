package com.hrms.identity.employee;

import com.hrms.common.web.PageResponse;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
import com.hrms.identity.history.JobHistoryService;
import com.hrms.identity.history.dto.JobHistoryResponse;
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
  private final JobHistoryService jobHistoryService;

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
  @Operation(summary = "Danh sách nhân viên phân trang (tìm kiếm + lọc status/phòng ban/chức vụ)")
  public PageResponse<EmployeeResponse> search(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "status", required = false) EmployeeStatus status,
      @RequestParam(name = "departmentId", required = false) UUID departmentId,
      @RequestParam(name = "position", required = false) String position,
      @RequestParam(name = "q", required = false) String q) {
    return employeeService.search(page, Math.max(size, 1), status, departmentId, position, q);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Chi tiết nhân viên")
  public EmployeeResponse get(@PathVariable UUID id) {
    return employeeService.get(id);
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Lịch sử thay đổi job info của nhân viên (mới nhất trước)")
  public List<JobHistoryResponse> history(@PathVariable UUID id) {
    employeeService.get(id);
    return jobHistoryService.listByEmployee(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Cập nhật một phần (null = không đổi; clearDepartment/clearManager = gỡ liên kết)")
  public EmployeeResponse update(@PathVariable UUID id,
      @Valid @RequestBody EmployeeUpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
    return employeeService.update(id, request, jwt.getSubject());
  }

  @PatchMapping("/{id}/calendar")
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Gán/bỏ gán lịch nghỉ lễ (calendarId null = fallback mặc định VN)")
  public EmployeeResponse assignCalendar(@PathVariable UUID id,
      @RequestBody com.hrms.identity.employee.dto.EmployeeCalendarAssignRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return employeeService.assignCalendar(id, request.calendarId(), jwt.getSubject());
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
