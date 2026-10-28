package com.hrms.identity.keycloak;

import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.keycloak.dto.KeycloakSyncTaskResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Vận hành keycloak_sync_tasks (P0-6): ADMIN xem task, retry task FAILED, dọn task DONE cũ.
 *
 * <p>Retry chỉ cho task {@code FAILED}: reset về PENDING (attempts=0, xóa last_error) rồi xử lý
 * lại NGAY sau khi transaction commit (cùng cơ chế afterCommit như {@link KeycloakSyncService}).
 * Task PENDING/DONE không retry được để tránh xử lý trùng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakSyncTaskService {

  private static final int MAX_PAGE_SIZE = 100;

  private final KeycloakSyncTaskRepository taskRepository;
  private final KeycloakSyncProcessor processor;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public PageResponse<KeycloakSyncTaskResponse> search(KeycloakSyncStatus status, int page,
      int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    List<KeycloakSyncTaskResponse> items =
        taskRepository.search(status, PageRequest.of(safePage, safeSize)).stream()
            .map(KeycloakSyncTaskResponse::from)
            .toList();
    long total = taskRepository.countSearch(status);
    return PageResponse.of(items, safePage, safeSize, total);
  }

  @Transactional
  public KeycloakSyncTaskResponse retry(UUID taskId) {
    KeycloakSyncTask task = taskRepository.findByIdForUpdate(taskId)
        .orElseThrow(() -> new ResourceNotFoundException("KeycloakSyncTask", taskId));
    if (task.getStatus() != KeycloakSyncStatus.FAILED) {
      throw new BusinessRuleException(
          "Chỉ retry được task FAILED (hiện tại: " + task.getStatus() + ")");
    }
    task.setStatus(KeycloakSyncStatus.PENDING);
    task.setAttempts(0);
    task.setLastError(null);
    task.setNextAttemptAt(Instant.now());
    taskRepository.save(task);
    KeycloakSyncTaskResponse response = KeycloakSyncTaskResponse.from(task);

    UUID id = task.getId();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          processor.process(id);
        } catch (RuntimeException ex) {
          // Task vẫn PENDING trong DB — scheduler sẽ thử lại.
          log.warn("Không retry ngay được Keycloak sync task {}: {}", id, ex.getMessage());
        }
      }
    });
    auditService.success("KEYCLOAK_SYNC_RETRY", "KeycloakSyncTask", id,
        "operation=" + task.getOperation() + " username=" + task.getUsername());
    log.info("ADMIN retry Keycloak sync task {} (operation={}, username={})", id,
        task.getOperation(), task.getUsername());
    return response;
  }

  @Transactional
  public int purgeDoneBefore(Instant cutoff) {
    int deleted = taskRepository.deleteByStatusAndUpdatedAtBefore(KeycloakSyncStatus.DONE, cutoff);
    if (deleted > 0) {
      log.info("Đã dọn {} keycloak_sync_tasks DONE trước {}", deleted, cutoff);
    }
    return deleted;
  }
}
