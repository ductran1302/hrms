package com.hrms.identity.document;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.identity.contract.Contract;
import com.hrms.identity.contract.ContractRepository;
import com.hrms.identity.document.dto.EmployeeDocumentResponse;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tài liệu đính kèm hồ sơ — file lưu trên đĩa ({@code hrms.storage.documents-dir}),
 * DB chỉ giữ metadata. Tên file trên đĩa được sinh UUID (không dùng tên gốc) để tránh
 * path traversal; tên gốc chỉ dùng khi download (Content-Disposition).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeDocumentService {

  private final EmployeeDocumentRepository documentRepository;
  private final EmployeeRepository employeeRepository;
  private final ContractRepository contractRepository;

  @Value("${hrms.storage.documents-dir:data/documents}")
  private String documentsDir;

  @Transactional
  public EmployeeDocumentResponse upload(UUID employeeId, MultipartFile file, String docType,
      String note, String actorId, UUID contractId) {
    Employee employee = employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    // Validate nội dung trước khi ghi đĩa: whitelist + magic bytes (FE có thể bị bypass).
    DocumentContentValidator.ValidatedDocument validated = DocumentContentValidator.validate(file);
    // Phụ lục hợp đồng: hợp đồng phải tồn tại và thuộc đúng nhân viên.
    if (contractId != null) {
      Contract contract = contractRepository.findById(contractId)
          .orElseThrow(() -> new ResourceNotFoundException("Contract", contractId));
      if (!employee.getId().equals(contract.getEmployeeId())) {
        throw new BusinessRuleException("Hợp đồng không thuộc nhân viên này");
      }
    }
    // Upload kèm hợp đồng mà không khai báo loại → mặc định CONTRACT.
    String type = validateDocType(
        (docType == null || docType.isBlank()) && contractId != null
            ? EmployeeDocumentType.CONTRACT.name()
            : docType);

    String relativePath = employeeId + "/" + UUID.randomUUID() + "." + validated.extension();
    Path target = Path.of(documentsDir, relativePath);
    try {
      Files.createDirectories(target.getParent());
      Files.copy(file.getInputStream(), target);
    } catch (IOException ex) {
      throw new UncheckedIOException("Không ghi được file: " + ex.getMessage(), ex);
    }

    EmployeeDocument document = new EmployeeDocument();
    document.setEmployeeId(employee.getId());
    document.setContractId(contractId);
    document.setDocType(type);
    document.setFileName(originalName(file));
    document.setContentType(validated.contentType());
    document.setFileSize(file.getSize());
    document.setFilePath(relativePath);
    document.setNote(note);
    document.setUploadedBy(actorId);
    document.setUploadedAt(Instant.now());
    documentRepository.save(document);
    log.info("Uploaded document {} for employee {} (contract={}) by {}", document.getId(),
        employeeId, contractId, actorId);
    return toResponse(document);
  }

  @Transactional(readOnly = true)
  public List<EmployeeDocumentResponse> list(UUID employeeId, UUID contractId) {
    ensureEmployeeExists(employeeId);
    List<EmployeeDocument> documents = contractId == null
        ? documentRepository.findByEmployeeIdOrderByUploadedAtDesc(employeeId)
        : documentRepository.findByEmployeeIdAndContractIdOrderByUploadedAtDesc(employeeId,
            contractId);
    return documents.stream()
        .map(EmployeeDocumentService::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public EmployeeDocument get(UUID employeeId, UUID documentId) {
    ensureEmployeeExists(employeeId);
    return documentRepository.findByIdAndEmployeeId(documentId, employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("EmployeeDocument", documentId));
  }

  public Path resolvePath(EmployeeDocument document) {
    return Path.of(documentsDir, document.getFilePath());
  }

  @Transactional
  public void delete(UUID employeeId, UUID documentId) {
    EmployeeDocument document = get(employeeId, documentId);
    try {
      Files.deleteIfExists(resolvePath(document));
    } catch (IOException ex) {
      log.warn("Không xóa được file {}: {}", document.getFilePath(), ex.getMessage());
    }
    documentRepository.delete(document);
  }

  private void ensureEmployeeExists(UUID employeeId) {
    if (!employeeRepository.existsById(employeeId)) {
      throw new ResourceNotFoundException("Employee", employeeId);
    }
  }

  private String validateDocType(String docType) {
    String value = (docType == null || docType.isBlank())
        ? EmployeeDocumentType.OTHER.name()
        : docType.trim().toUpperCase(Locale.ROOT);
    try {
      return EmployeeDocumentType.valueOf(value).name();
    } catch (IllegalArgumentException ex) {
      throw new BusinessRuleException(
          "Loại tài liệu không hợp lệ (CV/CERTIFICATE/ID_CARD/CONTRACT/MEDICAL/OTHER): "
              + docType);
    }
  }

  private static String originalName(MultipartFile file) {
    String name = file.getOriginalFilename();
    if (name == null || name.isBlank()) {
      return "file";
    }
    // Chặn path traversal + CR/LF (header injection) và ký tự điều khiển.
    String sanitized = name.replaceAll("[\\p{Cntrl}]", "_")
        .replace("/", "_")
        .replace("\\", "_")
        .replace("\"", "_")
        .trim();
    return sanitized.isBlank() ? "file" : sanitized;
  }

  static EmployeeDocumentResponse toResponse(EmployeeDocument document) {
    return new EmployeeDocumentResponse(
        document.getId(),
        document.getEmployeeId(),
        document.getContractId(),
        document.getDocType(),
        document.getFileName(),
        document.getContentType(),
        document.getFileSize(),
        document.getNote(),
        document.getUploadedBy(),
        document.getUploadedAt());
  }
}
