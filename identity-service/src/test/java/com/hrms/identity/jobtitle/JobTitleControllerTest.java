package com.hrms.identity.jobtitle;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.identity.config.SecurityConfig;
import com.hrms.identity.jobtitle.dto.JobTitleResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test RBAC cho API danh mục chức vụ: 401 thiếu token, 403 sai role, 200 HR/MANAGER. */
@WebMvcTest(controllers = JobTitleController.class)
@Import(SecurityConfig.class)
class JobTitleControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private JobTitleService jobTitleService;

  private static final SimpleGrantedAuthority HR_ADMIN =
      new SimpleGrantedAuthority("ROLE_HR_ADMIN");
  private static final SimpleGrantedAuthority MANAGER =
      new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/job-titles"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withEmployee_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/job-titles")
            .with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
  }

  @Test
  void list_withManager_returns200() throws Exception {
    when(jobTitleService.list()).thenReturn(List.of(sample()));
    mockMvc.perform(get("/api/v1/job-titles")
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("CTO"))
        .andExpect(jsonPath("$[0].name").value("Giám đốc Công nghệ"));
  }

  @Test
  void list_withHrAdmin_returns200() throws Exception {
    when(jobTitleService.list()).thenReturn(List.of(sample()));
    mockMvc.perform(get("/api/v1/job-titles")
            .with(jwt().authorities(HR_ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nameEn").value("Chief Technology Officer"))
        .andExpect(jsonPath("$[0].category").value("EXEC"));
  }

  private JobTitleResponse sample() {
    return new JobTitleResponse("CTO", "Giám đốc Công nghệ",
        "Chief Technology Officer", "EXEC", 90);
  }
}
