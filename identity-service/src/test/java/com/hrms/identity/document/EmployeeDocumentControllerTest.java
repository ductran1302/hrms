package com.hrms.identity.document;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.identity.config.SecurityConfig;
import com.hrms.identity.document.dto.EmployeeDocumentResponse;
import com.hrms.identity.employee.EmployeeService;
import com.hrms.identity.employee.EmployeeViewer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test RBAC cho API tài liệu: 401 thiếu token, 403 sai role, 201 đúng ADMIN. */
@WebMvcTest(controllers = EmployeeDocumentController.class)
@Import(SecurityConfig.class)
class EmployeeDocumentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private EmployeeDocumentService documentService;

  @MockBean
  private EmployeeService employeeService;

  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority MANAGER =
      new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  @TempDir
  private Path tempDir;

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
    when(documentService.list(eq(employeeId), org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(List.of(sampleResponse()));

    mockMvc.perform(get("/api/v1/employees/{id}/documents", employeeId)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fileName").value("cv.pdf"));
  }

  @Test
  void list_withManagerOutOfScope_returns403() throws Exception {
    when(employeeService.get(eq(employeeId), any(EmployeeViewer.class)))
        .thenThrow(new AccessDeniedException("Ngoài phạm vi"));

    mockMvc.perform(get("/api/v1/employees/{id}/documents", employeeId)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isForbidden());

    verify(documentService, never()).list(any(), any());
  }

  @Test
  void download_withManagerOutOfScope_returns403() throws Exception {
    when(employeeService.get(eq(employeeId), any(EmployeeViewer.class)))
        .thenThrow(new AccessDeniedException("Ngoài phạm vi"));

    mockMvc.perform(get("/api/v1/employees/{id}/documents/{docId}", employeeId, documentId)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isForbidden());

    verify(documentService, never()).get(any(), any());
  }

  @Test
  void download_withAdmin_fileMissing_returns404() throws Exception {
    EmployeeDocument document = new EmployeeDocument();
    document.setFileName("cv.pdf");
    when(documentService.get(eq(employeeId), eq(documentId))).thenReturn(document);
    when(documentService.resolvePath(document))
        .thenReturn(Path.of("missing-" + UUID.randomUUID() + ".pdf"));

    mockMvc.perform(get("/api/v1/employees/{id}/documents/{docId}", employeeId, documentId)
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isNotFound());
  }

  @Test
  void download_withAdmin_setsAttachmentAndNoSniffHeaders() throws Exception {
    Path file = tempDir.resolve("cv.pdf");
    Files.writeString(file, "pdf-bytes");
    EmployeeDocument document = new EmployeeDocument();
    document.setFileName("cv.pdf");
    document.setContentType("application/pdf");
    when(documentService.get(eq(employeeId), eq(documentId))).thenReturn(document);
    when(documentService.resolvePath(document)).thenReturn(file);

    mockMvc.perform(get("/api/v1/employees/{id}/documents/{docId}", employeeId, documentId)
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }

  @Test
  void upload_withManager_returns403() throws Exception {
    mockMvc.perform(multipart("/api/v1/employees/{id}/documents", employeeId)
            .file(FILE)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void upload_withAdmin_returns201() throws Exception {
    when(documentService.upload(eq(employeeId), any(), eq("CV"), eq("Hồ sơ ứng tuyển"),
        eq("user-1"), org.mockito.ArgumentMatchers.isNull())).thenReturn(sampleResponse());

    mockMvc.perform(multipart("/api/v1/employees/{id}/documents", employeeId)
            .file(FILE)
            .param("docType", "CV")
            .param("note", "Hồ sơ ứng tuyển")
            .with(jwt().authorities(ADMIN).jwt(j -> j.subject("user-1"))))
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
    return new EmployeeDocumentResponse(documentId, employeeId, null, "CV", "cv.pdf",
        "application/pdf", 9L, "Hồ sơ ứng tuyển", "user-1", Instant.now());
  }
}
