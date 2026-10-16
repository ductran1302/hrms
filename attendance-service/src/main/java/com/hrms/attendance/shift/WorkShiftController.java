package com.hrms.attendance.shift;

import com.hrms.attendance.shift.dto.WorkShiftRequest;
import com.hrms.attendance.shift.dto.WorkShiftResponse;
import com.hrms.attendance.shift.dto.WorkShiftUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Quản lý định nghĩa ca làm việc (cố định / linh hoạt). */
@Tag(name = "Attendance — Work shifts")
@RestController
@RequestMapping("/api/v1/attendance/work-shifts")
@RequiredArgsConstructor
public class WorkShiftController {

  private final WorkShiftService workShiftService;

  @GetMapping
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Danh sách ca làm việc (lọc: từ khóa tên, loại FIXED/FLEXIBLE, active)")
  public List<WorkShiftResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Boolean active) {
    return workShiftService.list(q, type, active);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Tạo ca làm việc (workingTime/breakingTime tính bằng phút)")
  public WorkShiftResponse create(@Valid @RequestBody WorkShiftRequest request) {
    return workShiftService.create(request);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Sửa ca làm việc (null = giữ nguyên, validate trên bản gộp)")
  public WorkShiftResponse update(@PathVariable UUID id, @RequestBody WorkShiftUpdateRequest request) {
    return workShiftService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Xóa ca (422 nếu còn nhân viên đang gán — dùng active=false để ngưng)")
  public void delete(@PathVariable UUID id) {
    workShiftService.delete(id);
  }
}
