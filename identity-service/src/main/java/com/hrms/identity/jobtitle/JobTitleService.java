package com.hrms.identity.jobtitle;

import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.jobtitle.dto.JobTitleCreateRequest;
import com.hrms.identity.jobtitle.dto.JobTitleResponse;
import com.hrms.identity.jobtitle.dto.JobTitleUpdateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobTitleService {

  private static final String AGGREGATE_TYPE = "JobTitle";

  private final JobTitleRepository jobTitleRepository;
  private final EmployeeRepository employeeRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<JobTitleResponse> list() {
    return jobTitleRepository.findAllByOrderBySortOrderAsc().stream()
        .map(JobTitleService::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public JobTitleResponse get(UUID id) {
    return toResponse(find(id));
  }

  @Transactional
  public JobTitleResponse create(JobTitleCreateRequest request) {
    String code = normalizeCode(request.code());
    if (jobTitleRepository.existsByCode(code)) {
      throw new ConflictException("Mã chức vụ đã tồn tại: " + code);
    }
    String name = request.name().trim();
    if (jobTitleRepository.existsByName(name)) {
      throw new ConflictException("Tên chức vụ đã tồn tại: " + name);
    }
    JobTitle jobTitle = new JobTitle();
    jobTitle.setCode(code);
    jobTitle.setName(name);
    jobTitle.setNameEn(request.nameEn().trim());
    jobTitle.setCategory(request.category().trim());
    jobTitle.setSortOrder(request.sortOrder());
    jobTitleRepository.save(jobTitle);
    auditService.success("JOB_TITLE_CREATE", AGGREGATE_TYPE, jobTitle.getId(),
        "code=" + code);
    return toResponse(jobTitle);
  }

  @Transactional
  public JobTitleResponse update(UUID id, JobTitleUpdateRequest patch) {
    JobTitle jobTitle = find(id);
    List<String> changed = new ArrayList<>();

    if (!isBlank(patch.code())) {
      String code = normalizeCode(patch.code());
      if (!code.equals(jobTitle.getCode())) {
        if (jobTitleRepository.existsByCodeAndIdNot(code, id)) {
          throw new ConflictException("Mã chức vụ đã tồn tại: " + code);
        }
        jobTitle.setCode(code);
        changed.add("code");
      }
    }

    if (!isBlank(patch.name())) {
      String name = patch.name().trim();
      if (!Objects.equals(name, jobTitle.getName())) {
        if (jobTitleRepository.existsByNameAndIdNot(name, id)) {
          throw new ConflictException("Tên chức vụ đã tồn tại: " + name);
        }
        if (employeeRepository.existsByPosition(jobTitle.getName())) {
          throw new BusinessRuleException(
              "Không thể đổi tên chức vụ đang được gán cho nhân viên");
        }
        jobTitle.setName(name);
        changed.add("name");
      }
    }

    if (!isBlank(patch.nameEn())) {
      String nameEn = patch.nameEn().trim();
      if (!Objects.equals(nameEn, jobTitle.getNameEn())) {
        jobTitle.setNameEn(nameEn);
        changed.add("nameEn");
      }
    }

    if (!isBlank(patch.category())) {
      String category = patch.category().trim();
      if (!Objects.equals(category, jobTitle.getCategory())) {
        jobTitle.setCategory(category);
        changed.add("category");
      }
    }

    if (patch.sortOrder() != null && patch.sortOrder() != jobTitle.getSortOrder()) {
      jobTitle.setSortOrder(patch.sortOrder());
      changed.add("sortOrder");
    }

    jobTitleRepository.save(jobTitle);
    if (!changed.isEmpty()) {
      auditService.success("JOB_TITLE_UPDATE", AGGREGATE_TYPE, id, "changed=" + changed);
    }
    return toResponse(jobTitle);
  }

  @Transactional
  public void delete(UUID id) {
    JobTitle jobTitle = find(id);
    if (employeeRepository.existsByPosition(jobTitle.getName())) {
      throw new BusinessRuleException(
          "Chức vụ '" + jobTitle.getName() + "' đang được gán cho nhân viên — không thể xóa");
    }
    jobTitleRepository.delete(jobTitle);
    auditService.success("JOB_TITLE_DELETE", AGGREGATE_TYPE, id, "code=" + jobTitle.getCode());
  }

  private JobTitle find(UUID id) {
    return jobTitleRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("JobTitle", id));
  }

  private static String normalizeCode(String code) {
    return code.trim().toUpperCase(Locale.ROOT);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static JobTitleResponse toResponse(JobTitle jobTitle) {
    return new JobTitleResponse(jobTitle.getId(), jobTitle.getCode(), jobTitle.getName(),
        jobTitle.getNameEn(), jobTitle.getCategory(), jobTitle.getSortOrder());
  }
}
