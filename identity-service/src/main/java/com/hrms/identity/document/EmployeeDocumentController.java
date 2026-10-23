package com.hrms.identity.document;

import com.hrms.common.security.EmployeeScope;
import com.hrms.identity.document.dto.EmployeeDocumentResponse;
import com.hrms.identity.employee.EmployeeService;
import com.hrms.identity.employee.EmployeeViewer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API tài liệu đính kèm — upload/xóa {@code ADMIN}, đọc (list/download)
 * {@code ADMIN} + {@code GLOBAL_LEAD} + {@code DEPT_LEAD} + {@code MANAGER}. File ≤ 10MB.
 *
 * <p>Đọc tài liệu áp dụng cùng scoping với hồ sơ nhân viên ({@link EmployeeScope}):
 * ADMIN/GLOBAL_LEAD toàn bộ; DEPT_LEAD phòng mình quản lý + trực thuộc + self;
 * MANAGER trực thuộc + self. Ngoài phạm vi → 403 (chống IDOR khi đoán employeeId/documentId).
 */
@Tag(name = "Employee Documents")
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/documents")
@RequiredArgsConstructor
public class EmployeeDocumentController {

  private final EmployeeDocumentService documentService;
  private final EmployeeService employeeService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Upload tài liệu (multipart: file, docType?, note?, contractId? — "
      + "kèm contractId = phụ lục hợp đồng, docType mặc định CONTRACT)")
  public EmployeeDocumentResponse upload(@PathVariable UUID employeeId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(name = "docType", required = false) String docType,
      @RequestParam(name = "note", required = false) String note,
      @RequestParam(name = "contractId", required = false) UUID contractId,
      @AuthenticationPrincipal Jwt jwt) {
    return documentService.upload(employeeId, file, docType, note, jwt.getSubject(), contractId);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Danh sách tài liệu của nhân viên (mới nhất trước; contractId? = lọc theo hợp đồng)")
  public List<EmployeeDocumentResponse> list(@PathVariable UUID employeeId,
      @RequestParam(name = "contractId", required = false) UUID contractId,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    employeeService.get(employeeId, viewer(jwt, authentication));
    return documentService.list(employeeId, contractId);
  }

  @GetMapping("/{documentId}")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Download tài liệu")
  public ResponseEntity<Resource> download(@PathVariable UUID employeeId,
      @PathVariable UUID documentId,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    employeeService.get(employeeId, viewer(jwt, authentication));
    EmployeeDocument document = documentService.get(employeeId, documentId);
    FileSystemResource resource = new FileSystemResource(
        documentService.resolvePath(document).toFile());
    if (!resource.exists()) {
      return ResponseEntity.notFound().build();
    }
    MediaType mediaType = document.getContentType() == null
        ? MediaType.APPLICATION_OCTET_STREAM
        : MediaType.parseMediaType(document.getContentType());
    String disposition = ContentDisposition.attachment()
        .filename(document.getFileName(), StandardCharsets.UTF_8)
        .build()
        .toString();
    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .header("X-Content-Type-Options", "nosniff")
        .body(resource);
  }

  @DeleteMapping("/{documentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Xóa tài liệu (xóa cả file trên đĩa)")
  public void delete(@PathVariable UUID employeeId, @PathVariable UUID documentId) {
    documentService.delete(employeeId, documentId);
  }

  /** Cùng nguồn scope với {@code EmployeeController} — authorities từ JWT. */
  private EmployeeViewer viewer(Jwt jwt, Authentication authentication) {
    return new EmployeeViewer(
        jwt.getClaimAsString("preferred_username"),
        jwt.getClaimAsString("email"),
        EmployeeScope.fromAuthorities(authentication.getAuthorities()));
  }
}
