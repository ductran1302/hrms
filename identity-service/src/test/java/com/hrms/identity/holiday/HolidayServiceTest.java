package com.hrms.identity.holiday;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.kafka.HolidayEventTypes;
import com.hrms.common.kafka.KafkaTopics;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.holiday.dto.HolidayCreateRequest;
import com.hrms.identity.holiday.dto.HolidayResponse;
import com.hrms.identity.holiday.dto.HolidayUpdateRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

  @Mock
  private HolidayRepository holidayRepository;

  @Mock
  private HolidayCalendarRepository calendarRepository;

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private OutboxRepository outboxRepository;

  @InjectMocks
  private HolidayService service;

  private final UUID calendarId = UUID.randomUUID();
  private final String actorId = "admin-1";

  private HolidayCalendar calendar() {
    HolidayCalendar calendar = new HolidayCalendar();
    calendar.setId(calendarId);
    calendar.setCode("VN");
    calendar.setName("Lịch VN");
    calendar.setCountry("VN");
    calendar.setStatus(HolidayCalendarStatus.ACTIVE);
    return calendar;
  }

  @Test
  void create_savesHolidayAndWritesOutbox() {
    HolidayCreateRequest request = new HolidayCreateRequest("Tết Dương lịch",
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), "NATIONAL", false, null);
    when(calendarRepository.findById(calendarId)).thenReturn(Optional.of(calendar()));
    when(holidayRepository.existsByCalendar_IdAndNameAndDateFrom(any(), any(), any()))
        .thenReturn(false);
    when(holidayRepository.save(any(Holiday.class))).thenAnswer(inv -> {
      Holiday saved = inv.getArgument(0);
      saved.setId(UUID.randomUUID());
      return saved;
    });

    HolidayResponse response = service.create(calendarId, request, actorId);

    assertEquals("Tết Dương lịch", response.name());
    assertEquals(2026, response.year());
    assertEquals("VN", response.calendarCode());
    verify(holidayRepository).save(any(Holiday.class));
    verify(outboxRepository).save(argThat((OutboxEvent e) ->
        KafkaTopics.HOLIDAY_V1.equals(e.getTopic())
            && HolidayEventTypes.CHANGED.equals(e.getEventType())));
  }

  @Test
  void create_duplicateNameSameStart_409() {
    HolidayCreateRequest request = new HolidayCreateRequest("Tết",
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), "NATIONAL", false, null);
    when(calendarRepository.findById(calendarId)).thenReturn(Optional.of(calendar()));
    when(holidayRepository.existsByCalendar_IdAndNameAndDateFrom(any(), any(), any()))
        .thenReturn(true);

    assertThrows(ConflictException.class, () -> service.create(calendarId, request, actorId));
  }

  @Test
  void create_crossYearRange_businessRule() {
    HolidayCreateRequest request = new HolidayCreateRequest("Cross",
        LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2), "NATIONAL", false, null);
    when(calendarRepository.findById(calendarId)).thenReturn(Optional.of(calendar()));

    assertThrows(BusinessRuleException.class, () -> service.create(calendarId, request, actorId));
  }

  @Test
  void delete_writesOutboxWithDeletedAction() {
    Holiday holiday = new Holiday();
    holiday.setId(UUID.randomUUID());
    holiday.setCalendar(calendar());
    holiday.setName("Tết");
    holiday.setDateFrom(LocalDate.of(2026, 2, 16));
    holiday.setDateTo(LocalDate.of(2026, 2, 18));
    holiday.setYear(2026);
    holiday.setType(HolidayType.NATIONAL);
    when(holidayRepository.findById(holiday.getId())).thenReturn(Optional.of(holiday));

    service.delete(holiday.getId(), actorId);

    verify(holidayRepository).delete(holiday);
    verify(outboxRepository).save(argThat((OutboxEvent e) ->
        e.getPayload().contains("\"action\":\"DELETED\"")));
  }

  @Test
  void myHolidays_fallbackDefaultVN_whenEmployeeHasNoCalendar() {
    Employee employee = new Employee();
    when(employeeRepository.findByEmployeeCode("hr")).thenReturn(Optional.of(employee));
    when(calendarRepository.findByCode("VN")).thenReturn(Optional.of(calendar()));
    when(holidayRepository.findByCalendar_IdAndYearOrderByDateFromAsc(calendarId, 2026))
        .thenReturn(List.of());

    List<HolidayResponse> result = service.myHolidays("hr", 2026);

    assertEquals(List.of(), result);
  }

  @Test
  void update_notFound_404() {
    when(holidayRepository.findById(any())).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class,
        () -> service.update(UUID.randomUUID(), new HolidayUpdateRequest(null, null, null,
            null, null, null), actorId));
  }
}
