package com.hrms.identity.history;

import com.hrms.common.security.Roles;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.history.dto.JobHistoryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ghi lịch sử thay đổi job info — mỗi dòng = một field đổi giá trị. */
@Service
@RequiredArgsConstructor
public class JobHistoryService {

  private static final int MAX_PAGE_SIZE = 200;
  private static final String SALARY_FIELD = "contract.salary";

  private final EmployeeJobHistoryRepository historyRepository;
  private final EmployeeRepository employeeRepository;

  @Transactional
  public void record(UUID employeeId, String field, String oldValue, String newValue,
      String actorId) {
    record(employeeId, field, oldValue, newValue, actorId, null);
  }

  @Transactional
  public void record(UUID employeeId, String field, String oldValue, String newValue,
      String actorId, String reason) {
    if (Objects.equals(oldValue, newValue)) {
      return;
    }
    EmployeeJobHistory entry = new EmployeeJobHistory();
    entry.setEmployee(employeeRepository.getReferenceById(employeeId));
    entry.setEffectiveDate(Instant.now());
    entry.setField(field);
    entry.setOldValue(truncate(oldValue));
    entry.setNewValue(truncate(newValue));
    entry.setActorId(actorId);
    entry.setReason(truncate(reason));
    historyRepository.save(entry);
  }

  @Transactional(readOnly = true)
  public PageResponse<JobHistoryResponse> listByEmployee(UUID employeeId, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    Page<EmployeeJobHistory> history = historyRepository.findByEmployee_Id(employeeId,
        PageRequest.of(safePage, safeSize, Sort.by("effectiveDate").descending()));
    boolean includeSalary = canViewSalary();
    List<JobHistoryResponse> items = history.getContent().stream()
        .map(entry -> toResponse(entry, includeSalary))
        .toList();
    return PageResponse.of(items, safePage, safeSize, history.getTotalElements());
  }

  /**
   * Lương hợp đồng chỉ hiện với ADMIN/GLOBAL_LEAD. Các role khác vẫn thấy dòng lịch sử
   * nhưng old/new value bị ẩn để không lộ lương qua endpoint history.
   */
  private JobHistoryResponse toResponse(EmployeeJobHistory entry, boolean includeSalary) {
    boolean hideSalary = !includeSalary && SALARY_FIELD.equals(entry.getField());
    return new JobHistoryResponse(
        entry.getId(),
        entry.getEmployee().getId(),
        entry.getEffectiveDate(),
        entry.getField(),
        hideSalary ? null : entry.getOldValue(),
        hideSalary ? null : entry.getNewValue(),
        entry.getActorId(),
        entry.getReason());
  }

  /** Fail-closed: không có authentication hoặc role lạ → không trả lương. */
  private static boolean canViewSalary() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return false;
    }
    String admin = "ROLE_" + Roles.ADMIN;
    String globalLead = "ROLE_" + Roles.GLOBAL_LEAD;
    return auth.getAuthorities().stream()
        .anyMatch(a -> admin.equals(a.getAuthority()) || globalLead.equals(a.getAuthority()));
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 500) {
      return value;
    }
    return value.substring(0, 500);
  }
}
