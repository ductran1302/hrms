package com.hrms.common.kafka;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import java.util.HashMap;
import java.util.Map;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka + Avro (Confluent) dùng chung cho mọi service.
 *
 * <p>Service chỉ cần {@code @Import(KafkaAvroConfiguration.class)} và cấu hình:
 * <pre>
 * spring.kafka.bootstrap-servers=kafka:29092
 * spring.kafka.properties.schema.registry.url=http://schema-registry:8081
 * </pre>
 * Key luôn là String (aggregateId/UUID), value là {@link SpecificRecord} (Avro).
 * Bean đều {@code @ConditionalOnMissingBean} — service muốn override cứ khai báo lại.
 */
@Configuration
public class KafkaAvroConfiguration {

  /**
   * Avro 1.12 (ClassSecurityValidator) chặn mọi class không được khai báo "tin tưởng".
   * Phải set trước lần validate đầu tiên (lúc serializer/deserializer khởi tạo) — static block
   * này chạy khi load config, tức là trước mọi bean Kafka.
   */
  static {
    String property = "org.apache.avro.SERIALIZABLE_PACKAGES";
    String existing = System.getProperty(property);
    if (existing == null || existing.isBlank()) {
      System.setProperty(property, "com.hrms");
    } else if (!existing.contains("com.hrms")) {
      System.setProperty(property, existing + ",com.hrms");
    }
  }

  @Bean
  @ConditionalOnMissingBean(ProducerFactory.class)
  public ProducerFactory<String, SpecificRecord> hrmsProducerFactory(
      KafkaProperties kafkaProperties) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
    // Nhiều loại record dùng chung 1 topic: subject = tên record (không phải tên topic),
    // nếu không các record khác nhau sẽ tranh nhau 1 subject và phá compatibility check.
    props.putIfAbsent(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY,
        RecordNameStrategy.class.getName());
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  @ConditionalOnMissingBean(KafkaTemplate.class)
  public KafkaTemplate<String, SpecificRecord> hrmsKafkaTemplate(
      ProducerFactory<String, SpecificRecord> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  @ConditionalOnMissingBean(ConsumerFactory.class)
  public ConsumerFactory<String, SpecificRecord> hrmsConsumerFactory(
      KafkaProperties kafkaProperties) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
    props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
    props.putIfAbsent(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY,
        RecordNameStrategy.class.getName());
    return new DefaultKafkaConsumerFactory<>(props);
  }
}
