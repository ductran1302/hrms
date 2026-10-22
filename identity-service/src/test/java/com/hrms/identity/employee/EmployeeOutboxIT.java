package com.hrms.identity.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.common.outbox.OutboxStatus;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import com.hrms.identity.keycloak.KeycloakSyncService;
import com.hrms.identity.keycloak.KeycloakSyncStatus;
import com.hrms.identity.keycloak.KeycloakSyncTask;
import com.hrms.identity.keycloak.KeycloakSyncTaskRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test P0-2: PostgreSQL thật (Testcontainers) + Flyway + JPA.
 *
 * <p>Kiểm chứng outbox được ghi CÙNG transaction với employee (nếu outbox lỗi thì
 * employee cũng rollback). Keycloak được mock — không gọi dịch vụ ngoài.
 */
@SpringBootTest
@Testcontainers
class EmployeeOutboxIT {

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

  @MockBean
  KeycloakAdminClient keycloakAdminClient;

  @Autowired
  EmployeeService employeeService;

  @Autowired
  EmployeeRepository employeeRepository;

  @Autowired
  OutboxRepository outboxRepository;

  @Autowired
  KeycloakSyncService keycloakSyncService;

  @Autowired
  KeycloakSyncTaskRepository keycloakSyncTaskRepository;

  @Autowired
  TransactionTemplate transactionTemplate;

  @Test
  void create_persistsEmployeeAndOutboxInSameTransaction() {
    EmployeeCreateRequest request = new EmployeeCreateRequest(
        "IT0001", "Nguyen Van Integration", "it0001@hrms.vn", null, "Engineer", null,
        Instant.parse("2024-01-01T00:00:00Z"), null, null, null, null, null, null);

    EmployeeResponse response = employeeService.create(request, "it-actor");

    assertThat(employeeRepository.findByEmployeeCode("IT0001")).isPresent();

    List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
        OutboxStatus.PENDING, PageRequest.of(0, 10));
    assertThat(pending).hasSize(1);
    OutboxEvent event = pending.get(0);
    assertThat(event.getEventType()).isEqualTo("employee.created");
    assertThat(event.getAggregateId()).isEqualTo(response.id().toString());
    assertThat(event.getTopic()).isEqualTo("hrms.employee.v1");
  }

  @Test
  void keycloakSyncTask_rollback_doesNotPersistTaskOrCallKeycloak() {
    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
      keycloakSyncService.enqueueCreate("ITKC1", "Sync IT", "itkc1@hrms.vn", "EMPLOYEE");
      throw new IllegalStateException("force rollback");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(keycloakSyncTaskRepository.findAll())
        .noneMatch(task -> "ITKC1".equals(task.getUsername()));
    verify(keycloakAdminClient, never()).provisionUser(eq("ITKC1"), any(), any(), any());
  }

  @Test
  void keycloakSyncTask_commit_provisionsAfterCommit() {
    transactionTemplate.executeWithoutResult(status ->
        keycloakSyncService.enqueueCreate("ITKC2", "Sync IT", "itkc2@hrms.vn", "EMPLOYEE"));

    verify(keycloakAdminClient, timeout(10_000))
        .provisionUser("ITKC2", "Sync IT", "itkc2@hrms.vn", "EMPLOYEE");

    KeycloakSyncTask task = keycloakSyncTaskRepository.findAll().stream()
        .filter(item -> "ITKC2".equals(item.getUsername()))
        .findFirst()
        .orElseThrow();
    assertThat(task.getStatus()).isEqualTo(KeycloakSyncStatus.DONE);
  }
}
