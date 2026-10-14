package com.hrms.identity.employee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.common.web.PageResponse;
import com.hrms.identity.config.SecurityConfig;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
import com.hrms.identity.history.JobHistoryService;
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
 * Slice test an ninh: 401 khi thiếu token, 403 khi sai role, 201 khi đúng HR_ADMIN.
 * Dùng {@code jwt()} post-processor nên không cần Keycloak thật.
 */
@WebMvcTest(controllers = EmployeeController.class)
@Import(SecurityConfig.class)
class EmployeeControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private EmployeeService employeeService;

  @MockBean
  private JobHistoryService jobHistoryService;

  private static final SimpleGrantedAuthority HR_ADMIN =
      new SimpleGrantedAuthority("ROLE_HR_ADMIN");
  private static final SimpleGrantedAuthority MANAGER =
      new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  private static final String CREATE_BODY = """
      {"employeeCode":"NV0010","fullName":"Trần Thị B","email":"b@hrms.vn","position":"QA"}
      """;

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/employees"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withHrAdmin_returns200PageResponse() throws Exception {
    when(employeeService.search(anyInt(), anyInt(), isNull(), isNull(), isNull(), isNull()))
        .thenReturn(PageResponse.of(List.of(sampleResponse()), 0, 20, 1));

    mockMvc.perform(get("/api/v1/employees").with(jwt().authorities(HR_ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].employeeCode").value("NV0001"))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void list_withEmployeeRole_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/employees").with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
    verify(employeeService, never()).search(anyInt(), anyInt(), any(), any(), any(), any());
  }

  @Test
  void create_withHrAdmin_returns201() throws Exception {
    when(employeeService.create(any(EmployeeCreateRequest.class), eq("user-1")))
        .thenReturn(sampleResponse());

    mockMvc.perform(post("/api/v1/employees")
            .with(jwt().authorities(HR_ADMIN).jwt(j -> j.subject("user-1")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(CREATE_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.employeeCode").value("NV0001"));
  }

  @Test
  void create_withManagerRole_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/employees")
            .with(jwt().authorities(MANAGER))
            .contentType(MediaType.APPLICATION_JSON)
            .content(CREATE_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  void create_invalidBody_returns400WithCode() throws Exception {
    mockMvc.perform(post("/api/v1/employees")
            .with(jwt().authorities(HR_ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"employeeCode\":\"NV0011\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void update_withHrAdmin_returns200() throws Exception {
    when(employeeService.update(any(UUID.class), any(EmployeeUpdateRequest.class),
        eq("user-1"))).thenReturn(sampleResponse());

    mockMvc.perform(patch("/api/v1/employees/{id}", UUID.randomUUID())
            .with(jwt().authorities(HR_ADMIN).jwt(j -> j.subject("user-1")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\":\"Senior QA\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void history_withManager_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc.perform(get("/api/v1/employees/{id}/history", id)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk());
    verify(jobHistoryService).listByEmployee(id);
  }

  @Test
  void terminate_withHrAdmin_returns204() throws Exception {
    mockMvc.perform(delete("/api/v1/employees/{id}", UUID.randomUUID())
            .with(jwt().authorities(HR_ADMIN).jwt(j -> j.subject("user-1")))
            .param("reason", "Tự nguyện"))
        .andExpect(status().isNoContent());

    verify(employeeService).terminate(any(UUID.class), eq("user-1"), eq("Tự nguyện"));
  }

  private static EmployeeResponse sampleResponse() {
    return new EmployeeResponse(
        UUID.randomUUID(), "NV0001", "Nguyễn Văn A", "a@hrms.vn",
        null, "Engineering", "Backend", null,
        EmployeeStatus.ACTIVE, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, 0L);
  }
}
