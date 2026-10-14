package com.hrms.attendance.holiday;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.attendance.event.ProcessedEvent;
import com.hrms.attendance.event.ProcessedEventRepository;
import com.hrms.common.avro.holiday.HolidayAction;
import com.hrms.common.avro.holiday.HolidayEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

  @Mock
  private HolidayRepository holidayRepository;

  @Mock
  private ProcessedEventRepository processedEventRepository;

  @InjectMocks
  private HolidayService service;

  private HolidayEvent event(HolidayAction action) {
    return HolidayEvent.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setEventType("holiday.changed")
        .setOccurredAt(Instant.now())
        .setAction(action)
        .setHolidayId("h-1")
        .setCalendarId("c-1")
        .setCalendarCode("VN")
        .setName("Tết Dương lịch")
        .setDateFrom(LocalDate.of(2026, 1, 1))
        .setDateTo(LocalDate.of(2026, 1, 1))
        .setYear(2026)
        .setHolidayType("NATIONAL")
        .setIsHalfDay(false)
        .setNote("note")
        .build();
  }

  @Test
  void applyChanged_created_upsertsAndMarksProcessed() {
    HolidayEvent event = event(HolidayAction.CREATED);
    when(processedEventRepository.existsById(event.getEventId())).thenReturn(false);
    when(holidayRepository.findById("h-1")).thenReturn(Optional.empty());

    service.applyChanged(event);

    ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
    verify(holidayRepository).save(captor.capture());
    assertEquals("VN", captor.getValue().getCalendarCode());
    assertEquals(LocalDate.of(2026, 1, 1), captor.getValue().getDateFrom());
    verify(processedEventRepository).save(any(ProcessedEvent.class));
  }

  @Test
  void applyChanged_duplicateEventId_noop() {
    HolidayEvent event = event(HolidayAction.UPDATED);
    when(processedEventRepository.existsById(event.getEventId())).thenReturn(true);

    service.applyChanged(event);

    verify(holidayRepository, never()).save(any());
    verify(processedEventRepository, never()).save(any());
  }

  @Test
  void applyChanged_deleted_removesByHolidayId() {
    HolidayEvent event = event(HolidayAction.DELETED);
    when(processedEventRepository.existsById(event.getEventId())).thenReturn(false);

    service.applyChanged(event);

    verify(holidayRepository).deleteById("h-1");
    verify(processedEventRepository).save(any(ProcessedEvent.class));
  }
}
