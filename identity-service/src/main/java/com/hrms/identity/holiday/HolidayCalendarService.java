package com.hrms.identity.holiday;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.holiday.dto.HolidayCalendarCreateRequest;
import com.hrms.identity.holiday.dto.HolidayCalendarResponse;
import com.hrms.identity.holiday.dto.HolidayCalendarUpdateRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD lịch nghỉ lễ. Code bất biến sau tạo (projection attendance join theo code). */
@Service
@RequiredArgsConstructor
public class HolidayCalendarService {

  private final HolidayCalendarRepository calendarRepository;
  private final HolidayRepository holidayRepository;
  private final EmployeeRepository employeeRepository;

  @Transactional(readOnly = true)
  public List<HolidayCalendarResponse> list() {
    return calendarRepository.findAllByOrderByCodeAsc().stream()
        .map(HolidayCalendarService::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public HolidayCalendarResponse get(UUID id) {
    return toResponse(find(id));
  }

  @Transactional
  public HolidayCalendarResponse create(HolidayCalendarCreateRequest request) {
    String code = request.code().trim().toUpperCase(Locale.ROOT);
    if (calendarRepository.existsByCode(code)) {
      throw new ConflictException("Mã lịch nghỉ lễ đã tồn tại: " + code);
    }
    HolidayCalendar calendar = new HolidayCalendar();
    calendar.setCode(code);
    calendar.setName(request.name().trim());
    calendar.setCountry(request.country().trim().toUpperCase(Locale.ROOT));
    calendar.setDescription(request.description());
    calendar.setStatus(HolidayCalendarStatus.ACTIVE);
    calendarRepository.save(calendar);
    return toResponse(calendar);
  }

  @Transactional
  public HolidayCalendarResponse update(UUID id, HolidayCalendarUpdateRequest patch) {
    HolidayCalendar calendar = find(id);
    if (patch.name() != null && !patch.name().isBlank()) {
      calendar.setName(patch.name().trim());
    }
    if (patch.description() != null) {
      calendar.setDescription(patch.description());
    }
    if (patch.status() != null && !patch.status().isBlank()) {
      try {
        calendar.setStatus(HolidayCalendarStatus.valueOf(patch.status().trim().toUpperCase()));
      } catch (IllegalArgumentException ex) {
        throw new BusinessRuleException("Trạng thái lịch không hợp lệ: " + patch.status());
      }
    }
    calendarRepository.save(calendar);
    return toResponse(calendar);
  }

  /** Chỉ xóa được lịch trống (không ngày lễ, không nhân viên gắn) — tránh gãy projection. */
  @Transactional
  public void delete(UUID id) {
    HolidayCalendar calendar = find(id);
    if (holidayRepository.countByCalendar_Id(id) > 0) {
      throw new ConflictException(
          "Lịch còn ngày lễ — xóa ngày lễ trước khi xóa lịch (code=" + calendar.getCode() + ")");
    }
    if (employeeRepository.countByCalendarId(id) > 0) {
      throw new ConflictException(
          "Còn nhân viên gắn lịch này — bỏ gán trước khi xóa (code=" + calendar.getCode() + ")");
    }
    calendarRepository.delete(calendar);
  }

  private HolidayCalendar find(UUID id) {
    return calendarRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("HolidayCalendar", id));
  }

  static HolidayCalendarResponse toResponse(HolidayCalendar calendar) {
    return new HolidayCalendarResponse(
        calendar.getId(),
        calendar.getCode(),
        calendar.getName(),
        calendar.getCountry(),
        calendar.getDescription(),
        calendar.getStatus().name());
  }
}
