package com.hrms.identity.history;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ghi lịch sử thay đổi job info — mỗi dòng = một field đổi giá trị. */
@Service
@RequiredArgsConstructor
public class JobHistoryService {

  private static final int MAX_PAGE_SIZE = 200;

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
    List<JobHistoryResponse> items = history.getContent().stream()
        .map(entry -> new JobHistoryResponse(
            entry.getId(),
            entry.getEmployee().getId(),
            entry.getEffectiveDate(),
            entry.getField(),
            entry.getOldValue(),
            entry.getNewValue(),
            entry.getActorId(),
            entry.getReason()))
        .toList();
    return PageResponse.of(items, safePage, safeSize, history.getTotalElements());
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 500) {
      return value;
    }
    return value.substring(0, 500);
  }
}
