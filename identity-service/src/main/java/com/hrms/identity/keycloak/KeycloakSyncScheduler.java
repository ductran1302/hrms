package com.hrms.identity.keycloak;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fallback bền vững cho {@link KeycloakSyncService}: poll task PENDING chưa tới hạn/đã tới
 * hạn thử lại. afterCommit xử lý ngay trong request; scheduler bắt các task còn sót sau crash
 * hoặc khi Keycloak tạm lỗi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakSyncScheduler {

  private static final int BATCH_SIZE = 50;

  private final KeycloakSyncTaskRepository taskRepository;
  private final KeycloakSyncProcessor processor;

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
}
