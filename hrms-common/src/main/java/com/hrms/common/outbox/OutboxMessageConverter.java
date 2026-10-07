package com.hrms.common.outbox;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Convert 1 dòng {@link OutboxEvent} (payload JSON) thành {@link ProducerRecord} Avro để gửi.
 *
 * <p>Mỗi service tự implement (nó biết cách map JSON của event mình sang record Avro tương ứng),
 * đăng ký bean rồi inject vào {@link OutboxPublisher}.
 */
public interface OutboxMessageConverter {

  ProducerRecord<String, SpecificRecord> convert(OutboxEvent event);
}
