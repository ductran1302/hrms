package com.hrms.attendance.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ghi nhận event đã xử lý — PK = eventId chặn xử lý trùng lặp (idempotent consumer). */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

  @Id
  @Column(name = "event_id", length = 64)
  private String eventId;

  @Column(name = "event_type", length = 100, nullable = false)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  public ProcessedEvent(String eventId, String eventType) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.processedAt = Instant.now();
  }
}
