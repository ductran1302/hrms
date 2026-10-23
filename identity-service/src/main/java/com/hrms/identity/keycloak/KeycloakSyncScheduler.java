package com.hrms.identity.keycloak;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fallback bền vững cho {@link KeycloakSyncService}: poll task PENDING chưa tới hạn/đã tới
 * hạn thử lại. afterCommit xử lý ngay trong request; scheduler bắt các task còn sót sau crash
 * hoặc khi Keycloak tạm lỗi.
 *
 * <p>Kèm dọn task DONE cũ (retention) để bảng không phình vô hạn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakSyncScheduler {

  private static final int BATCH_SIZE = 50;

  private final KeycloakSyncTaskRepository taskRepository;
  private final KeycloakSyncProcessor processor;
  private final KeycloakSyncTaskService taskService;

  @Value("${hrms.keycloak.sync.retention-days:30}")
  private long retentionDays;

  @Scheduled(fixedDelayString = "${hrms.keycloak.sync.poll-ms:5000}",
      initialDelayString = "${hrms.keycloak.sync.initial-delay-ms:10000}")
  public void processPending() {
    List<UUID> taskIds;
    try {
      taskIds = taskRepository.findIdsByStatusAndNextAttemptAtBefore(
          KeycloakSyncStatus.PENDING, Instant.now(), PageRequest.of(0, BATCH_SIZE));
    } catch (Exception ex) {
      log.warn("Không quét được keycloak_sync_tasks: {}", ex.getMessage());
      return;
    }
    for (UUID taskId : taskIds) {
      try {
        processor.process(taskId);
      } catch (Exception ex) {
        log.error("Lỗi xử lý Keycloak sync task {}: {}", taskId, ex.getMessage());
      }
    }
  }

  @Scheduled(cron = "${hrms.keycloak.sync.cleanup-cron:0 30 3 * * *}")
  public void purgeDoneTasks() {
    if (retentionDays <= 0) {
      return;
    }
    try {
      taskService.purgeDoneBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    } catch (Exception ex) {
      log.warn("Không dọn được keycloak_sync_tasks DONE: {}", ex.getMessage());
    }
  }
}
