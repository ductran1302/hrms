package com.hrms.identity.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrms.common.audit.AuditLog;
import com.hrms.common.audit.AuditLogRepository;
import com.hrms.common.audit.AuditService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test audit log: PostgreSQL thật (Testcontainers) + Flyway + JPA.
 *
 * <p>Kiểm chứng {@link AuditService} join transaction nghiệp vụ (rollback cùng nhau) và tự
 * mở transaction khi gọi ngoài transaction; actor mặc định là {@code system} khi không có
 * SecurityContext (job/scheduler).
 */
@SpringBootTest
@Testcontainers
class AuditLogIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("hrms.outbox.enabled", () -> "false");
    registry.add("hrms.seed.enabled", () -> "false");
    registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    registry.add("spring.kafka.properties.schema.registry.url", () -> "http://localhost:8081");
  }

  @Autowired
  AuditService auditService;

  @Autowired
  AuditLogRepository auditLogRepository;

  @Autowired
  TransactionTemplate transactionTemplate;

  @Test
  void success_committed_persistsAuditRow() {
    UUID targetId = UUID.randomUUID();

    transactionTemplate.executeWithoutResult(status ->
        auditService.success("IT_AUDIT_OK", "Employee", targetId, "detail-1"));

    AuditLog entry = auditLogRepository.findAll().stream()
        .filter(row -> "IT_AUDIT_OK".equals(row.getAction()))
        .findFirst()
        .orElseThrow();
    assertThat(entry.getActor()).isEqualTo(AuditService.SYSTEM);
    assertThat(entry.getOutcome()).isEqualTo("SUCCESS");
    assertThat(entry.getTargetType()).isEqualTo("Employee");
    assertThat(entry.getTargetId()).isEqualTo(targetId.toString());
    assertThat(entry.getDetail()).isEqualTo("detail-1");
    assertThat(entry.getOccurredAt()).isNotNull();
  }

  @Test
  void success_rolledBack_doesNotPersistAuditRow() {
    long before = auditLogRepository.count();

    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
      auditService.success("IT_AUDIT_ROLLBACK", "Employee", UUID.randomUUID(), null);
      throw new IllegalStateException("force rollback");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(auditLogRepository.count()).isEqualTo(before);
    assertThat(auditLogRepository.findAll())
        .noneMatch(row -> "IT_AUDIT_ROLLBACK".equals(row.getAction()));
  }

  @Test
  void success_withoutTransaction_opensOwnTransaction() {
    UUID targetId = UUID.randomUUID();

    auditService.success("IT_AUDIT_OWN_TX", "Contract", targetId, null);

    assertThat(auditLogRepository.findAll())
        .anyMatch(row -> "IT_AUDIT_OWN_TX".equals(row.getAction())
            && targetId.toString().equals(row.getTargetId()));
  }
}
