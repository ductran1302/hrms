package com.hrms.attendance.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hrms.attendance.employee.EmployeeService;
import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import com.hrms.common.avro.employee.EmployeeTerminatedEvent;
import com.hrms.common.avro.employee.EmployeeUpdatedEvent;
import com.hrms.common.exception.BusinessRuleException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeEventConsumerTest {

  @Mock
  private EmployeeService employeeService;

  @InjectMocks
  private EmployeeEventConsumer consumer;

  private static final String TOPIC = "hrms.employee.v1";

  @Test
  void createdEvent_dispatchesToApplyCreated() {
    EmployeeCreatedEvent event = EmployeeCreatedEvent.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setEventType("employee.created")
        .setOccurredAt(Instant.now())
        .setEmployeeId(UUID.randomUUID().toString())
        .setEmployeeCode("NV0001")
        .setFullName("Nguyễn Văn A")
        .setStatus("ACTIVE")
        .build();

    consumer.onEmployeeEvent(record(0L, event));

    verify(employeeService).applyCreated(event);
  }

  @Test
  void updatedEvent_dispatchesToApplyUpdated() {
    EmployeeUpdatedEvent event = EmployeeUpdatedEvent.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setEventType("employee.updated")
        .setOccurredAt(Instant.now())
        .setEmployeeId(UUID.randomUUID().toString())
        .setEmployeeCode("NV0001")
        .setFullName("Nguyễn Văn A")
        .setStatus("ACTIVE")
        .setChangedFields(List.of("fullName"))
        .build();

    consumer.onEmployeeEvent(record(2L, event));

    verify(employeeService).applyUpdated(event);
  }

  @Test
  void terminatedEvent_dispatchesToApplyTerminated() {
    EmployeeTerminatedEvent event = EmployeeTerminatedEvent.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setEventType("employee.terminated")
        .setOccurredAt(Instant.now())
        .setEmployeeId(UUID.randomUUID().toString())
        .setEmployeeCode("NV0001")
        .setFullName("Nguyễn Văn A")
        .setTerminatedAt(Instant.now())
        .build();

    consumer.onEmployeeEvent(record(4L, event));

    verify(employeeService).applyTerminated(event);
  }

  @Test
  void unknownEventType_throwsForDlq() {
    SpecificRecord unknown = new SpecificRecord() {
      @Override
      public void put(int i, Object v) {}

      @Override
      public Object get(int i) {
        return null;
      }

      @Override
      public org.apache.avro.Schema getSchema() {
        return null;
      }
    };

    assertThrows(BusinessRuleException.class, () -> consumer.onEmployeeEvent(record(6L, unknown)));
    verifyNoInteractions(employeeService);
  }

  private static ConsumerRecord<String, SpecificRecord> record(long offset, SpecificRecord value) {
    return new ConsumerRecord<>(TOPIC, 0, offset, "key", value);
  }
}
