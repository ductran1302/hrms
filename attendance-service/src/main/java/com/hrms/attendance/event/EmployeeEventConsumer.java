package com.hrms.attendance.event;

import com.hrms.attendance.employee.EmployeeService;
import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import com.hrms.common.avro.employee.EmployeeTerminatedEvent;
import com.hrms.common.avro.employee.EmployeeUpdatedEvent;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer event nhân sự — idempotent (bấm neo theo eventId trong EmployeeService).
 * Lỗi xử lý do container factory đưa vào DLQ {@code hrms.employee.v1.DLQ} sau 3 lần thử.
 */
@Component
@RequiredArgsConstructor
public class EmployeeEventConsumer {

  private final EmployeeService employeeService;

  @KafkaListener(
      topics = KafkaTopics.EMPLOYEE_V1,
      containerFactory = "kafkaListenerContainerFactory",
      groupId = "attendance-service")
  public void onEmployeeEvent(ConsumerRecord<String, SpecificRecord> record) {
    SpecificRecord value = record.value();
    if (value instanceof EmployeeCreatedEvent event) {
      employeeService.applyCreated(event);
    } else if (value instanceof EmployeeUpdatedEvent event) {
      employeeService.applyUpdated(event);
    } else if (value instanceof EmployeeTerminatedEvent event) {
      employeeService.applyTerminated(event);
    } else {
      // Không biết xử lý → ném để error handler retry rồi đưa vào DLQ
      throw new BusinessRuleException(
          "Loại event không hỗ trợ, topic=" + record.topic()
              + " offset=" + record.offset()
              + " type=" + (value == null ? "null" : value.getClass().getName()));
    }
  }
}
