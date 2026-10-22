package com.hrms.identity.keycloak;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Task đồng bộ Keycloak (P0-6). Ghi trong CÙNG transaction với business data; chỉ được xử lý
 * sau khi transaction commit (afterCommit) hoặc bởi scheduler poll PENDING làm fallback.
 *
 * <p>Mục tiêu: không để Keycloak write chạy bên trong transaction DB — tránh trường hợp KC
 * thành công nhưng DB rollback (user mồ côi) hoặc KC chậm giữ connection transaction.
 */
@Entity
@Table(name = "keycloak_sync_tasks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeycloakSyncTask extends BaseEntity {

  /** Số lần thử tối đa trước khi chuyển FAILED (không tự retry nữa). */
  public static final int MAX_ATTEMPTS = 5;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation", nullable = false, length = 30)
  private KeycloakSyncOperation operation;

  @Column(name = "username", nullable = false, length = 64)
  private String username;

  @Column(name = "full_name", length = 200)
  private String fullName;

  @Column(name = "email", length = 200)
  private String email;

  @Column(name = "role_name", length = 30)
  private String roleName;

  @Column(name = "enabled")
  private Boolean enabled;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private KeycloakSyncStatus status = KeycloakSyncStatus.PENDING;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  public static KeycloakSyncTask createUser(String username, String fullName, String email,
      String roleName) {
    KeycloakSyncTask task = new KeycloakSyncTask();
    task.operation = KeycloakSyncOperation.CREATE_USER;
    task.username = username;
    task.fullName = fullName;
    task.email = email;
    task.roleName = roleName;
    task.status = KeycloakSyncStatus.PENDING;
    task.nextAttemptAt = Instant.now();
    return task;
  }

  public static KeycloakSyncTask setUserEnabled(String username, boolean enabled) {
    KeycloakSyncTask task = new KeycloakSyncTask();
    task.operation = KeycloakSyncOperation.SET_ENABLED;
    task.username = username;
    task.enabled = enabled;
    task.status = KeycloakSyncStatus.PENDING;
    task.nextAttemptAt = Instant.now();
    return task;
  }

  void markDone() {
    status = KeycloakSyncStatus.DONE;
    lastError = null;
  }

  void markFailed(String error) {
    attempts++;
    lastError = error == null ? null
        : (error.length() > 1000 ? error.substring(0, 1000) : error);
    if (attempts >= MAX_ATTEMPTS) {
      status = KeycloakSyncStatus.FAILED;
    } else {
      nextAttemptAt = Instant.now().plusSeconds(5L * attempts);
    }
  }
}
