package com.hrms.identity.department;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.common.web.PageResponse;
import com.hrms.identity.config.SecurityConfig;
import com.hrms.identity.department.dto.DepartmentResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DepartmentController.class)
@Import(SecurityConfig.class)
class DepartmentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private DepartmentService departmentService;

  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/departments"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withAdmin_returnsPageResponse() throws Exception {
    when(departmentService.list(0, 20))
        .thenReturn(PageResponse.of(List.of(sampleResponse()), 0, 20, 1));

    mockMvc.perform(get("/api/v1/departments").with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].code").value("IT"))
        .andExpect(jsonPath("$.items[0].employeeCount").value(5))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void list_withEmployeeRole_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/departments").with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());

    verify(departmentService, never()).list(anyInt(), anyInt());
  }

  private DepartmentResponse sampleResponse() {
    return new DepartmentResponse(
        UUID.randomUUID(), "IT", "IT", null, null, null, "ACTIVE", 5);
  }
}
