package com.hrms.attendance.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.attendance.config.SecurityConfig;
import com.hrms.attendance.schedule.dto.WorkSchedulePolicyRequest;
import com.hrms.attendance.schedule.dto.WorkSchedulePolicyResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WorkSchedulePolicyController.class)
@Import(SecurityConfig.class)
class WorkSchedulePolicyControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private WorkSchedulePolicyService policyService;

  private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority MANAGER = new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE = new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  private static WorkSchedulePolicyResponse response() {
    return new WorkSchedulePolicyResponse(
        UUID.randomUUID(), "Chuẩn (nghỉ Thứ 7)", null,
        null, null, null, null, null, null, null, null, null,
        true, true, Map.of(), null, null, 0L);
  }

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/attendance/schedule-policies"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withEmployeeRole_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/attendance/schedule-policies")
            .with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
    verify(policyService, never()).list(any());
  }

  @Test
  void list_withManagerRole_returns200() throws Exception {
    when(policyService.list(isNull())).thenReturn(List.of(response()));

    mockMvc.perform(get("/api/v1/attendance/schedule-policies")
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Chuẩn (nghỉ Thứ 7)"));
  }

  @Test
  void create_withAdmin_returns201() throws Exception {
    when(policyService.create(any(WorkSchedulePolicyRequest.class), isNull()))
        .thenReturn(response());

    mockMvc.perform(post("/api/v1/attendance/schedule-policies")
            .with(jwt().authorities(ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Chuẩn (nghỉ Thứ 7)","defaultPolicy":true,"active":true}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.defaultPolicy").value(true));
  }

  @Test
  void create_withManager_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/attendance/schedule-policies")
            .with(jwt().authorities(MANAGER))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Chuẩn","defaultPolicy":false,"active":true}
                """))
        .andExpect(status().isForbidden());
    verify(policyService, never()).create(any(), any());
  }
}
