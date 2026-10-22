package com.hrms.identity.keycloak;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ghi task đồng bộ Keycloak trong CÙNG transaction nghiệp vụ (P0-6).
 *
 * <p>Task chỉ được xử lý sau khi transaction commit. Nếu transaction rollback, task cũng
 * rollback theo → không có user Keycloak mồ côi. {@code MANDATORY} đảm bảo không ghi task
 * ngoài transaction (mất tính nguyên tử).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakSyncService {

  private final KeycloakSyncTaskRepository taskRepository;
  private final KeycloakSyncProcessor processor;

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueueCreate(String username, String fullName, String email, String roleName) {
    schedule(KeycloakSyncTask.createUser(username, fullName, email, roleName));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueueEnabled(String username, boolean enabled) {
    schedule(KeycloakSyncTask.setUserEnabled(username, enabled));
  }

  private void schedule(KeycloakSyncTask task) {
    taskRepository.save(task);
    UUID taskId = task.getId();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          processor.process(taskId);
        } catch (RuntimeException ex) {
          // Task vẫn PENDING trong DB — scheduler sẽ thử lại.
          log.warn("Không xử lý ngay được Keycloak sync task {}: {}", taskId, ex.getMessage());
        }
      }
    });
  }
}
