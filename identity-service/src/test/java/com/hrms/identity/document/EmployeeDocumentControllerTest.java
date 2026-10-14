package com.hrms.identity.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.identity.config.SecurityConfig;
import com.hrms.identity.document.dto.EmployeeDocumentResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test RBAC cho API tài liệu: 401 thiếu token, 403 sai role, 201 đúng HR_ADMIN. */
@WebMvcTest(controllers = EmployeeDocumentController.class)
@Import(SecurityConfig.class)
class EmployeeDocumentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private EmployeeDocumentService documentService;

  private static final SimpleGrantedAuthority HR_ADMIN =
      new SimpleGrantedAuthority("ROLE_HR_ADMIN");
  private static final SimpleGrantedAuthority MANAGER =
      new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  private final UUID employeeId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();

  private static final MockMultipartFile FILE =
      new MockMultipartFile("file", "cv.pdf", "application/pdf", "pdf-bytes".getBytes());

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/employees/{id}/documents", employeeId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withManager_returns200() throws Exception {
    when(documentService.list(eq(employeeId))).thenReturn(List.of(sampleResponse()));

    mockMvc.perform(get("/api/v1/employees/{id}/documents", employeeId)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fileName").value("cv.pdf"));
  }

  @Test
  void upload_withManager_returns403() throws Exception {
    mockMvc.perform(multipart("/api/v1/employees/{id}/documents", employeeId)
            .file(FILE)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void upload_withHrAdmin_returns201() throws Exception {
    when(documentService.upload(eq(employeeId), any(), eq("CV"), eq("Hồ sơ ứng tuyển"),
        eq("user-1"))).thenReturn(sampleResponse());

    mockMvc.perform(multipart("/api/v1/employees/{id}/documents", employeeId)
            .file(FILE)
            .param("docType", "CV")
            .param("note", "Hồ sơ ứng tuyển")
            .with(jwt().authorities(HR_ADMIN).jwt(j -> j.subject("user-1"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fileName").value("cv.pdf"));
  }

  @Test
  void delete_withEmployeeRole_returns403() throws Exception {
    mockMvc.perform(delete("/api/v1/employees/{id}/documents/{docId}", employeeId, documentId)
            .with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
  }

  private EmployeeDocumentResponse sampleResponse() {
    return new EmployeeDocumentResponse(documentId, employeeId, "CV", "cv.pdf",
        "application/pdf", 9L, "Hồ sơ ứng tuyển", "user-1", Instant.now());
  }
}
