package com.hrms.common.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  @Mock
  private OutboxRepository repository;

  @Mock
  private OutboxMessageConverter converter;

  @Mock
  private KafkaTemplate<String, SpecificRecord> kafkaTemplate;

  @InjectMocks
  private OutboxPublisher publisher;

  private final OutboxEvent event = pendingEvent();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(publisher, "batchSize", 10);
    ReflectionTestUtils.setField(publisher, "maxAttempts", 3);
    ReflectionTestUtils.setField(publisher, "sendTimeoutSeconds", 1L);
  }

  @Test
  void publishPending_marksPublishedOnSuccess() {
    ProducerRecord<String, SpecificRecord> record = new ProducerRecord<>("hrms.employee.v1", "k", null);
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(event));
    when(converter.convert(event)).thenReturn(record);
    when(kafkaTemplate.send(record)).thenReturn(CompletableFuture.completedFuture(null));

    publisher.publishPending();

    verify(kafkaTemplate).send(record);
    verify(repository).markPublished(eq(event.getId()), eq(OutboxStatus.PUBLISHED), any(Instant.class));
    verify(repository, never()).recordFailure(any(), any());
    verify(repository, never()).markDead(any(), any(), any());
  }

  @Test
  void publishPending_recordsFailureOnSendError() {
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(event));
    when(converter.convert(event))
        .thenReturn(new ProducerRecord<>("hrms.employee.v1", "k", null));
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

    publisher.publishPending();

    verify(repository).recordFailure(eq(event.getId()), contains("broker down"));
    verify(repository, never()).markPublished(any(), any(), any());
    verify(repository, never()).markDead(any(), any(), any());
  }

  @Test
  void publishPending_marksDeadWhenAttemptsExceeded() {
    event.setAttempts(2);
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(event));
    when(converter.convert(event))
        .thenReturn(new ProducerRecord<>("hrms.employee.v1", "k", null));
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("still down")));

    publisher.publishPending();

    verify(repository).markDead(eq(event.getId()), eq(OutboxStatus.FAILED), contains("still down"));
    verify(repository, never()).recordFailure(any(), any());
    verify(repository, never()).markPublished(any(), any(), any());
  }

  @Test
  void publishPending_recordsFailureWhenConverterThrows() {
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(event));
    when(converter.convert(event)).thenThrow(new IllegalArgumentException("bad payload"));

    publisher.publishPending();

    verify(repository).recordFailure(eq(event.getId()), contains("bad payload"));
    verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
  }

  @Test
  void publishPending_doesNothingWhenBatchEmpty() {
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of());

    publisher.publishPending();

    verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    verify(repository, never()).markPublished(any(), any(), any());
  }

  @Test
  void publishPending_respectsBatchSize() {
    when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), eq(PageRequest.of(0, 10))))
        .thenReturn(List.of());

    publisher.publishPending();

    verify(repository).findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10));
  }

  private static OutboxEvent pendingEvent() {
    OutboxEvent event = OutboxEvent.of("Employee", UUID.randomUUID().toString(),
        "employee.created", "hrms.employee.v1", "{\"employeeCode\":\"NV0001\"}");
    event.setId(UUID.randomUUID());
    return event;
  }
}
