package com.hrms.attendance.holiday;

import com.hrms.common.avro.holiday.HolidayEvent;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer event ngày lễ — idempotent (bấm neo theo eventId trong HolidayService).
 * Lỗi xử lý do container factory đưa vào DLQ {@code hrms.holiday.v1.DLQ} sau 3 lần thử.
 */
@Component
@RequiredArgsConstructor
public class HolidayEventConsumer {

  private final HolidayService holidayService;

  @KafkaListener(
      topics = KafkaTopics.HOLIDAY_V1,
      containerFactory = "kafkaListenerContainerFactory",
      groupId = "attendance-service")
  public void onHolidayEvent(ConsumerRecord<String, SpecificRecord> record) {
    SpecificRecord value = record.value();
    if (value instanceof HolidayEvent event) {
      holidayService.applyChanged(event);
    } else {
      // Không biết xử lý → ném để error handler retry rồi đưa vào DLQ
      throw new BusinessRuleException(
          "Loại event không hỗ trợ, topic=" + record.topic()
              + " offset=" + record.offset()
              + " type=" + (value == null ? "null" : value.getClass().getName()));
    }
  }
}
