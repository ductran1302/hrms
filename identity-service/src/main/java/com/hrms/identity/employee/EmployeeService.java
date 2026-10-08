package com.hrms.identity.employee;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.kafka.EmployeeEventTypes;
import com.hrms.common.kafka.KafkaTopics;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxPayloadConverter;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.department.Department;
import com.hrms.identity.department.DepartmentRepository;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
import com.hrms.identity.event.EmployeeEventPayload;
import com.hrms.identity.history.JobHistoryService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD nhân viên. Mỗi thao tác ghi business data + outbox trong CÙNG transaction
 * ({@code @Transactional}); {@code OutboxPublisher} poll và publish sang Kafka.
 * Thay đổi job info (phòng ban/vị trí/quản lý/trạng thái) được ghi vào
 * {@code employee_job_history} (model theo SAP SuccessFactors EC).
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

  private static final String AGGREGATE_TYPE = "Employee";

  private final EmployeeRepository employeeRepository;
  private final OutboxRepository outboxRepository;
  private final EmployeeMapper mapper;
  private final DepartmentRepository departmentRepository;
  private final JobHistoryService jobHistoryService;

  @Transactional
  public EmployeeResponse create(EmployeeCreateRequest request, String actorId) {
    if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
      throw new ConflictException("Mã nhân viên đã tồn tại: " + request.employeeCode());
    }
    Employee employee = mapper.toEntity(request);
    employee.setStatus(EmployeeStatus.ACTIVE);
    applyDepartment(employee, request.departmentId());
    validateGender(employee.getGender());
    validateManagerRef(employee.getManagerId(), null);
    employeeRepository.save(employee);
    writeOutbox(employee, EmployeeEventTypes.CREATED, actorId, null, null, null);
    return mapper.toResponse(employee);
  }

  /**
   * Danh sách phân trang + tìm kiếm/tìm lọc.
   * Bước 9: đổi từ list phẳng ({@code List}) sang {@code PageResponse} — FE/verify scripts
   * đọc field {@code items}.
   */
  @Transactional(readOnly = true)
  public PageResponse<EmployeeResponse> search(int page, int size, EmployeeStatus status,
      UUID departmentId, String q) {
    String query = (q == null || q.isBlank()) ? "" : q.trim().toLowerCase(Locale.ROOT);
    PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    List<EmployeeResponse> items =
        employeeRepository.search(status, departmentId, query, pageable).stream()
            .map(mapper::toResponse)
            .toList();
    long total = employeeRepository.countSearch(status, departmentId, query);
    return PageResponse.of(items, page, size, total);
  }

  @Transactional(readOnly = true)
  public EmployeeResponse get(UUID id) {
    return mapper.toResponse(find(id));
  }

  @Transactional
  public EmployeeResponse update(UUID id, EmployeeUpdateRequest patch, String actorId) {
    if (isNothingToPatch(patch)) {
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

    // ---- Phòng ban (job info → history) ----
    if (Boolean.TRUE.equals(patch.clearDepartment())) {
      if (employee.getDepartmentId() != null) {
        String oldName = employee.getDepartmentName();
        employee.setDepartmentId(null);
        employee.setDepartmentName(null);
        changed.add("departmentId");
        jobHistoryService.record(id, "department", oldName, null, actorId);
      }
    } else if (patch.departmentId() != null
        && !Objects.equals(patch.departmentId(), employee.getDepartmentId())) {
      String oldName = employee.getDepartmentName();
      applyDepartment(employee, patch.departmentId());
      changed.add("departmentId");
      jobHistoryService.record(id, "department", oldName, employee.getDepartmentName(), actorId);
    }

    if (!isBlank(patch.position())
        && !Objects.equals(patch.position(), employee.getPosition())) {
      jobHistoryService.record(id, "position", employee.getPosition(), patch.position(), actorId);
      employee.setPosition(patch.position());
      changed.add("position");
    }

    // ---- Quản lý trực tiếp (job info → history) ----
    if (Boolean.TRUE.equals(patch.clearManager())) {
      if (employee.getManagerId() != null) {
        employee.setManagerId(null);
        changed.add("managerId");
        jobHistoryService.record(id, "manager", null, null, actorId);
      }
    } else if (patch.managerId() != null
        && !Objects.equals(patch.managerId(), employee.getManagerId())) {
      validateManagerRef(patch.managerId(), id);
      jobHistoryService.record(id, "manager",
          employee.getManagerId() == null ? null : employee.getManagerId().toString(),
          patch.managerId().toString(), actorId);
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
        jobHistoryService.record(id, "status", employee.getStatus().name(), newStatus.name(),
            actorId);
        employee.setStatus(newStatus);
        changed.add("status");
      }
    }

    // ---- Personal info (không ghi job history) ----
    if (patch.birthDate() != null && !Objects.equals(patch.birthDate(), employee.getBirthDate())) {
      employee.setBirthDate(patch.birthDate());
      changed.add("birthDate");
    }
    if (!isBlank(patch.gender())) {
      validateGender(patch.gender());
      String normalized = patch.gender().trim().toUpperCase(Locale.ROOT);
      if (!Objects.equals(normalized, employee.getGender())) {
        employee.setGender(normalized);
        changed.add("gender");
      }
    }
    if (patch.phone() != null && !Objects.equals(patch.phone(), employee.getPhone())) {
      employee.setPhone(patch.phone());
      changed.add("phone");
    }
    if (patch.nationalId() != null
        && !Objects.equals(patch.nationalId(), employee.getNationalId())) {
      employee.setNationalId(patch.nationalId());
      changed.add("nationalId");
    }
    if (patch.address() != null && !Objects.equals(patch.address(), employee.getAddress())) {
      employee.setAddress(patch.address());
      changed.add("address");
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
    jobHistoryService.record(id, "status", employee.getStatus().name(),
        EmployeeStatus.TERMINATED.name(), actorId);
    employee.setStatus(EmployeeStatus.TERMINATED);
    employeeRepository.save(employee);
    writeOutbox(employee, EmployeeEventTypes.TERMINATED, actorId, null, reason,
        Instant.now().toEpochMilli());
  }

  private void applyDepartment(Employee employee, UUID departmentId) {
    if (departmentId == null) {
      employee.setDepartmentId(null);
      employee.setDepartmentName(null);
      return;
    }
    Department department = departmentRepository.findById(departmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
    employee.setDepartmentId(department.getId());
    employee.setDepartmentName(department.getName());
  }

  private void validateManagerRef(UUID managerId, UUID selfId) {
    if (managerId == null) {
      return;
    }
    if (selfId != null && managerId.equals(selfId)) {
      throw new BusinessRuleException("Nhân viên không thể là quản lý của chính mình");
    }
    employeeRepository.findById(managerId)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", managerId));
  }

  private void validateGender(String gender) {
    if (isBlank(gender)) {
      return;
    }
    if (!List.of("MALE", "FEMALE", "OTHER").contains(gender.trim().toUpperCase(Locale.ROOT))) {
      throw new BusinessRuleException("Giới tính chỉ nhận MALE/FEMALE/OTHER: " + gender);
    }
  }

  private boolean isNothingToPatch(EmployeeUpdateRequest patch) {
    return isBlank(patch.fullName())
        && isBlank(patch.email())
        && patch.departmentId() == null
        && !Boolean.TRUE.equals(patch.clearDepartment())
        && isBlank(patch.position())
        && patch.managerId() == null
        && !Boolean.TRUE.equals(patch.clearManager())
        && isBlank(patch.status())
        && patch.birthDate() == null
        && isBlank(patch.gender())
        && patch.phone() == null
        && patch.nationalId() == null
        && patch.address() == null;
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
