package com.hrms.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Poll bảng outbox định kỳ và publish lên Kafka. Kích hoạt bằng cấu hình
 * {@code hrms.outbox.enabled=true} (mỗi service tự bật khi nó có outbox).
 *
 * <p>Gửi đồng bộ (`.get(timeout)`) để biết chắc thành công/thất bại → update trạng thái chính xác;
 * bù lại mỗi lần poll chỉ lấy {@code batch-size} dòng, không gửi nền hàng loạt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hrms.outbox", name = "enabled", havingValue = "true")
public class OutboxPublisher {

  private final OutboxRepository repository;
  private final OutboxMessageConverter converter;
  private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;

  @Value("${hrms.outbox.batch-size:50}")
  private int batchSize;

  @Value("${hrms.outbox.max-attempts:5}")
  private int maxAttempts;

  @Value("${hrms.outbox.send-timeout-seconds:10}")
  private long sendTimeoutSeconds;

  @Scheduled(fixedDelayString = "${hrms.outbox.poll-interval-ms:1000}")
  public void publishPending() {
    List<OutboxEvent> batch =
        repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, batchSize));
    for (OutboxEvent event : batch) {
      publishOne(event);
    }
  }

  void publishOne(OutboxEvent event) {
    try {
      ProducerRecord<String, SpecificRecord> record = converter.convert(event);
      kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
      repository.markPublished(event.getId(), OutboxStatus.PUBLISHED, Instant.now());
      log.debug("Outbox published: id={} topic={} type={}", event.getId(), event.getTopic(),
          event.getEventType());
    } catch (Exception ex) {
      handleFailure(event, ex);
    }
  }

  private void handleFailure(OutboxEvent event, Exception ex) {
    int nextAttempts = event.getAttempts() + 1;
    String error = truncate(ex.toString());
    if (nextAttempts >= maxAttempts) {
      repository.markDead(event.getId(), OutboxStatus.FAILED, error);
      log.error("Outbox FAILED sau {} lần thử: id={} type={} — cần xem xét thủ công",
          nextAttempts, event.getId(), event.getEventType(), ex);
    } else {
      repository.recordFailure(event.getId(), error);
      log.warn("Outbox publish lỗi (thử {}/{}): id={} type={} — {}",
          nextAttempts, maxAttempts, event.getId(), event.getEventType(), ex.toString());
    }
  }

  private static String truncate(String value) {
    return value != null && value.length() > 1000 ? value.substring(0, 1000) : value;
  }
}
