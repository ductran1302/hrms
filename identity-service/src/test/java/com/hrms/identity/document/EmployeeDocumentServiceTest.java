package com.hrms.identity.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.identity.contract.ContractRepository;
import com.hrms.identity.document.dto.EmployeeDocumentResponse;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmployeeDocumentServiceTest {

  @Mock
  private EmployeeDocumentRepository documentRepository;
  @Mock
  private EmployeeRepository employeeRepository;
  @Mock
  private ContractRepository contractRepository;

  @Mock
  private AuditService auditService;

  @InjectMocks
  private EmployeeDocumentService service;

  @TempDir
  private Path documentsDir;

  private UUID employeeId;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "documentsDir", documentsDir.toString());
    employeeId = UUID.randomUUID();
    Employee employee = new Employee();
    employee.setId(employeeId);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
  }

  @Test
  void upload_validPdf_storesDetectedContentTypeAndFile() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "text/html",
        "%PDF-1.7\nnoi dung".getBytes(StandardCharsets.UTF_8));
    when(documentRepository.save(any(EmployeeDocument.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EmployeeDocumentResponse response =
        service.upload(employeeId, file, "CV", "note", "hr", null);

    ArgumentCaptor<EmployeeDocument> captor = ArgumentCaptor.forClass(EmployeeDocument.class);
    verify(documentRepository).save(captor.capture());
    EmployeeDocument saved = captor.getValue();
    assertThat(saved.getContentType()).isEqualTo("application/pdf");
    assertThat(saved.getFilePath()).endsWith(".pdf");
    assertThat(Files.exists(documentsDir.resolve(saved.getFilePath()))).isTrue();
    assertThat(Files.size(documentsDir.resolve(saved.getFilePath()))).isEqualTo(file.getSize());
    assertThat(response.fileName()).isEqualTo("cv.pdf");
  }

  @Test
  void upload_spoofedPdf_isRejectedBeforeWriting() {
    MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
        "<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> service.upload(employeeId, file, "CV", null, "hr", null))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("không khớp định dạng .pdf");

    verify(documentRepository, never()).save(any());
    assertThat(documentsDir.toFile().list()).isEmpty();
  }

  @Test
  void upload_sanitizesFileNameAndStripsControlCharacters() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "..\\dir/cv\r\n.pdf", "application/pdf",
        "%PDF-1.7\nx".getBytes(StandardCharsets.UTF_8));
    when(documentRepository.save(any(EmployeeDocument.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.upload(employeeId, file, "CV", null, "hr", null);

    ArgumentCaptor<EmployeeDocument> captor = ArgumentCaptor.forClass(EmployeeDocument.class);
    verify(documentRepository).save(captor.capture());
    String fileName = captor.getValue().getFileName();
    assertThat(fileName).doesNotContain("\r", "\n", "\\", "/", "\"");
    assertThat(fileName).endsWith(".pdf");
  }
}
