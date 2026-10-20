package com.hrms.identity.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.account.dto.AccountResponse;
import com.hrms.identity.account.dto.ChangePasswordRequest;
import com.hrms.identity.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test RBAC + validation cho màn Cài đặt: danh sách tài khoản chỉ ADMIN,
 * tự đổi mật khẩu cho mọi role (validate min 8 ký tự), admin reset/bật-tắt chỉ ADMIN.
 */
@WebMvcTest(controllers = AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AccountService accountService;

  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority MANAGER =
      new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  @Test
  void search_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/accounts"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void search_withEmployee_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/accounts")
            .with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
  }

  @Test
  void search_withAdmin_returns200() throws Exception {
    when(accountService.search("thieu", 0, 20)).thenReturn(
        PageResponse.of(List.of(
            new AccountResponse("nthieu", "Nguyễn Trung Hiếu", "a@hrms.vn", true,
                List.of("ADMIN"), 1_700_000_000_000L, "hr", "hr",
                "2026-01-01T00:00:00Z")), 0, 20, 1));

    mockMvc.perform(get("/api/v1/accounts").param("q", "thieu")
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].username").value("nthieu"))
        .andExpect(jsonPath("$.items[0].roles[0]").value("ADMIN"))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void changePassword_withEmployee_returns204() throws Exception {
    mockMvc.perform(post("/api/v1/accounts/change-password")
            .with(jwt().authorities(EMPLOYEE)
                .jwt(j -> j.claim("preferred_username", "employee1")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"old-123456\",\"newPassword\":\"new-123456\"}"))
        .andExpect(status().isNoContent());

    verify(accountService).changeOwnPassword(eq("employee1"), any(ChangePasswordRequest.class));
  }

  @Test
  void changePassword_wrongCurrentPassword_returns422() throws Exception {
    doThrow(new BusinessRuleException("Mật khẩu hiện tại không đúng"))
        .when(accountService).changeOwnPassword(eq("employee1"), any(ChangePasswordRequest.class));

    mockMvc.perform(post("/api/v1/accounts/change-password")
            .with(jwt().authorities(EMPLOYEE)
                .jwt(j -> j.claim("preferred_username", "employee1")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"sai\",\"newPassword\":\"new-123456\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void changePassword_shortNewPassword_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/accounts/change-password")
            .with(jwt().authorities(EMPLOYEE)
                .jwt(j -> j.claim("preferred_username", "employee1")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"old-123456\",\"newPassword\":\"1234567\"}"))
        .andExpect(status().isBadRequest());

    verify(accountService, never()).changeOwnPassword(any(), any());
  }

  @Test
  void resetPassword_withoutToken_returns401() throws Exception {
    mockMvc.perform(patch("/api/v1/accounts/nthieu/password")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newPassword\":\"new-123456\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void resetPassword_withManager_returns403() throws Exception {
    mockMvc.perform(patch("/api/v1/accounts/nthieu/password")
            .with(jwt().authorities(MANAGER))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newPassword\":\"new-123456\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void resetPassword_withAdmin_returns204() throws Exception {
    mockMvc.perform(patch("/api/v1/accounts/nthieu/password")
            .with(jwt().authorities(ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newPassword\":\"new-123456\"}"))
        .andExpect(status().isNoContent());

    verify(accountService).resetPassword("nthieu", "new-123456");
  }

  @Test
  void setEnabled_withAdmin_passesActorFromToken() throws Exception {
    mockMvc.perform(patch("/api/v1/accounts/nthieu/enabled")
            .with(jwt().authorities(ADMIN)
                .jwt(j -> j.claim("preferred_username", "hr")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isNoContent());

    verify(accountService).setEnabled("nthieu", false, "hr");
  }
}
