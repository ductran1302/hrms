package com.hrms.identity.event;

import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import com.hrms.common.avro.employee.EmployeeTerminatedEvent;
import com.hrms.common.avro.employee.EmployeeUpdatedEvent;
import com.hrms.common.avro.holiday.HolidayAction;
import com.hrms.common.avro.holiday.HolidayEvent;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.kafka.EmployeeEventTypes;
import com.hrms.common.kafka.HolidayEventTypes;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxMessageConverter;
import com.hrms.common.outbox.OutboxPayloadConverter;
import java.time.Instant;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

/**
 * Convert payload JSON trong outbox → Avro record. Một bean duy nhất cho cả identity
 * (OutboxPublisher inject đúng 1 OutboxMessageConverter) — employee.* + holiday.*.
 * {@code eventId} = id dòng outbox — retry sau lỗi gửi vẫn giữ nguyên eventId
 * nên consumer idempotent chặn được trùng lặp.
 */
@Component
public class IdentityOutboxConverter implements OutboxMessageConverter {

  @Override
  public ProducerRecord<String, SpecificRecord> convert(OutboxEvent event) {
    SpecificRecord record = switch (event.getEventType()) {
      case EmployeeEventTypes.CREATED ->
          toCreated(event, payload(event, EmployeeEventPayload.class));
      case EmployeeEventTypes.UPDATED ->
          toUpdated(event, payload(event, EmployeeEventPayload.class));
      case EmployeeEventTypes.TERMINATED ->
          toTerminated(event, payload(event, EmployeeEventPayload.class));
      case HolidayEventTypes.CHANGED ->
          toHoliday(event, payload(event, HolidayEventPayload.class));
      default -> throw new BusinessRuleException(
          "Không biết cách convert event type: " + event.getEventType());
    };
    return new ProducerRecord<>(event.getTopic(), event.getPartitionKey(), record);
  }

  private static <T> T payload(OutboxEvent event, Class<T> type) {
    return OutboxPayloadConverter.fromJson(event.getPayload(), type);
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

  private HolidayEvent toHoliday(OutboxEvent event, HolidayEventPayload p) {
    return HolidayEvent.newBuilder()
        .setEventId(event.getId().toString())
        .setEventType(HolidayEventTypes.CHANGED)
        .setOccurredAt(occurredAt(event))
        .setAction(HolidayAction.valueOf(p.action()))
        .setHolidayId(p.holidayId())
        .setCalendarId(p.calendarId())
        .setCalendarCode(p.calendarCode())
        .setName(p.name())
        .setDateFrom(p.dateFrom())
        .setDateTo(p.dateTo())
        .setYear(p.year())
        .setHolidayType(p.holidayType())
        .setIsHalfDay(p.halfDay())
        .setNote(p.note())
        .build();
  }

  private static Instant occurredAt(OutboxEvent event) {
    return event.getCreatedAt() != null ? event.getCreatedAt() : Instant.now();
  }
}
