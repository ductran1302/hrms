package com.hrms.identity.document;

import com.hrms.identity.document.dto.EmployeeDocumentResponse;
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
 * API tài liệu đính kèm — upload/xóa {@code HR_ADMIN}, đọc (list/download)
 * {@code HR_ADMIN} + {@code MANAGER}. File ≤ 10MB.
 */
@Tag(name = "Employee Documents")
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/documents")
@RequiredArgsConstructor
public class EmployeeDocumentController {

  private final EmployeeDocumentService documentService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('HR_ADMIN')")
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
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Danh sách tài liệu của nhân viên (mới nhất trước; contractId? = lọc theo hợp đồng)")
  public List<EmployeeDocumentResponse> list(@PathVariable UUID employeeId,
      @RequestParam(name = "contractId", required = false) UUID contractId) {
    return documentService.list(employeeId, contractId);
  }

  @GetMapping("/{documentId}")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Download tài liệu")
  public ResponseEntity<Resource> download(@PathVariable UUID employeeId,
      @PathVariable UUID documentId) {
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
        .body(resource);
  }

  @DeleteMapping("/{documentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Xóa tài liệu (xóa cả file trên đĩa)")
  public void delete(@PathVariable UUID employeeId, @PathVariable UUID documentId) {
    documentService.delete(employeeId, documentId);
  }
}
