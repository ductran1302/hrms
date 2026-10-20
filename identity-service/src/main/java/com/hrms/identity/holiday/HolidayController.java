package com.hrms.identity.holiday;

import com.hrms.identity.holiday.dto.HolidayCreateRequest;
import com.hrms.identity.holiday.dto.HolidayResponse;
import com.hrms.identity.holiday.dto.HolidayUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

@Tag(name = "Holidays")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HolidayController {

  private final HolidayService holidayService;

  @GetMapping("/holiday-calendars/{calendarId}/holidays")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Ngày lễ theo lịch + năm")
  public List<HolidayResponse> listByCalendar(@PathVariable UUID calendarId,
      @RequestParam int year) {
    return holidayService.listByCalendar(calendarId, year);
  }

  @GetMapping("/holidays")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Tìm ngày lễ (lọc theo calendarCode/year/tên)")
  public List<HolidayResponse> search(
      @RequestParam(required = false) String calendarCode,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) String q) {
    return holidayService.search(calendarCode, year, q);
  }

  @GetMapping("/holidays/my")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER','EMPLOYEE')")
  @Operation(summary = "Ngày lễ của mình trong năm (lịch đã gán, fallback VN)")
  public List<HolidayResponse> myHolidays(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "year", required = false) Integer year) {
    int effectiveYear = year != null ? year : LocalDate.now().getYear();
    // sub = KC user id (UUID) — employeeCode nằm ở preferred_username (= username KC)
    String employeeCode = jwt.getClaimAsString("preferred_username");
    return holidayService.myHolidays(employeeCode, effectiveYear);
  }

  @GetMapping("/holidays/today")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER','EMPLOYEE')")
  @Operation(summary = "Ngày lễ đang diễn ra hôm nay (mọi lịch)")
  public List<HolidayResponse> todayHolidays() {
    return holidayService.todayHolidays();
  }

  @PostMapping("/holiday-calendars/{calendarId}/holidays")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Thêm ngày lễ (phát holiday.changed → attendance)")
  public HolidayResponse create(@PathVariable UUID calendarId,
      @Valid @RequestBody HolidayCreateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return holidayService.create(calendarId, request, jwt.getSubject());
  }

  @PatchMapping("/holidays/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public HolidayResponse update(@PathVariable UUID id,
      @Valid @RequestBody HolidayUpdateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return holidayService.update(id, request, jwt.getSubject());
  }

  @DeleteMapping("/holidays/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    holidayService.delete(id, jwt.getSubject());
  }
}
