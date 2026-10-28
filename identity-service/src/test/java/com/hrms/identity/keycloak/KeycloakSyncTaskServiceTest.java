package com.hrms.identity.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.keycloak.dto.KeycloakSyncTaskResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class KeycloakSyncTaskServiceTest {

  @Mock
  private KeycloakSyncTaskRepository taskRepository;
  @Mock
  private KeycloakSyncProcessor processor;

  @Mock
  private AuditService auditService;

  @InjectMocks
  private KeycloakSyncTaskService service;

  private final UUID taskId = UUID.randomUUID();

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private KeycloakSyncTask task(KeycloakSyncStatus status) {
    KeycloakSyncTask task = KeycloakSyncTask.createUser("u1", "Nguyễn Văn A", "a@hrms.vn",
        "EMPLOYEE");
    task.setId(taskId);
    task.setStatus(status);
    return task;
  }

  @Test
  void search_capsPageSizeAndMapsResponse() {
    KeycloakSyncTask task = task(KeycloakSyncStatus.DONE);
    when(taskRepository.search(isNull(), any(Pageable.class))).thenReturn(List.of(task));
    when(taskRepository.countSearch(isNull())).thenReturn(1L);

    PageResponse<KeycloakSyncTaskResponse> page = service.search(null, -5, 1000);

    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(100);
    assertThat(page.total()).isEqualTo(1L);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).status()).isEqualTo("DONE");
    assertThat(page.items().get(0).operation()).isEqualTo("CREATE_USER");

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(taskRepository).search(isNull(), captor.capture());
    assertThat(captor.getValue().getPageNumber()).isZero();
    assertThat(captor.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  void search_withStatus_passesFilterToRepository() {
    when(taskRepository.search(eq(KeycloakSyncStatus.FAILED), any(Pageable.class)))
        .thenReturn(List.of());
    when(taskRepository.countSearch(eq(KeycloakSyncStatus.FAILED))).thenReturn(0L);

    service.search(KeycloakSyncStatus.FAILED, 0, 20);

    verify(taskRepository).search(eq(KeycloakSyncStatus.FAILED), any(Pageable.class));
    verify(taskRepository).countSearch(eq(KeycloakSyncStatus.FAILED));
  }

  @Test
  void retry_failedTask_resetsAndProcessesAfterCommit() {
    KeycloakSyncTask task = task(KeycloakSyncStatus.FAILED);
    task.setAttempts(KeycloakSyncTask.MAX_ATTEMPTS);
    task.setLastError("KC down");
    when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

    TransactionSynchronizationManager.initSynchronization();
    KeycloakSyncTaskResponse response = service.retry(taskId);

    assertThat(response.status()).isEqualTo("PENDING");
    assertThat(task.getStatus()).isEqualTo(KeycloakSyncStatus.PENDING);
    assertThat(task.getAttempts()).isZero();
    assertThat(task.getLastError()).isNull();
    verify(processor, never()).process(any());

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(processor).process(taskId);
  }

  @Test
  void retry_notFailedTask_returns422() {
    when(taskRepository.findByIdForUpdate(taskId))
        .thenReturn(Optional.of(task(KeycloakSyncStatus.DONE)));

    assertThatThrownBy(() -> service.retry(taskId))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("FAILED");
    verify(processor, never()).process(any());
  }

  @Test
  void retry_missingTask_returns404() {
    when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.retry(taskId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void retry_processorFails_doesNotPropagate() {
    when(taskRepository.findByIdForUpdate(taskId))
        .thenReturn(Optional.of(task(KeycloakSyncStatus.FAILED)));
    doThrow(new RuntimeException("KC vẫn down")).when(processor).process(taskId);

    TransactionSynchronizationManager.initSynchronization();
    service.retry(taskId);

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(processor).process(taskId);
  }

  @Test
  void purgeDoneBefore_delegatesToRepository() {
    Instant cutoff = Instant.parse("2026-09-01T00:00:00Z");
    when(taskRepository.deleteByStatusAndUpdatedAtBefore(KeycloakSyncStatus.DONE, cutoff))
        .thenReturn(4);

    assertThat(service.purgeDoneBefore(cutoff)).isEqualTo(4);
  }
}
