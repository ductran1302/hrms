package com.hrms.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bản ghi audit append-only: ai làm gì, trên đối tượng nào, lúc nào, kết quả.
 *
 * <p>Không kế thừa {@code BaseEntity} vì bảng append-only (không update/version).
 * Mỗi service có bảng {@code audit_log} trong DB riêng (Flyway migration riêng).
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  /** username trong JWT; "system" cho tác vụ nền/Kafka consumer. */
  @Column(name = "actor", nullable = false, length = 200)
  private String actor;

  /** Mã hành động, vd EMPLOYEE_CREATE, LEAVE_POLICY_APPROVE. */
  @Column(name = "action", nullable = false, length = 100)
  private String action;

  @Column(name = "target_type", length = 100)
  private String targetType;

  @Column(name = "target_id", length = 100)
  private String targetId;

  /** SUCCESS | FAILURE. */
  @Column(name = "outcome", nullable = false, length = 20)
  private String outcome;

  /** Mô tả ngắn, KHÔNG chứa dữ liệu nhạy cảm (mật khẩu, lương, PII). */
  @Column(name = "detail", length = 500)
  private String detail;

  @Column(name = "correlation_id", length = 100)
  private String correlationId;

  @Column(name = "client_ip", length = 64)
  private String clientIp;

  @PrePersist
  void prePersist() {
    if (occurredAt == null) {
      occurredAt = Instant.now();
    }
  }
}
