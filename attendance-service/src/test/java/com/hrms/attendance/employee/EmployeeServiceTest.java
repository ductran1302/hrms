package com.hrms.attendance.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.attendance.event.ProcessedEvent;
import com.hrms.attendance.event.ProcessedEventRepository;
import com.hrms.attendance.leave.LeaveEmployeeNoteRepository;
import com.hrms.attendance.leave.LeaveEntitlementRepository;
import com.hrms.attendance.leave.LeaveWorkDayRepository;
import com.hrms.attendance.shift.ShiftAssignmentRepository;
import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import com.hrms.common.avro.employee.EmployeeTerminatedEvent;
import com.hrms.common.avro.employee.EmployeeUpdatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private ProcessedEventRepository processedEventRepository;

  @Mock
  private ShiftAssignmentRepository shiftAssignmentRepository;

  @Mock
  private LeaveEntitlementRepository leaveEntitlementRepository;

  @Mock
  private LeaveEmployeeNoteRepository leaveNoteRepository;

  @Mock
  private LeaveWorkDayRepository leaveWorkDayRepository;

  @InjectMocks
  private EmployeeService employeeService;

  private final String employeeId = UUID.randomUUID().toString();
  private final String eventId = UUID.randomUUID().toString();

  @Test
  void applyCreated_insertsEmployeeAndMarksEventProcessed() {
    when(processedEventRepository.existsById(eventId)).thenReturn(false);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

    employeeService.applyCreated(createdEvent());

    verify(employeeRepository).save(any(Employee.class));
    verify(processedEventRepository).save(any(ProcessedEvent.class));
  }

  @Test
  void applyCreated_duplicateEventId_isNoOp() {
    when(processedEventRepository.existsById(eventId)).thenReturn(true);

    employeeService.applyCreated(createdEvent());

    verify(employeeRepository, never()).save(any());
    verify(processedEventRepository, never()).save(any());
  }

  @Test
  void applyCreated_sameCodeNewId_rekeysProjectionAndForeignKeys() {
    String oldId = UUID.randomUUID().toString();
    Employee existing = new Employee();
    existing.setEmployeeId(oldId);
    existing.setEmployeeCode("NV0001");
    existing.setFullName("Nguyễn Văn A");
    existing.setStatus("ACTIVE");

    when(processedEventRepository.existsById(eventId)).thenReturn(false);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());
    when(employeeRepository.findByEmployeeCode("NV0001")).thenReturn(Optional.of(existing));
    when(shiftAssignmentRepository.rekeyEmployee(oldId, employeeId)).thenReturn(2);
    when(leaveEntitlementRepository.rekeyEmployee(oldId, employeeId)).thenReturn(1);
    when(leaveNoteRepository.rekeyEmployee(oldId, employeeId)).thenReturn(0);
    when(leaveWorkDayRepository.rekeyEmployee(oldId, employeeId)).thenReturn(3);
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

    employeeService.applyCreated(createdEvent());

    // Re-key = delete row cũ + insert row mới với id từ event (không đổi @Id managed entity)
    verify(employeeRepository).delete(existing);
    verify(employeeRepository).flush();
    verify(shiftAssignmentRepository).rekeyEmployee(oldId, employeeId);
    verify(leaveEntitlementRepository).rekeyEmployee(oldId, employeeId);
    verify(leaveNoteRepository).rekeyEmployee(oldId, employeeId);
    verify(leaveWorkDayRepository).rekeyEmployee(oldId, employeeId);
    verify(employeeRepository)
        .save(
            argThat(
                e ->
                    employeeId.equals(e.getEmployeeId())
                        && "NV0001".equals(e.getEmployeeCode())
                        && "Nguyễn Văn A".equals(e.getFullName())));
  }

  @Test
  void applyUpdated_existingEmployee_updatesFields() {
    Employee existing = new Employee();
    existing.setEmployeeId(employeeId);
    existing.setEmployeeCode("NV0001");
    existing.setStatus("ACTIVE");

    when(processedEventRepository.existsById(eventId)).thenReturn(false);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(existing));

    EmployeeUpdatedEvent event = EmployeeUpdatedEvent.newBuilder()
        .setEventId(eventId)
        .setEventType("employee.updated")
        .setOccurredAt(Instant.now())
        .setEmployeeId(employeeId)
        .setEmployeeCode("NV0001")
        .setFullName("Nguyễn Văn A Updated")
        .setStatus("ACTIVE")
        .setChangedFields(List.of("fullName"))
        .build();

    employeeService.applyUpdated(event);

    assertEquals("Nguyễn Văn A Updated", existing.getFullName());
    verify(employeeRepository).save(existing);
    verify(processedEventRepository).save(any(ProcessedEvent.class));
  }

  @Test
  void applyTerminated_setsTerminatedStatusAndTimestamps() {
    Employee existing = new Employee();
    existing.setEmployeeId(employeeId);
    existing.setEmployeeCode("NV0001");
    existing.setStatus("ACTIVE");

    when(processedEventRepository.existsById(eventId)).thenReturn(false);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(existing));

    // Avro timestamp-millis truncate nanos
    Instant terminatedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    EmployeeTerminatedEvent event = EmployeeTerminatedEvent.newBuilder()
        .setEventId(eventId)
        .setEventType("employee.terminated")
        .setOccurredAt(Instant.now())
        .setEmployeeId(employeeId)
        .setEmployeeCode("NV0001")
        .setFullName("Nguyễn Văn A")
        .setTerminatedAt(terminatedAt)
        .setReason("Hết hợp đồng")
        .build();

    employeeService.applyTerminated(event);

    assertEquals("TERMINATED", existing.getStatus());
    assertEquals(terminatedAt, existing.getTerminatedAt());
    assertEquals("Hết hợp đồng", existing.getTerminationReason());
    verify(employeeRepository).save(existing);
  }

  @Test
  void applyTerminated_duplicateEventId_isNoOp() {
    when(processedEventRepository.existsById(eventId)).thenReturn(true);

    EmployeeTerminatedEvent event = EmployeeTerminatedEvent.newBuilder()
        .setEventId(eventId)
        .setEventType("employee.terminated")
        .setOccurredAt(Instant.now())
        .setEmployeeId(employeeId)
        .setEmployeeCode("NV0001")
        .setFullName("Nguyễn Văn A")
        .setTerminatedAt(Instant.now())
        .build();

    employeeService.applyTerminated(event);

    verify(employeeRepository, never()).save(any());
    verify(processedEventRepository, never()).save(any());
  }

  private EmployeeCreatedEvent createdEvent() {
    return EmployeeCreatedEvent.newBuilder()
        .setEventId(eventId)
        .setEventType("employee.created")
        .setOccurredAt(Instant.now())
        .setEmployeeId(employeeId)
        .setEmployeeCode("NV0001")
        .setFullName("Nguyễn Văn A")
        .setStatus("ACTIVE")
        .build();
  }
}
