package com.hrms.attendance.leave;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.attendance.config.SecurityConfig;
import com.hrms.attendance.leave.dto.LeavePolicyResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test an ninh workflow chính sách phép: approve/reject = ADMIN hoặc GLOBAL_LEAD
 * (duyệt cuối), tạo/sửa/activate/cancel/delete = ADMIN.
 */
@WebMvcTest(controllers = LeavePolicyController.class)
@Import(SecurityConfig.class)
class LeavePolicyControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private LeavePolicyService policyService;

  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority GLOBAL_LEAD =
      new SimpleGrantedAuthority("ROLE_GLOBAL_LEAD");
  private static final SimpleGrantedAuthority DEPT_LEAD =
      new SimpleGrantedAuthority("ROLE_DEPT_LEAD");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

  private LeavePolicyResponse response() {
    return new LeavePolicyResponse(
        ID, 2099, 1, PolicyStatus.PENDING_APPROVAL,
        null, null, BigDecimal.valueOf(12), 1, BigDecimal.ONE,
        BigDecimal.valueOf(5), BigDecimal.valueOf(5),
        "hr", null, null, null, null, List.of(),
        SeniorityRuleType.VN_TIERED, List.of());
  }

  @Test
  void approve_withoutToken_returns401() throws Exception {
    mockMvc.perform(post("/api/v1/attendance/leave/policies/versions/{id}/approve", ID)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
    verify(policyService, never()).approve(any(), any());
  }

  @Test
  void approve_withGlobalLead_returns200() throws Exception {
    when(policyService.approve(eq(ID), any())).thenReturn(response());

    mockMvc.perform(post("/api/v1/attendance/leave/policies/versions/{id}/approve", ID)
            .with(jwt().authorities(GLOBAL_LEAD)))
        .andExpect(status().isOk());

    verify(policyService).approve(eq(ID), any());
  }

  @Test
  void approve_withDeptLead_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/attendance/leave/policies/versions/{id}/approve", ID)
            .with(jwt().authorities(DEPT_LEAD)))
        .andExpect(status().isForbidden());
    verify(policyService, never()).approve(any(), any());
  }

  @Test
  void reject_withGlobalLead_returns200() throws Exception {
    when(policyService.reject(eq(ID), any())).thenReturn(response());

    mockMvc.perform(post("/api/v1/attendance/leave/policies/versions/{id}/reject", ID)
            .with(jwt().authorities(GLOBAL_LEAD)))
        .andExpect(status().isOk());

    verify(policyService).reject(eq(ID), any());
  }

  @Test
  void reject_withEmployee_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/attendance/leave/policies/versions/{id}/reject", ID)
            .with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
    verify(policyService, never()).reject(any(), any());
  }

  @Test
  void create_withGlobalLead_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/attendance/leave/policies")
            .with(jwt().authorities(GLOBAL_LEAD))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"year\":2099,\"baseDays\":12,\"seniorityStepYears\":1,"
                + "\"seniorityBonusDays\":1,\"seniorityMaxBonus\":5,\"carryoverMax\":5,"
                + "\"roleQuotas\":[]}"))
        .andExpect(status().isForbidden());
    verify(policyService, never()).createVersion(any(), any());
  }
}
