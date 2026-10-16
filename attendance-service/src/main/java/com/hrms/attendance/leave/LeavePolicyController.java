package com.hrms.attendance.leave;

import com.hrms.attendance.leave.dto.LeavePolicyRequest;
import com.hrms.attendance.leave.dto.LeavePolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chính sách quỹ phép theo năm (base/thâm niên/chức vụ/carryover). */
@Tag(name = "Attendance — Leave policies")
@RestController
@RequestMapping("/api/v1/attendance/leave/policies")
@RequiredArgsConstructor
public class LeavePolicyController {

  private final LeaveQuotaService leaveQuotaService;

  @GetMapping
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Danh sách chính sách quỹ phép")
  public List<LeavePolicyResponse> list() {
    return leaveQuotaService.listPolicies();
  }

  @GetMapping("/{year}")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Chính sách quỹ phép theo năm — tự tạo mặc định nếu chưa có")
  public LeavePolicyResponse get(@PathVariable int year) {
    return leaveQuotaService.getPolicy(year);
  }

  @PostMapping
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Tạo/cập nhật chính sách quỹ phép (upsert theo year)")
  public LeavePolicyResponse save(@RequestBody LeavePolicyRequest request) {
    return leaveQuotaService.savePolicy(request);
  }
}
