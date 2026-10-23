package com.hrms.identity.keycloak.dto;

import com.hrms.identity.keycloak.KeycloakSyncTask;
import java.time.Instant;
import java.util.UUID;

/** Task đồng bộ Keycloak — dùng cho màn vận hành (ADMIN xem/retry task FAILED). */
public record KeycloakSyncTaskResponse(
    UUID id,
    String operation,
    String username,
    String fullName,
    String email,
    String roleName,
    Boolean enabled,
    String status,
    int attempts,
    Instant nextAttemptAt,
    String lastError,
    Instant createdAt,
    Instant updatedAt) {

  public static KeycloakSyncTaskResponse from(KeycloakSyncTask task) {
    return new KeycloakSyncTaskResponse(
        task.getId(),
        task.getOperation() == null ? null : task.getOperation().name(),
        task.getUsername(),
        task.getFullName(),
        task.getEmail(),
        task.getRoleName(),
        task.getEnabled(),
        task.getStatus() == null ? null : task.getStatus().name(),
        task.getAttempts(),
        task.getNextAttemptAt(),
        task.getLastError(),
        task.getCreatedAt(),
        task.getUpdatedAt());
  }
}
