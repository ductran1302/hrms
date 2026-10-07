package com.hrms.identity.event;

import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import com.hrms.common.avro.employee.EmployeeTerminatedEvent;
import com.hrms.common.avro.employee.EmployeeUpdatedEvent;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.kafka.EmployeeEventTypes;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxMessageConverter;
import com.hrms.common.outbox.OutboxPayloadConverter;
import java.time.Instant;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

/**
 * Convert payload JSON trong outbox → Avro record. {@code eventId} = id dòng outbox —
 * retry sau lỗi gửi vẫn giữ nguyên eventId nên consumer idempotent chặn được trùng lặp.
 */
@Component
public class EmployeeOutboxConverter implements OutboxMessageConverter {

  @Override
  public ProducerRecord<String, SpecificRecord> convert(OutboxEvent event) {
    EmployeeEventPayload payload =
        OutboxPayloadConverter.fromJson(event.getPayload(), EmployeeEventPayload.class);
    SpecificRecord record = switch (event.getEventType()) {
      case EmployeeEventTypes.CREATED -> toCreated(event, payload);
      case EmployeeEventTypes.UPDATED -> toUpdated(event, payload);
      case EmployeeEventTypes.TERMINATED -> toTerminated(event, payload);
      default -> throw new BusinessRuleException(
          "Không biết cách convert event type: " + event.getEventType());
    };
    return new ProducerRecord<>(event.getTopic(), event.getPartitionKey(), record);
  }

  private EmployeeCreatedEvent toCreated(OutboxEvent event, EmployeeEventPayload p) {
    EmployeeCreatedEvent.Builder builder = EmployeeCreatedEvent.newBuilder()
        .setEventId(event.getId().toString())
        .setEventType(EmployeeEventTypes.CREATED)
        .setOccurredAt(occurredAt(event))
        .setEmployeeId(p.employeeId())
        .setEmployeeCode(p.employeeCode())
        .setFullName(p.fullName())
        .setStatus(p.status());
    if (p.email() != null) {
      builder.setEmail(p.email());
    }
    if (p.departmentId() != null) {
      builder.setDepartmentId(p.departmentId());
    }
    if (p.departmentName() != null) {
      builder.setDepartmentName(p.departmentName());
    }
    if (p.position() != null) {
      builder.setPosition(p.position());
    }
    if (p.managerId() != null) {
      builder.setManagerId(p.managerId());
    }
    if (p.hiredAt() != null) {
      builder.setHiredAt(Instant.ofEpochMilli(p.hiredAt()));
    }
    if (p.actorId() != null) {
      builder.setActorId(p.actorId());
    }
    return builder.build();
  }

  private EmployeeUpdatedEvent toUpdated(OutboxEvent event, EmployeeEventPayload p) {
    EmployeeUpdatedEvent.Builder builder = EmployeeUpdatedEvent.newBuilder()
        .setEventId(event.getId().toString())
        .setEventType(EmployeeEventTypes.UPDATED)
        .setOccurredAt(occurredAt(event))
        .setEmployeeId(p.employeeId())
        .setEmployeeCode(p.employeeCode())
        .setFullName(p.fullName())
        .setStatus(p.status())
        .setChangedFields(p.changedFields());
    if (p.email() != null) {
      builder.setEmail(p.email());
    }
    if (p.departmentId() != null) {
      builder.setDepartmentId(p.departmentId());
    }
    if (p.departmentName() != null) {
      builder.setDepartmentName(p.departmentName());
    }
    if (p.position() != null) {
      builder.setPosition(p.position());
    }
    if (p.managerId() != null) {
      builder.setManagerId(p.managerId());
    }
    if (p.hiredAt() != null) {
      builder.setHiredAt(Instant.ofEpochMilli(p.hiredAt()));
    }
    if (p.actorId() != null) {
      builder.setActorId(p.actorId());
    }
    return builder.build();
  }

  private EmployeeTerminatedEvent toTerminated(OutboxEvent event, EmployeeEventPayload p) {
    long terminatedAt = p.terminatedAt() != null ? p.terminatedAt() : occurredAt(event).toEpochMilli();
    EmployeeTerminatedEvent.Builder builder = EmployeeTerminatedEvent.newBuilder()
        .setEventId(event.getId().toString())
        .setEventType(EmployeeEventTypes.TERMINATED)
        .setOccurredAt(occurredAt(event))
        .setEmployeeId(p.employeeId())
        .setEmployeeCode(p.employeeCode())
        .setFullName(p.fullName())
        .setTerminatedAt(Instant.ofEpochMilli(terminatedAt));
    if (p.reason() != null) {
      builder.setReason(p.reason());
    }
    if (p.actorId() != null) {
      builder.setActorId(p.actorId());
    }
    return builder.build();
  }

  private static Instant occurredAt(OutboxEvent event) {
    return event.getCreatedAt() != null ? event.getCreatedAt() : Instant.now();
  }
}
