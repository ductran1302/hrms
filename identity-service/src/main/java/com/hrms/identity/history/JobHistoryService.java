package com.hrms.identity.history;

import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.history.dto.JobHistoryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ghi lịch sử thay đổi job info — mỗi dòng = một field đổi giá trị. */
@Service
@RequiredArgsConstructor
public class JobHistoryService {

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
  public List<JobHistoryResponse> listByEmployee(UUID employeeId) {
    return historyRepository.findByEmployee_IdOrderByEffectiveDateDesc(employeeId).stream()
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
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 500) {
      return value;
    }
    return value.substring(0, 500);
  }
}
