package com.hrms.identity.holiday;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.kafka.HolidayEventTypes;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.common.outbox.OutboxPayloadConverter;
import com.hrms.identity.event.HolidayEventPayload;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.holiday.dto.HolidayCreateRequest;
import com.hrms.identity.holiday.dto.HolidayResponse;
import com.hrms.identity.holiday.dto.HolidayUpdateRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD ngày lễ trong 1 lịch. Mỗi thay đổi ghi outbox holiday.changed → attendance
 * projection (bảng holidays local, không join DB chéo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

  private static final String AGGREGATE_TYPE = "Holiday";
  private static final String DEFAULT_CALENDAR_CODE = "VN";

  private final HolidayRepository holidayRepository;
  private final HolidayCalendarRepository calendarRepository;
  private final EmployeeRepository employeeRepository;
  private final OutboxRepository outboxRepository;

  @Transactional(readOnly = true)
  public List<HolidayResponse> search(String calendarCode, Integer year, String q) {
    String code = isBlank(calendarCode) ? null : calendarCode.trim().toUpperCase(Locale.ROOT);
    // Pattern build sẵn trong Java — tránh lower(:param) null → PG lỗi lower(bytea)
    String pattern = isBlank(q) ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
    return holidayRepository.search(code, year, pattern).stream()
        .map(HolidayService::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<HolidayResponse> listByCalendar(UUID calendarId, int year) {
    return holidayRepository.findByCalendar_IdAndYearOrderByDateFromAsc(calendarId, year).stream()
        .map(HolidayService::toResponse)
        .toList();
  }

  /** Lịch của chính mình (fallback calendar mặc định VN) — mọi nhân viên đều đọc được. */
  @Transactional(readOnly = true)
  public List<HolidayResponse> myHolidays(String employeeCode, int year) {
    HolidayCalendar calendar = resolveEmployeeCalendar(employeeCode);
    return holidayRepository.findByCalendar_IdAndYearOrderByDateFromAsc(calendar.getId(), year)
        .stream()
        .map(HolidayService::toResponse)
        .toList();
  }

  /** Ngày lễ đang diễn ra hôm nay (mọi lịch) — FE badge "Hôm nay là ngày lễ". */
  @Transactional(readOnly = true)
  public List<HolidayResponse> todayHolidays() {
    LocalDate today = LocalDate.now();
    return holidayRepository.findByDateBetweenFetchCalendar(today).stream()
        .map(HolidayService::toResponse)
        .toList();
  }

  @Transactional
  public HolidayResponse create(UUID calendarId, HolidayCreateRequest request, String actorId) {
    HolidayCalendar calendar = findCalendar(calendarId);
    HolidayType type = parseType(request.type());
    validateRange(request.dateFrom(), request.dateTo());
    String name = request.name().trim();
    if (holidayRepository.existsByCalendar_IdAndNameAndDateFrom(calendarId, name,
        request.dateFrom())) {
      throw new ConflictException(
          "Đã có ngày lễ cùng tên + ngày bắt đầu trong lịch " + calendar.getCode());
    }
    Holiday holiday = new Holiday();
    holiday.setCalendar(calendar);
    holiday.setName(name);
    holiday.setDateFrom(request.dateFrom());
    holiday.setDateTo(request.dateTo());
    holiday.setYear(request.dateFrom().getYear());
    holiday.setType(type);
    holiday.setHalfDay(Boolean.TRUE.equals(request.halfDay()));
    holiday.setNote(request.note());
    holidayRepository.save(holiday);
    writeOutbox(holiday, "CREATED", actorId);
    return toResponse(holiday);
  }

  @Transactional
  public HolidayResponse update(UUID id, HolidayUpdateRequest patch, String actorId) {
    Holiday holiday = holidayRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Holiday", id));
    UUID calendarId = holiday.getCalendarId();
    String name = patch.name() != null && !patch.name().isBlank()
        ? patch.name().trim() : holiday.getName();
    LocalDate from = patch.dateFrom() != null ? patch.dateFrom() : holiday.getDateFrom();
    LocalDate to = patch.dateTo() != null ? patch.dateTo() : holiday.getDateTo();
    validateRange(from, to);
    if (!name.equals(holiday.getName()) || !from.equals(holiday.getDateFrom())) {
      boolean duplicate = holidayRepository
          .existsByCalendar_IdAndNameAndDateFromAndIdNot(calendarId, name, from, id);
      if (duplicate) {
        throw new ConflictException(
            "Đã có ngày lễ cùng tên + ngày bắt đầu trong lịch này");
      }
    }
    holiday.setName(name);
    holiday.setDateFrom(from);
    holiday.setDateTo(to);
    holiday.setYear(from.getYear());
    if (patch.type() != null && !patch.type().isBlank()) {
      holiday.setType(parseType(patch.type()));
    }
    if (patch.halfDay() != null) {
      holiday.setHalfDay(patch.halfDay());
    }
    if (patch.note() != null) {
      holiday.setNote(patch.note());
    }
    holidayRepository.save(holiday);
    writeOutbox(holiday, "UPDATED", actorId);
    return toResponse(holiday);
  }

  @Transactional
  public void delete(UUID id, String actorId) {
    Holiday holiday = holidayRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Holiday", id));
    holidayRepository.delete(holiday);
    writeOutbox(holiday, "DELETED", actorId);
  }

  private HolidayCalendar resolveEmployeeCalendar(String employeeCode) {
    Employee employee = employeeRepository.findByEmployeeCode(employeeCode).orElse(null);
    if (employee != null && employee.getCalendarId() != null) {
      return calendarRepository.findById(employee.getCalendarId())
          .orElseThrow(() -> new ResourceNotFoundException("HolidayCalendar",
              employee.getCalendarId()));
    }
    return calendarRepository.findByCode(DEFAULT_CALENDAR_CODE)
        .orElseThrow(() -> new BusinessRuleException(
            "Chưa có lịch nghỉ lễ mặc định (code=" + DEFAULT_CALENDAR_CODE + ")"));
  }

  private HolidayCalendar findCalendar(UUID id) {
    return calendarRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("HolidayCalendar", id));
  }

  private static void validateRange(LocalDate from, LocalDate to) {
    if (to.isBefore(from)) {
      throw new BusinessRuleException("Ngày kết thúc phải >= ngày bắt đầu");
    }
    if (from.getYear() != to.getYear()) {
      throw new BusinessRuleException("Ngày lễ phải nằm trong cùng một năm");
    }
  }

  private static HolidayType parseType(String raw) {
    try {
      return HolidayType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new BusinessRuleException("Loại ngày lễ chỉ nhận NATIONAL/COMPANY/OTHER: " + raw);
    }
  }

  private void writeOutbox(Holiday holiday, String action, String actorId) {
    HolidayEventPayload payload = HolidayEventPayload.from(holiday, action, actorId);
    outboxRepository.save(OutboxEvent.of(
        AGGREGATE_TYPE,
        holiday.getId().toString(),
        HolidayEventTypes.CHANGED,
        com.hrms.common.kafka.KafkaTopics.HOLIDAY_V1,
        OutboxPayloadConverter.toJson(payload)));
  }

  static HolidayResponse toResponse(Holiday holiday) {
    return new HolidayResponse(
        holiday.getId(),
        holiday.getCalendarId(),
        holiday.getCalendarCode(),
        holiday.getName(),
        holiday.getDateFrom(),
        holiday.getDateTo(),
        holiday.getYear(),
        holiday.getType().name(),
        holiday.isHalfDay(),
        holiday.getNote());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
