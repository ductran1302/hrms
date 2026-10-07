package com.hrms.identity.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.kafka.EmployeeEventTypes;
import com.hrms.common.kafka.KafkaTopics;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxPayloadConverter;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
import com.hrms.identity.event.EmployeeEventPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private OutboxRepository outboxRepository;

  @Mock
  private EmployeeMapper mapper;

  @InjectMocks
  private EmployeeService service;

  private final UUID employeeId = UUID.randomUUID();
  private final String actorId = "admin-user-1";

  @Test
  void create_savesEmployeeAndWritesOutbox() {
    EmployeeCreateRequest request =
        new EmployeeCreateRequest("NV0001", "Nguyễn Văn A", "a@hrms.vn", "Engineering",
            "Backend", null, null);
    Employee entity = new Employee();
    entity.setEmployeeCode("NV0001");
    entity.setFullName("Nguyễn Văn A");

    when(employeeRepository.existsByEmployeeCode("NV0001")).thenReturn(false);
    when(mapper.toEntity(request)).thenReturn(entity);
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
      Employee saved = inv.getArgument(0);
      saved.setId(employeeId);
      return saved;
    });
    when(outboxRepository.save(any(OutboxEvent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.create(request, actorId);

    assertEquals(EmployeeStatus.ACTIVE, entity.getStatus());

    ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(outbox.capture());
    OutboxEvent event = outbox.getValue();
    assertEquals(EmployeeEventTypes.CREATED, event.getEventType());
    assertEquals(KafkaTopics.EMPLOYEE_V1, event.getTopic());
    assertEquals(employeeId.toString(), event.getAggregateId());

    EmployeeEventPayload payload =
        OutboxPayloadConverter.fromJson(event.getPayload(), EmployeeEventPayload.class);
    assertEquals("NV0001", payload.employeeCode());
    assertEquals("ACTIVE", payload.status());
    assertEquals(actorId, payload.actorId());
  }

  @Test
  void create_duplicateCode_throwsConflict() {
    EmployeeCreateRequest request =
        new EmployeeCreateRequest("NV0001", "Nguyễn Văn A", null, null, null, null, null);
    when(employeeRepository.existsByEmployeeCode("NV0001")).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.create(request, actorId));
    verify(employeeRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void list_withStatus_filters() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findByStatusOrderByCreatedAtDesc(EmployeeStatus.ACTIVE))
        .thenReturn(List.of(employee));

    var result = service.list(EmployeeStatus.ACTIVE);

    assertEquals(1, result.size());
    verify(employeeRepository).findByStatusOrderByCreatedAtDesc(EmployeeStatus.ACTIVE);
  }

  @Test
  void get_notFound_throwsResourceNotFound() {
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> service.get(employeeId));
  }

  @Test
  void update_noFields_throwsBusinessRule() {
    EmployeeUpdateRequest patch = new EmployeeUpdateRequest(null, null, null, null, null, null);
    assertThrows(BusinessRuleException.class, () -> service.update(employeeId, patch, actorId));
    verify(employeeRepository, never()).findById(any());
  }

  @Test
  void update_statusTerminatedViaPatch_rejected() {
    EmployeeUpdateRequest patch =
        new EmployeeUpdateRequest(null, null, null, null, null, "TERMINATED");
    assertThrows(BusinessRuleException.class, () -> service.update(employeeId, patch, actorId));
    verify(employeeRepository, never()).findById(any());
  }

  @Test
  void update_changedField_savesAndWritesOutbox() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    employee.setEmail("old@hrms.vn");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    EmployeeUpdateRequest patch =
        new EmployeeUpdateRequest(null, "new@hrms.vn", null, null, null, null);
    service.update(employeeId, patch, actorId);

    assertEquals("new@hrms.vn", employee.getEmail());

    ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(outbox.capture());
    assertEquals(EmployeeEventTypes.UPDATED, outbox.getValue().getEventType());

    EmployeeEventPayload payload =
        OutboxPayloadConverter.fromJson(outbox.getValue().getPayload(),
            EmployeeEventPayload.class);
    assertTrue(payload.changedFields().contains("email"));
  }

  @Test
  void update_nothingChanged_writesNoOutbox() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    employee.setFullName("Nguyễn Văn A");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

    EmployeeUpdateRequest patch = new EmployeeUpdateRequest("Nguyễn Văn A", null, null, null,
        null, null);
    service.update(employeeId, patch, actorId);

    verify(outboxRepository, never()).save(any());
  }

  @Test
  void terminate_setsStatusAndWritesTerminatedEvent() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    service.terminate(employeeId, actorId, "Tự nguyện");

    assertEquals(EmployeeStatus.TERMINATED, employee.getStatus());

    ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(outbox.capture());
    assertEquals(EmployeeEventTypes.TERMINATED, outbox.getValue().getEventType());

    EmployeeEventPayload payload =
        OutboxPayloadConverter.fromJson(outbox.getValue().getPayload(),
            EmployeeEventPayload.class);
    assertNotNull(payload.terminatedAt());
    assertEquals("Tự nguyện", payload.reason());
    assertFalse(payload.changedFields().contains("status"));
  }

  @Test
  void terminate_alreadyTerminated_throwsBusinessRule() {
    Employee employee = employeeWithStatus(EmployeeStatus.TERMINATED);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

    assertThrows(BusinessRuleException.class,
        () -> service.terminate(employeeId, actorId, null));
    verify(outboxRepository, never()).save(any());
  }

  private Employee employeeWithStatus(EmployeeStatus status) {
    Employee employee = new Employee();
    employee.setId(employeeId);
    employee.setEmployeeCode("NV0001");
    employee.setFullName("Nguyễn Văn A");
    employee.setStatus(status);
    return employee;
  }
}
