package com.hrms.attendance.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.attendance.config.SecurityConfig;
import com.hrms.attendance.schedule.dto.RosterEntryResponse;
import com.hrms.common.security.EmployeeScope;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RosterController.class)
@Import(SecurityConfig.class)
class RosterControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private RosterQueryService queryService;

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
    verify(queryService, never()).list(any(), any(), any(), any(), any(), any());
  }

  @Test
  void list_withManagerRole_returns200() throws Exception {
    when(queryService.list(isNull(), isNull(), isNull(), isNull(), isNull(),
            any(EmployeeScope.class)))
        .thenReturn(List.of());

    mockMvc.perform(get("/api/v1/attendance/rosters").with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk());
  }

  @Test
  void list_withEmployeeIds_passesBatchToService() throws Exception {
    when(queryService.list(isNull(), eq(List.of("e-1", "e-2")), isNull(), isNull(), isNull(),
            any(EmployeeScope.class)))
        .thenReturn(List.of());

    mockMvc.perform(get("/api/v1/attendance/rosters?employeeIds=e-1,e-2")
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk());
  }

  @Test
  void get_withAdmin_returnsResolvedDay() throws Exception {
    LocalDate date = LocalDate.now().plusDays(1);
    when(queryService.get(eq("e-1"), eq(date), any(), any(EmployeeScope.class)))
        .thenReturn(entry(date));

    mockMvc.perform(get("/api/v1/attendance/rosters/e-1/" + date)
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employeeId").value("e-1"))
        .andExpect(jsonPath("$.dayOff").value(true));
  }

  @Test
  void override_withAdmin_returns200() throws Exception {
    LocalDate date = LocalDate.now().plusDays(1);
    when(queryService.override(eq("e-1"), eq(date), isNull(), any(), any()))
        .thenReturn(entry(date));

    mockMvc.perform(put("/api/v1/attendance/rosters/e-1/" + date)
            .with(jwt().authorities(ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"shiftId\":null,\"note\":\"smoke\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("MANUAL_OVERRIDE"));
  }

  @Test
  void override_withManager_returns403() throws Exception {
    mockMvc.perform(put("/api/v1/attendance/rosters/e-1/2026-11-10")
            .with(jwt().authorities(MANAGER))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"shiftId\":null,\"note\":\"smoke\"}"))
        .andExpect(status().isForbidden());
    verify(queryService, never()).override(any(), any(), any(), any(), any());
  }

  @Test
  void clearOverride_withAdmin_returns200() throws Exception {
    LocalDate date = LocalDate.now().plusDays(1);
    when(queryService.clearOverride(eq("e-1"), eq(date), any())).thenReturn(entry(date));

    mockMvc.perform(delete("/api/v1/attendance/rosters/e-1/" + date)
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employeeId").value("e-1"));
  }

  private static RosterEntryResponse entry(LocalDate date) {
    return RosterEntryResponse.derived(
        "e-1", "NV001", "Nguyễn Văn A", date,
        new ScheduleResolver.DayPlan(null, true, RosterSource.MANUAL_OVERRIDE, null), null);
  }
}
