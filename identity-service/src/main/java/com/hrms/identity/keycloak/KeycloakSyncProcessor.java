package com.hrms.identity.keycloak;

import com.hrms.common.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý 1 task đồng bộ Keycloak trong transaction RIÊNG (REQUIRES_NEW).
 *
 * <p>Chạy sau khi transaction nghiệp vụ đã commit (afterCommit) hoặc từ scheduler poll.
 * Khóa bi quan + kiểm tra status đảm bảo mỗi task chỉ được xử lý 1 lần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakSyncProcessor {

  private final KeycloakSyncTaskRepository taskRepository;
  private final KeycloakAdminClient keycloakAdminClient;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(UUID taskId) {
    KeycloakSyncTask task = taskRepository.findByIdForUpdate(taskId).orElse(null);
    if (task == null || task.getStatus() != KeycloakSyncStatus.PENDING) {
      return;
    }
    try {
      execute(task);
      task.markDone();
      log.info("Đồng bộ Keycloak {} '{}' thành công", task.getOperation(), task.getUsername());
    } catch (Exception ex) {
      task.markFailed(message(ex));
      if (task.getStatus() == KeycloakSyncStatus.FAILED) {
        log.error("Đồng bộ Keycloak {} '{}' thất bại sau {} lần: {}", task.getOperation(),
            task.getUsername(), task.getAttempts(), message(ex));
      } else {
        log.warn("Đồng bộ Keycloak {} '{}' lỗi lần {}/{} — sẽ thử lại: {}",
            task.getOperation(), task.getUsername(), task.getAttempts(),
            KeycloakSyncTask.MAX_ATTEMPTS, message(ex));
      }
    }
    taskRepository.save(task);
  }

  private void execute(KeycloakSyncTask task) {
    switch (task.getOperation()) {
      case CREATE_USER -> keycloakAdminClient.provisionUser(task.getUsername(),
          task.getFullName(), task.getEmail(), task.getRoleName());
      case SET_ENABLED -> {
        try {
          keycloakAdminClient.setUserEnabled(task.getUsername(),
              Boolean.TRUE.equals(task.getEnabled()));
        } catch (ResourceNotFoundException ex) {
          log.warn("Không có tài khoản Keycloak '{}' để bật/tắt — bỏ qua task",
              task.getUsername());
        }
      }
    }
  }

  private static String message(Exception ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }
}
