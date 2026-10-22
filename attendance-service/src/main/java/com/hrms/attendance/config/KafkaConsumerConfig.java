package com.hrms.attendance.config;

import com.hrms.common.kafka.KafkaTopics;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Container factory cho listener: xử lý lỗi → retry giới hạn → DLQ.
 *
 * <ul>
 *   <li>Lỗi xử lý (DB...) : retry 3 lần (1s) rồi publish record Avro đã deserializer đúng
 *       sang {@code hrms.employee.v1.DLQ}.</li>
 *   <li>Lỗi deserialize (poison pill) : ErrorHandlingDeserializer (ở hrms-common) trả
 *       value=null + exception trong header → recoverer publish {@code byte[]} gốc sang DLQ
 *       (dùng template ByteArraySerializer riêng — Avro serializer không nhận byte[]).</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfig {

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, SpecificRecord> kafkaListenerContainerFactory(
      ConsumerFactory<String, SpecificRecord> consumerFactory,
      KafkaTemplate<String, SpecificRecord> kafkaTemplate,
      KafkaProperties kafkaProperties) {

    // Template gửi byte[] (dữ liệu gốc khi deserialize fail) — không qua Avro serializer.
    Map<String, Object> rawProps = new HashMap<>(kafkaProperties.buildProducerProperties());
    rawProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    rawProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    KafkaTemplate<String, byte[]> rawTemplate =
        new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(rawProps));

    Function<ProducerRecord<?, ?>, KafkaOperations<?, ?>> templateResolver = record -> {
      if (record.value() instanceof byte[]) {
        return rawTemplate;
      }
      return kafkaTemplate;
    };
    // DLQ giữ cùng partition với topic gốc (partition assignment = 1, khớp nhau).
    BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destination =
        (record, exception) -> new TopicPartition(KafkaTopics.dlq(record.topic()), record.partition());

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(templateResolver, destination);
    // 1 lần thử + 2 retry (cách nhau 1s) rồi đưa vào DLQ.
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));

    ConcurrentKafkaListenerContainerFactory<String, SpecificRecord> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    // Tôn trọng spring.kafka.listener.auto-startup (mặc định true) — IT tắt consumer
    // để không kết nối Kafka khi chỉ test DB.
    factory.setAutoStartup(kafkaProperties.getListener().isAutoStartup());
    return factory;
  }
}
