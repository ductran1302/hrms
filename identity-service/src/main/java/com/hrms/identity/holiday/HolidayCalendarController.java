package com.hrms.identity.holiday;

import com.hrms.identity.holiday.dto.HolidayCalendarCreateRequest;
import com.hrms.identity.holiday.dto.HolidayCalendarResponse;
import com.hrms.identity.holiday.dto.HolidayCalendarUpdateRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Holiday Calendars")
@RestController
@RequestMapping("/api/v1/holiday-calendars")
@RequiredArgsConstructor
public class HolidayCalendarController {

  private final HolidayCalendarService calendarService;

  @GetMapping
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER','EMPLOYEE')")
  @Operation(summary = "Danh sách lịch nghỉ lễ (mọi vai trò — FE cần chọn lịch)")
  public List<HolidayCalendarResponse> list() {
    return calendarService.list();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER','EMPLOYEE')")
  public HolidayCalendarResponse get(@PathVariable UUID id) {
    return calendarService.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Tạo lịch nghỉ lễ (code bất biến, mặc định ACTIVE)")
  public HolidayCalendarResponse create(
      @Valid @RequestBody HolidayCalendarCreateRequest request) {
    return calendarService.create(request);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Cập nhật lịch (code không đổi — projection join theo code)")
  public HolidayCalendarResponse update(@PathVariable UUID id,
      @RequestBody HolidayCalendarUpdateRequest request) {
    return calendarService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Xóa lịch (chỉ khi trống: không ngày lễ, không nhân viên gắn)")
  public void delete(@PathVariable UUID id) {
    calendarService.delete(id);
  }
}
