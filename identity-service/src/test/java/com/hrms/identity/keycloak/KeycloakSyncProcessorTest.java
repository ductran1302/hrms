package com.hrms.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeycloakSyncProcessorTest {

  @Mock
  private KeycloakSyncTaskRepository taskRepository;

  @Mock
  private KeycloakAdminClient keycloakAdminClient;

  @InjectMocks
  private KeycloakSyncProcessor processor;

  @Test
  void process_createUser_provisionsAndMarksDone() {
    KeycloakSyncTask task = createTask();
    when(taskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));
    when(taskRepository.save(any(KeycloakSyncTask.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    processor.process(task.getId());

    verify(keycloakAdminClient).provisionUser("u1", "User One", "u1@hrms.vn", "EMPLOYEE");
    assertThat(task.getStatus()).isEqualTo(KeycloakSyncStatus.DONE);
    assertThat(task.getAttempts()).isZero();
  }

  @Test
  void process_setEnabledMissingKeycloakUser_marksDone() {
    KeycloakSyncTask task = KeycloakSyncTask.setUserEnabled("u1", false);
    task.setId(UUID.randomUUID());
    when(taskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));
    when(taskRepository.save(any(KeycloakSyncTask.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    doThrow(new ResourceNotFoundException("Tài khoản", "u1"))
        .when(keycloakAdminClient).setUserEnabled("u1", false);

    processor.process(task.getId());

    assertThat(task.getStatus()).isEqualTo(KeycloakSyncStatus.DONE);
  }

  @Test
  void process_failure_keepsPendingAndSchedulesRetry() {
    KeycloakSyncTask task = createTask();
    when(taskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));
    when(taskRepository.save(any(KeycloakSyncTask.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    doThrow(new BusinessRuleException("KC down")).when(keycloakAdminClient)
        .provisionUser("u1", "User One", "u1@hrms.vn", "EMPLOYEE");
    Instant before = Instant.now();

    processor.process(task.getId());

    assertThat(task.getStatus()).isEqualTo(KeycloakSyncStatus.PENDING);
    assertThat(task.getAttempts()).isEqualTo(1);
    assertThat(task.getLastError()).contains("KC down");
    assertThat(task.getNextAttemptAt()).isAfter(before);
  }

  @Test
  void process_lastAttempt_marksFailed() {
    KeycloakSyncTask task = createTask();
    task.setAttempts(KeycloakSyncTask.MAX_ATTEMPTS - 1);
    when(taskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));
    when(taskRepository.save(any(KeycloakSyncTask.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    doThrow(new BusinessRuleException("KC down")).when(keycloakAdminClient)
        .provisionUser(any(), any(), any(), any());

    processor.process(task.getId());

    assertThat(task.getStatus()).isEqualTo(KeycloakSyncStatus.FAILED);
    assertThat(task.getAttempts()).isEqualTo(KeycloakSyncTask.MAX_ATTEMPTS);
  }

  @Test
  void process_alreadyDone_skipsKeycloak() {
    KeycloakSyncTask task = createTask();
    task.setStatus(KeycloakSyncStatus.DONE);
    when(taskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

    processor.process(task.getId());

    verifyNoInteractions(keycloakAdminClient);
    verify(taskRepository, never()).save(any());
  }

  @Test
  void process_missingTask_doesNothing() {
    UUID taskId = UUID.randomUUID();
    when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.empty());

    processor.process(taskId);

    verifyNoInteractions(keycloakAdminClient);
    verify(taskRepository, never()).save(any());
  }

  private static KeycloakSyncTask createTask() {
    KeycloakSyncTask task = KeycloakSyncTask.createUser("u1", "User One", "u1@hrms.vn",
        "EMPLOYEE");
    task.setId(UUID.randomUUID());
    return task;
  }
}
