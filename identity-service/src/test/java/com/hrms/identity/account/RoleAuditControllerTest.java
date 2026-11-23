package com.hrms.identity.account;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.identity.account.dto.RoleAuditResponse;
import com.hrms.identity.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RoleAuditController.class)
@Import(SecurityConfig.class)
class RoleAuditControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private RoleAuditService roleAuditService;

  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority MANAGER =
      new SimpleGrantedAuthority("ROLE_MANAGER");

  @Test
  void audit_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/accounts/role-audit"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void audit_withAdmin_returnsReport() throws Exception {
    when(roleAuditService.audit(null, false)).thenReturn(emptyResponse());

    mockMvc.perform(get("/api/v1/accounts/role-audit").with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(false))
        .andExpect(jsonPath("$.scanned").value(0))
        .andExpect(jsonPath("$.items").isArray());
  }

  @Test
  void audit_withManager_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/accounts/role-audit").with(jwt().authorities(MANAGER)))
        .andExpect(status().isForbidden());

    verify(roleAuditService, never()).audit(null, false);
  }

  @Test
  void apply_withAdmin_returnsAppliedReport() throws Exception {
    when(roleAuditService.audit("emp1", true)).thenReturn(
        new RoleAuditResponse(true, 1, 1, 0, 1, 0, 0, 0, 0, List.of()));

    mockMvc.perform(post("/api/v1/accounts/role-audit/apply")
            .param("q", "emp1")
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(true))
        .andExpect(jsonPath("$.corrected").value(1));

    verify(roleAuditService).audit("emp1", true);
  }

  private static RoleAuditResponse emptyResponse() {
    return new RoleAuditResponse(false, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
  }
}
