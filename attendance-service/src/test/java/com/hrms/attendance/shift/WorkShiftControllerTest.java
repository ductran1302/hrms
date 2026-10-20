package com.hrms.attendance.shift;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.attendance.config.SecurityConfig;
import com.hrms.attendance.shift.dto.WorkShiftRequest;
import com.hrms.attendance.shift.dto.WorkShiftResponse;
import java.time.LocalTime;
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

/** Slice test an ninh cho ca làm việc: 401 thiếu token, 403 sai role, 201 đúng role. */
@WebMvcTest(controllers = WorkShiftController.class)
@Import(SecurityConfig.class)
class WorkShiftControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private WorkShiftService workShiftService;

  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority MANAGER =
      new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/attendance/work-shifts"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withEmployeeRole_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/attendance/work-shifts").with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
    verify(workShiftService, never()).list(any(), any(), any());
  }

  @Test
  void list_withManagerRole_returns200() throws Exception {
    when(workShiftService.list(null, null, null)).thenReturn(List.of());

    mockMvc.perform(get("/api/v1/attendance/work-shifts").with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk());
  }

  @Test
  void create_withHrAdmin_returns201() throws Exception {
    WorkShiftResponse response = new WorkShiftResponse(
        UUID.randomUUID(), "Ca 8:00-17:00", "FIXED",
        LocalTime.of(8, 0), LocalTime.of(17, 0),
        LocalTime.of(12, 0), LocalTime.of(13, 0),
        480, 60, true, null, null);
    when(workShiftService.create(any(WorkShiftRequest.class))).thenReturn(response);

    mockMvc.perform(post("/api/v1/attendance/work-shifts")
                .with(jwt().authorities(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ca 8:00-17:00","shiftType":"FIXED",
                     "startWorkTime":"08:00:00","endWorkTime":"17:00:00",
                     "startBreakTime":"12:00:00","endBreakTime":"13:00:00",
                     "workingTime":480,"breakingTime":60}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Ca 8:00-17:00"));
  }

  @Test
  void create_withEmployeeRole_returns403() throws Exception {
    // Body phải hợp lệ (@Valid chạy trước @PreAuthorize khi parse @RequestBody)
    mockMvc.perform(post("/api/v1/attendance/work-shifts")
                .with(jwt().authorities(EMPLOYEE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ca x","shiftType":"FIXED",
                     "startWorkTime":"08:00:00","endWorkTime":"12:00:00",
                     "workingTime":240,"breakingTime":0}
                    """))
        .andExpect(status().isForbidden());
    verify(workShiftService, never()).create(any());
  }
}
