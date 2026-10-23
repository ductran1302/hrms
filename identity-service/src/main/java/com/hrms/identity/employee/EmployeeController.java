package com.hrms.identity.employee;

import com.hrms.common.security.EmployeeScope;
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
import org.springframework.security.core.Authentication;
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
 * API nhân viên — HR-facing. Mọi endpoint yêu cầu JWT hợp lệ; ghi (create) chỉ
 * {@code ADMIN}; ghi (update/terminate/assignCalendar) {@code ADMIN/GLOBAL_LEAD/DEPT_LEAD}
 * + check scope trong service; đọc thêm {@code MANAGER}.
 *
 * <p>Scoping theo {@link EmployeeScope}: MANAGER/EMPLOYEE đọc trực thuộc + self (mask PII);
 * DEPT_LEAD đọc phòng mình quản lý (gồm nhánh con) + trực thuộc + self, PII đầy đủ;
 * GLOBAL_LEAD/ADMIN toàn bộ. Chi tiết ngoài phạm vi → 403.
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
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Tạo nhân viên mới (chỉ ADMIN — tài khoản được auto-assign role)")
  public EmployeeResponse create(@Valid @RequestBody EmployeeCreateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return employeeService.create(request, jwt.getSubject());
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Danh sách nhân viên phân trang (tìm kiếm + lọc status/phòng ban/chức vụ/văn phòng)")
  public PageResponse<EmployeeResponse> search(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "status", required = false) EmployeeStatus status,
      @RequestParam(name = "departmentId", required = false) UUID departmentId,
      @RequestParam(name = "position", required = false) String position,
      @RequestParam(name = "office", required = false) String office,
      @RequestParam(name = "q", required = false) String q,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    return employeeService.search(page, Math.max(size, 1), status, departmentId, position, office,
        q, viewer(jwt, authentication));
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Hồ sơ nhân viên của chính mình (username = employeeCode, fallback email)")
  public EmployeeResponse me(@AuthenticationPrincipal Jwt jwt) {
    return employeeService.me(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("email"));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Chi tiết nhân viên (scope: MANAGER/DEPT_LEAD — PII được mask nếu cần)")
  public EmployeeResponse get(@PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    return employeeService.get(id, viewer(jwt, authentication));
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Lịch sử thay đổi job info của nhân viên (mới nhất trước)")
  public List<JobHistoryResponse> history(@PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    employeeService.get(id, viewer(jwt, authentication));
    return jobHistoryService.listByEmployee(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD')")
  @Operation(summary = "Cập nhật một phần (null = không đổi; clearDepartment/clearManager = gỡ liên kết)")
  public EmployeeResponse update(@PathVariable UUID id,
      @Valid @RequestBody EmployeeUpdateRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    return employeeService.update(id, request, jwt.getSubject(),
        viewer(jwt, authentication));
  }

  @PatchMapping("/{id}/calendar")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD')")
  @Operation(summary = "Gán/bỏ gán lịch nghỉ lễ (calendarId null = fallback mặc định VN)")
  public EmployeeResponse assignCalendar(@PathVariable UUID id,
      @RequestBody com.hrms.identity.employee.dto.EmployeeCalendarAssignRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    return employeeService.assignCalendar(id, request.calendarId(), jwt.getSubject(),
        viewer(jwt, authentication));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD')")
  @Operation(summary = "Nghỉ việc (soft delete — phát employee.terminated)")
  public void terminate(@PathVariable UUID id,
      @RequestParam(name = "reason", required = false) String reason,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    employeeService.terminate(id, jwt.getSubject(), reason, viewer(jwt, authentication));
  }

  /**
   * Xây viewer cho endpoint đọc/ghi: username/email lấy từ JWT claim, scope suy từ
   * {@code Authentication} authorities (cùng nguồn với {@code @PreAuthorize}).
   */
  private EmployeeViewer viewer(Jwt jwt, Authentication authentication) {
    return EmployeeViewer.of(jwt, authentication);
  }
}
