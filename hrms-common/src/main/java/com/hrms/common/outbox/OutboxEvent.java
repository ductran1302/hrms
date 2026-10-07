package com.hrms.common.outbox;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bảng outbox (mỗi service 1 bảng cùng tên trong DB của nó). Ghi trong cùng transaction với
 * business data; {@link OutboxPublisher} poll và publish lên Kafka → đảm bảo exactly-once
 * về phía business (giao dịch DB không bao giờ phụ thuộc vào Kafka).
 *
 * <p>{@code payload} lưu JSON (đọc được để debug/repair); khi publish sẽ convert sang Avro.
 *
 * <p>Service dùng bảng này phải bật {@code @EnableJpaAuditing} — publisher order theo
 * {@code createdAt} để các event cùng entity được gửi đúng thứ tự.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

  @Column(name = "aggregate_type", nullable = false, length = 100)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, length = 64)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "topic", nullable = false, length = 200)
  private String topic;

  /** JSON payload (Avro record được serialize về JSON tương đương) — Postgres {@code text}. */
  @Column(name = "payload", nullable = false, columnDefinition = "text")
  private String payload;

  /** Partition key — mặc định = aggregateId để mọi event cùng entity về cùng partition. */
  @Column(name = "partition_key", length = 64)
  private String partitionKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  public static OutboxEvent of(String aggregateType, String aggregateId, String eventType,
      String topic, String payload) {
    OutboxEvent event = new OutboxEvent();
    event.setAggregateType(aggregateType);
    event.setAggregateId(aggregateId);
    event.setEventType(eventType);
    event.setTopic(topic);
    event.setPayload(payload);
    event.setPartitionKey(aggregateId);
    event.setStatus(OutboxStatus.PENDING);
    return event;
  }
}
