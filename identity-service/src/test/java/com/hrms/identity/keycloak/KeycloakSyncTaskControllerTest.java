package com.hrms.identity.keycloak;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.config.SecurityConfig;
import com.hrms.identity.keycloak.dto.KeycloakSyncTaskResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test RBAC cho màn vận hành Keycloak sync tasks: chỉ ADMIN. */
@WebMvcTest(controllers = KeycloakSyncTaskController.class)
@Import(SecurityConfig.class)
class KeycloakSyncTaskControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private KeycloakSyncTaskService taskService;

  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  private final UUID taskId = UUID.randomUUID();

  private static KeycloakSyncTaskResponse sample(String status) {
    return new KeycloakSyncTaskResponse(UUID.randomUUID(), "CREATE_USER", "nthieu", "Nguyễn",
        "a@hrms.vn", "EMPLOYEE", null, status, status.equals("FAILED") ? 5 : 0,
        Instant.parse("2026-10-23T00:00:00Z"), status.equals("FAILED") ? "KC down" : null,
        Instant.parse("2026-10-23T00:00:00Z"), Instant.parse("2026-10-23T00:00:00Z"));
  }

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/accounts/sync-tasks"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withEmployee_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/accounts/sync-tasks")
            .with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
  }

  @Test
  void list_withAdmin_returnsItems() throws Exception {
    when(taskService.search(eq(KeycloakSyncStatus.FAILED), eq(0), eq(20)))
        .thenReturn(PageResponse.of(List.of(sample("FAILED")), 0, 20, 1));

    mockMvc.perform(get("/api/v1/accounts/sync-tasks").param("status", "FAILED")
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].status").value("FAILED"))
        .andExpect(jsonPath("$.items[0].lastError").value("KC down"))
        .andExpect(jsonPath("$.items[0].attempts").value(5))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void retry_withEmployee_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/accounts/sync-tasks/{id}/retry", taskId)
            .with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
  }

  @Test
  void retry_withAdmin_returns202() throws Exception {
    when(taskService.retry(taskId)).thenReturn(sample("PENDING"));

    mockMvc.perform(post("/api/v1/accounts/sync-tasks/{id}/retry", taskId)
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void retry_notFailed_returns422() throws Exception {
    doThrow(new BusinessRuleException("Chỉ retry được task FAILED")).when(taskService)
        .retry(taskId);

    mockMvc.perform(post("/api/v1/accounts/sync-tasks/{id}/retry", taskId)
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void list_invalidStatus_returns400() throws Exception {
    mockMvc.perform(get("/api/v1/accounts/sync-tasks").param("status", "SAI")
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isBadRequest());

    verify(taskService, never()).search(any(), anyInt(), anyInt());
  }
}
