package com.hrms.attendance.holiday;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đọc projection ngày lễ (phục vụ chấm công trừ ngày lễ) — dữ liệu đến từ Kafka. */
@Tag(name = "Attendance — Holidays")
@RestController
@RequestMapping("/api/v1/attendance/holidays")
@RequiredArgsConstructor
public class HolidayController {

  private final HolidayService holidayService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER','EMPLOYEE')")
  @Operation(summary = "Danh sách ngày lễ trong attendance (lọc calendarCode/year)")
  public List<HolidayResponse> list(
      @RequestParam(required = false) String calendarCode,
      @RequestParam(required = false) Integer year) {
    return holidayService.list(calendarCode, year).stream().map(HolidayResponse::from).toList();
  }

  @GetMapping("/today")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER','EMPLOYEE')")
  @Operation(summary = "Ngày lễ theo ngày cụ thể + lịch (mặc định hôm nay)")
  public List<HolidayResponse> byDate(
      @RequestParam String calendarCode,
      @RequestParam(required = false) LocalDate date) {
    LocalDate effective = date != null ? date : LocalDate.now();
    return holidayService.byDate(calendarCode, effective).stream()
        .map(HolidayResponse::from)
        .toList();
  }
}
