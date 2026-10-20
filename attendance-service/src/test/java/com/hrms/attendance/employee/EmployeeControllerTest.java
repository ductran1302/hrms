package com.hrms.attendance.employee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.attendance.config.SecurityConfig;
import com.hrms.common.security.EmployeeScope;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test an ninh: 401 thiếu token, 403 sai role, 200 đúng role. Dùng jwt() post-processor
 * nên không cần Keycloak thật.
 */
@WebMvcTest(controllers = EmployeeController.class)
@Import(SecurityConfig.class)
class EmployeeControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private EmployeeService employeeService;

  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority MANAGER =
      new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/attendance/employees"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withHrAdmin_returns200() throws Exception {
    Employee employee = new Employee();
    employee.setEmployeeId("4600e356-3e5e-4268-b6e7-e1113bf9f357");
    employee.setEmployeeCode("NV0001");
    employee.setFullName("Nguyễn Văn A");
    employee.setStatus("ACTIVE");
    employee.setCreatedAt(Instant.now());
    employee.setUpdatedAt(Instant.now());
    when(employeeService.list(isNull(), eq(EmployeeScope.ALL), any()))
        .thenReturn(List.of(employee));

    mockMvc.perform(get("/api/v1/attendance/employees").with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].employeeCode").value("NV0001"));
  }

  @Test
  void list_withManagerRole_returns200() throws Exception {
    when(employeeService.list(eq("ACTIVE"), eq(EmployeeScope.SELF), any()))
        .thenReturn(List.of());

    mockMvc.perform(get("/api/v1/attendance/employees")
            .param("status", "ACTIVE")
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk());

    verify(employeeService).list(eq("ACTIVE"), eq(EmployeeScope.SELF), any());
  }

  @Test
  void list_withEmployeeRole_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/attendance/employees").with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
    verify(employeeService, never()).list(any(), any(), any());
  }

  @Test
  void getByCode_withHrAdmin_returns200() throws Exception {
    Employee employee = new Employee();
    employee.setEmployeeId("4600e356-3e5e-4268-b6e7-e1113bf9f357");
    employee.setEmployeeCode("NV0001");
    employee.setFullName("Nguyễn Văn A");
    employee.setStatus("ACTIVE");
    when(employeeService.getByCode(eq("NV0001"), eq(EmployeeScope.ALL), any()))
        .thenReturn(employee);

    mockMvc.perform(get("/api/v1/attendance/employees/NV0001").with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employeeCode").value("NV0001"));
  }
}
