package com.hrms.attendance.leave;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression §7.9: GET settings self-heal phải COMMIT (write transaction), không chỉ
 * trả 200 rồi mất row. Dùng transaction mới để đọc lại sau khi service trả về.
 */
@SpringBootTest
@Testcontainers
class LeaveSettingsPersistenceIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    registry.add("spring.kafka.properties.schema.registry.url", () -> "http://localhost:8081");
    registry.add("spring.kafka.listener.auto-startup", () -> "false");
  }

  @Autowired
  LeaveSettingsService settingsService;

  @Autowired
  LeaveSettingsRepository settingsRepository;

  @Test
  void getSettings_selfHealsAndCommitsRow() {
    assertThat(settingsRepository.count()).isZero();

    settingsService.getSettings();

    assertThat(settingsRepository.count()).isEqualTo(1);
  }
}
