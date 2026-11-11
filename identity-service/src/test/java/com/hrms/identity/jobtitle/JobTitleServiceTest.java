package com.hrms.identity.jobtitle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.jobtitle.dto.JobTitleCreateRequest;
import com.hrms.identity.jobtitle.dto.JobTitleResponse;
import com.hrms.identity.jobtitle.dto.JobTitleUpdateRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobTitleServiceTest {

  @Mock
  private JobTitleRepository jobTitleRepository;

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private AuditService auditService;

  @InjectMocks
  private JobTitleService service;

  private final UUID id = UUID.randomUUID();

  private JobTitle existing() {
    JobTitle jobTitle = new JobTitle();
    jobTitle.setId(id);
    jobTitle.setCode("CTO");
    jobTitle.setName("Giám đốc Công nghệ");
    jobTitle.setNameEn("Chief Technology Officer");
    jobTitle.setCategory("EXEC");
    jobTitle.setSortOrder(90);
    return jobTitle;
  }

  @Test
  void create_withValidData_normalizesCodeAndSaves() {
    when(jobTitleRepository.existsByCode("CTO")).thenReturn(false);
    when(jobTitleRepository.existsByName("Giám đốc Công nghệ")).thenReturn(false);
    when(jobTitleRepository.save(any(JobTitle.class))).thenAnswer(inv -> {
      JobTitle saved = inv.getArgument(0);
      saved.setId(id);
      return saved;
    });

    JobTitleResponse response = service.create(new JobTitleCreateRequest(
        " cto ", "Giám đốc Công nghệ", "Chief Technology Officer", "EXEC", 90));

    assertEquals(id, response.id());
    assertEquals("CTO", response.code());
    verify(auditService).success("JOB_TITLE_CREATE", "JobTitle", id, "code=CTO");
  }

  @Test
  void create_withDuplicateCode_throwsConflict() {
    when(jobTitleRepository.existsByCode("CTO")).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.create(new JobTitleCreateRequest(
        "CTO", "Giám đốc Công nghệ", "Chief Technology Officer", "EXEC", 90)));
    verify(jobTitleRepository, never()).save(any());
  }

  @Test
  void create_withDuplicateName_throwsConflict() {
    when(jobTitleRepository.existsByCode("CTO")).thenReturn(false);
    when(jobTitleRepository.existsByName("Giám đốc Công nghệ")).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.create(new JobTitleCreateRequest(
        "CTO", "Giám đốc Công nghệ", "Chief Technology Officer", "EXEC", 90)));
  }

  @Test
  void update_changesSortOrder() {
    when(jobTitleRepository.findById(id)).thenReturn(Optional.of(existing()));

    JobTitleResponse response = service.update(id, new JobTitleUpdateRequest(
        null, null, null, null, 100));

    assertEquals(100, response.sortOrder());
    verify(auditService).success("JOB_TITLE_UPDATE", "JobTitle", id, "changed=[sortOrder]");
  }

  @Test
  void update_renamesUnusedJobTitle() {
    when(jobTitleRepository.findById(id)).thenReturn(Optional.of(existing()));
    when(jobTitleRepository.existsByNameAndIdNot("Giám đốc Kỹ thuật", id)).thenReturn(false);
    when(employeeRepository.existsByPosition("Giám đốc Công nghệ")).thenReturn(false);

    JobTitleResponse response = service.update(id, new JobTitleUpdateRequest(
        null, "Giám đốc Kỹ thuật", null, null, null));

    assertEquals("Giám đốc Kỹ thuật", response.name());
  }

  @Test
  void update_renameUsedJobTitle_throwsBusinessRule() {
    when(jobTitleRepository.findById(id)).thenReturn(Optional.of(existing()));
    when(jobTitleRepository.existsByNameAndIdNot("Giám đốc Kỹ thuật", id)).thenReturn(false);
    when(employeeRepository.existsByPosition("Giám đốc Công nghệ")).thenReturn(true);

    assertThrows(BusinessRuleException.class, () -> service.update(id,
        new JobTitleUpdateRequest(null, "Giám đốc Kỹ thuật", null, null, null)));
  }

  @Test
  void delete_usedJobTitle_throwsBusinessRule() {
    when(jobTitleRepository.findById(id)).thenReturn(Optional.of(existing()));
    when(employeeRepository.existsByPosition("Giám đốc Công nghệ")).thenReturn(true);

    assertThrows(BusinessRuleException.class, () -> service.delete(id));
    verify(jobTitleRepository, never()).delete(any());
  }

  @Test
  void delete_unusedJobTitle_deletesAndAudits() {
    when(jobTitleRepository.findById(id)).thenReturn(Optional.of(existing()));
    when(employeeRepository.existsByPosition("Giám đốc Công nghệ")).thenReturn(false);

    service.delete(id);

    verify(jobTitleRepository).delete(any(JobTitle.class));
    verify(auditService).success("JOB_TITLE_DELETE", "JobTitle", id, "code=CTO");
  }

  @Test
  void get_missing_throwsNotFound() {
    when(jobTitleRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> service.get(id));
  }
}
