package com.hrms.attendance.event;

import com.hrms.attendance.department.DepartmentService;
import com.hrms.common.avro.department.DepartmentCreatedEvent;
import com.hrms.common.avro.department.DepartmentUpdatedEvent;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer event phòng ban — idempotent (bấm neo theo eventId trong DepartmentService).
 * Lỗi xử lý do container factory đưa vào DLQ {@code hrms.department.v1.DLQ} sau 3 lần thử.
 */
@Component
@RequiredArgsConstructor
public class DepartmentEventConsumer {

  private final DepartmentService departmentService;

  @KafkaListener(
      topics = KafkaTopics.DEPARTMENT_V1,
      containerFactory = "kafkaListenerContainerFactory",
      groupId = "attendance-service")
  public void onDepartmentEvent(ConsumerRecord<String, SpecificRecord> record) {
    SpecificRecord value = record.value();
    if (value instanceof DepartmentCreatedEvent event) {
      departmentService.applyCreated(event);
    } else if (value instanceof DepartmentUpdatedEvent event) {
      departmentService.applyUpdated(event);
    } else {
      // Không biết xử lý → ném để error handler retry rồi đưa vào DLQ
      throw new BusinessRuleException(
          "Loại event không hỗ trợ, topic=" + record.topic()
              + " offset=" + record.offset()
              + " type=" + (value == null ? "null" : value.getClass().getName()));
    }
  }
}
