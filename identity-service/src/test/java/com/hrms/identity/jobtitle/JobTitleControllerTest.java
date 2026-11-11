package com.hrms.identity.jobtitle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.identity.config.SecurityConfig;
import com.hrms.identity.jobtitle.dto.JobTitleResponse;
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

/** Slice test RBAC cho API danh mục chức vụ: 401 thiếu token, 403 sai role, 200/201/204 đúng role. */
@WebMvcTest(controllers = JobTitleController.class)
@Import(SecurityConfig.class)
class JobTitleControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private JobTitleService jobTitleService;

  private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
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
        .andExpect(jsonPath("$[0].id").value(ID.toString()))
        .andExpect(jsonPath("$[0].code").value("CTO"))
        .andExpect(jsonPath("$[0].name").value("Giám đốc Công nghệ"));
  }

  @Test
  void list_withAdmin_returns200() throws Exception {
    when(jobTitleService.list()).thenReturn(List.of(sample()));
    mockMvc.perform(get("/api/v1/job-titles")
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nameEn").value("Chief Technology Officer"))
        .andExpect(jsonPath("$[0].category").value("EXEC"));
  }

  @Test
  void get_withManager_returns200() throws Exception {
    when(jobTitleService.get(ID)).thenReturn(sample());
    mockMvc.perform(get("/api/v1/job-titles/{id}", ID)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CTO"));
  }

  @Test
  void create_withoutToken_returns401() throws Exception {
    mockMvc.perform(post("/api/v1/job-titles")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void create_withManager_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/job-titles")
            .with(jwt().authorities(MANAGER))
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  void create_withAdmin_returns201() throws Exception {
    when(jobTitleService.create(any())).thenReturn(sample());
    mockMvc.perform(post("/api/v1/job-titles")
            .with(jwt().authorities(ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.code").value("CTO"));
  }

  @Test
  void create_withBlankCode_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/job-titles")
            .with(jwt().authorities(ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"code":"","name":"Giám đốc Công nghệ",
                 "nameEn":"Chief Technology Officer","category":"EXEC","sortOrder":90}
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void update_withManager_returns403() throws Exception {
    mockMvc.perform(patch("/api/v1/job-titles/{id}", ID)
            .with(jwt().authorities(MANAGER))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sortOrder\":100}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void update_withAdmin_returns200() throws Exception {
    when(jobTitleService.update(eq(ID), any())).thenReturn(sample());
    mockMvc.perform(patch("/api/v1/job-titles/{id}", ID)
            .with(jwt().authorities(ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sortOrder\":100}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("CTO"));
  }

  @Test
  void delete_withManager_returns403() throws Exception {
    mockMvc.perform(delete("/api/v1/job-titles/{id}", ID)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void delete_withAdmin_returns204() throws Exception {
    mockMvc.perform(delete("/api/v1/job-titles/{id}", ID)
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isNoContent());
  }

  private static String createBody() {
    return """
        {"code":"CTO","name":"Giám đốc Công nghệ",
         "nameEn":"Chief Technology Officer","category":"EXEC","sortOrder":90}
        """;
  }

  private JobTitleResponse sample() {
    return new JobTitleResponse(ID, "CTO", "Giám đốc Công nghệ",
        "Chief Technology Officer", "EXEC", 90);
  }
}
