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
import com.hrms.attendance.schedule.dto.GenerateRosterResponse;
import com.hrms.common.security.EmployeeScope;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RosterController.class)
@Import(SecurityConfig.class)
class RosterControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private RosterQueryService queryService;
  @MockBean private RosterGenerationService generationService;

  private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority MANAGER = new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE = new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/attendance/rosters"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withEmployeeRole_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/attendance/rosters").with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
    verify(queryService, never()).list(any(), any(), any(), any(), any());
  }

  @Test
  void list_withManagerRole_returns200() throws Exception {
    when(queryService.list(isNull(), isNull(), isNull(), isNull(), any(EmployeeScope.class)))
        .thenReturn(List.of());

    mockMvc.perform(get("/api/v1/attendance/rosters").with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk());
  }

  @Test
  void generate_withAdmin_returns200() throws Exception {
    LocalDate from = LocalDate.now();
    LocalDate to = from.plusDays(7);
    when(generationService.generate(isNull(), isNull(), isNull(), isNull()))
        .thenReturn(new GenerateRosterResponse(from, to, 1, 7, 0, 0, 0, 0));

    mockMvc.perform(post("/api/v1/attendance/rosters/generate")
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(7));
  }

  @Test
  void generate_withManager_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/attendance/rosters/generate")
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isForbidden());
    verify(generationService, never()).generate(any(), any(), any(), any());
  }
}
