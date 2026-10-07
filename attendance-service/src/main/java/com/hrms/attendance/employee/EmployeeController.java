package com.hrms.attendance.employee;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Danh sách nhân sự trong attendance (lọc theo status tùy chọn)")
  public List<EmployeeResponse> list(@RequestParam(required = false) String status) {
    return employeeService.list(status).stream().map(EmployeeResponse::from).toList();
  }

  @GetMapping("/{employeeCode}")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Chi tiết nhân sự theo mã nhân viên")
  public EmployeeResponse getByCode(@PathVariable String employeeCode) {
    return EmployeeResponse.from(employeeService.getByCode(employeeCode));
  }
}
