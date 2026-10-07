package com.hrms.identity.employee;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.kafka.EmployeeEventTypes;
import com.hrms.common.kafka.KafkaTopics;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxPayloadConverter;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
import com.hrms.identity.event.EmployeeEventPayload;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD nhân viên. Mỗi thao tác ghi business data + outbox trong CÙNG transaction
 * ({@code @Transactional}); {@code OutboxPublisher} poll và publish sang Kafka.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

  private static final String AGGREGATE_TYPE = "Employee";

  private final EmployeeRepository employeeRepository;
  private final OutboxRepository outboxRepository;
  private final EmployeeMapper mapper;

  @Transactional
  public EmployeeResponse create(EmployeeCreateRequest request, String actorId) {
    if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
      throw new ConflictException("Mã nhân viên đã tồn tại: " + request.employeeCode());
    }
    Employee employee = mapper.toEntity(request);
    employee.setStatus(EmployeeStatus.ACTIVE);
    employeeRepository.save(employee);
    writeOutbox(employee, EmployeeEventTypes.CREATED, actorId, null, null, null);
    return mapper.toResponse(employee);
  }

  @Transactional(readOnly = true)
  public List<EmployeeResponse> list(EmployeeStatus status) {
    List<Employee> employees = status == null
        ? employeeRepository.findAllByOrderByCreatedAtDesc()
        : employeeRepository.findByStatusOrderByCreatedAtDesc(status);
    return employees.stream().map(mapper::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public EmployeeResponse get(UUID id) {
    return mapper.toResponse(find(id));
  }

  @Transactional
  public EmployeeResponse update(UUID id, EmployeeUpdateRequest patch, String actorId) {
    if (isBlank(patch.fullName()) && isBlank(patch.email())
        && isBlank(patch.departmentName()) && isBlank(patch.position())
        && isBlank(patch.managerId()) && isBlank(patch.status())) {
      throw new BusinessRuleException("Không có trường nào để cập nhật");
    }
    if (EmployeeStatus.TERMINATED.name().equals(patch.status())) {
      throw new BusinessRuleException(
          "Không thể nghỉ việc qua PATCH — dùng DELETE /api/v1/employees/{id}");
    }

    Employee employee = find(id);
    List<String> changed = new ArrayList<>();

    if (!isBlank(patch.fullName()) && !Objects.equals(patch.fullName(), employee.getFullName())) {
      employee.setFullName(patch.fullName());
      changed.add("fullName");
    }
    if (!isBlank(patch.email()) && !Objects.equals(patch.email(), employee.getEmail())) {
      employee.setEmail(patch.email());
      changed.add("email");
    }
    if (!isBlank(patch.departmentName())
        && !Objects.equals(patch.departmentName(), employee.getDepartmentName())) {
      employee.setDepartmentName(patch.departmentName());
      changed.add("departmentName");
    }
    if (!isBlank(patch.position()) && !Objects.equals(patch.position(), employee.getPosition())) {
      employee.setPosition(patch.position());
      changed.add("position");
    }
    if (!isBlank(patch.managerId()) && !Objects.equals(patch.managerId(), employee.getManagerId())) {
      employee.setManagerId(patch.managerId());
      changed.add("managerId");
    }
    if (!isBlank(patch.status())) {
      EmployeeStatus newStatus;
      try {
        newStatus = EmployeeStatus.valueOf(patch.status());
      } catch (IllegalArgumentException ex) {
        throw new BusinessRuleException("Trạng thái không hợp lệ: " + patch.status());
      }
      if (newStatus != employee.getStatus()) {
        employee.setStatus(newStatus);
        changed.add("status");
      }
    }

    if (!changed.isEmpty()) {
      employeeRepository.save(employee);
      writeOutbox(employee, EmployeeEventTypes.UPDATED, actorId, changed, null, null);
    }
    return mapper.toResponse(employee);
  }

  /** Nghỉ việc (soft delete): chỉ đổi status, không xóa record — giữ cho payroll/tracing. */
  @Transactional
  public void terminate(UUID id, String actorId, String reason) {
    Employee employee = find(id);
    if (employee.getStatus() == EmployeeStatus.TERMINATED) {
      throw new BusinessRuleException("Nhân viên đã nghỉ việc: " + employee.getEmployeeCode());
    }
    employee.setStatus(EmployeeStatus.TERMINATED);
    employeeRepository.save(employee);
    writeOutbox(employee, EmployeeEventTypes.TERMINATED, actorId, null, reason,
        Instant.now().toEpochMilli());
  }

  private Employee find(UUID id) {
    return employeeRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
  }

  private void writeOutbox(Employee employee, String eventType, String actorId,
      List<String> changedFields, String reason, Long terminatedAt) {
    EmployeeEventPayload payload =
        EmployeeEventPayload.from(employee, actorId, changedFields, reason, terminatedAt);
    outboxRepository.save(OutboxEvent.of(
        AGGREGATE_TYPE,
        employee.getId().toString(),
        eventType,
        KafkaTopics.EMPLOYEE_V1,
        OutboxPayloadConverter.toJson(payload)));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
