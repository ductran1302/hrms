package com.hrms.attendance.holiday;

import com.hrms.attendance.event.ProcessedEvent;
import com.hrms.attendance.event.ProcessedEventRepository;
import com.hrms.common.avro.holiday.HolidayAction;
import com.hrms.common.avro.holiday.HolidayEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projection ngày lễ cho attendance. Idempotent: {@code applyChanged} kiểm tra
 * {@code processed_events} trước — replay cùng eventId không ghi thêm gì.
 * DELETED = xóa cứng (holiday không còn — chấm công không cần lịch sử loại trừ này).
 */
@Service
@RequiredArgsConstructor
public class HolidayService {

  private final HolidayRepository holidayRepository;
  private final ProcessedEventRepository processedEventRepository;

  @Transactional
  public void applyChanged(HolidayEvent event) {
    if (isDuplicate(event.getEventId())) {
      return;
    }
    if (event.getAction() == HolidayAction.DELETED) {
      holidayRepository.deleteById(event.getHolidayId().toString());
    } else {
      upsert(event);
    }
    markProcessed(event.getEventId(), event.getEventType());
  }

  private void upsert(HolidayEvent event) {
    Holiday holiday = holidayRepository.findById(event.getHolidayId().toString())
        .orElseGet(Holiday::new);
    holiday.setHolidayId(event.getHolidayId().toString());
    holiday.setCalendarId(event.getCalendarId().toString());
    holiday.setCalendarCode(event.getCalendarCode().toString());
    holiday.setName(event.getName().toString());
    holiday.setDateFrom(event.getDateFrom());
    holiday.setDateTo(event.getDateTo());
    holiday.setYear(event.getYear());
    holiday.setHolidayType(event.getHolidayType().toString());
    holiday.setHalfDay(event.getIsHalfDay());
    holiday.setNote(event.getNote() == null ? null : event.getNote().toString());
    holidayRepository.save(holiday);
  }

  @Transactional(readOnly = true)
  public List<Holiday> list(String calendarCode, Integer year) {
    if (calendarCode == null || calendarCode.isBlank()) {
      return holidayRepository.search(null, year);
    }
    if (year == null) {
      return holidayRepository.search(calendarCode.trim().toUpperCase(), null);
    }
    return holidayRepository
        .findByCalendarCodeAndYearOrderByDateFromAsc(calendarCode.trim().toUpperCase(), year);
  }

  @Transactional(readOnly = true)
  public List<Holiday> byDate(String calendarCode, LocalDate date) {
    return holidayRepository.findByCalendarCodeAndDate(calendarCode, date);
  }

  private boolean isDuplicate(String eventId) {
    return processedEventRepository.existsById(eventId);
  }

  private void markProcessed(String eventId, String eventType) {
    processedEventRepository.save(new ProcessedEvent(eventId, eventType));
  }
}
