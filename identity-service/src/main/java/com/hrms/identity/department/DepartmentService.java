package com.hrms.identity.department;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.kafka.DepartmentEventTypes;
import com.hrms.common.kafka.EmployeeEventTypes;
import com.hrms.common.kafka.KafkaTopics;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxPayloadConverter;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.identity.department.dto.DepartmentCreateRequest;
import com.hrms.identity.department.dto.DepartmentNode;
import com.hrms.identity.department.dto.DepartmentResponse;
import com.hrms.identity.department.dto.DepartmentUpdateRequest;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.event.DepartmentEventPayload;
import com.hrms.identity.event.EmployeeEventPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD phòng ban. Đặt cùng identity_db (không tách service) để có FK trực tiếp
 * với employees; khi đổi tên phòng ban thì đồng bộ {@code department_name}
 * denormalized của nhân viên + phát employee.updated qua outbox.
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

  private static final String EMPLOYEE_AGGREGATE = "Employee";
  private static final String DEPARTMENT_AGGREGATE = "Department";

  private final DepartmentRepository departmentRepository;
  private final EmployeeRepository employeeRepository;
  private final OutboxRepository outboxRepository;

  @Transactional
  public DepartmentResponse create(DepartmentCreateRequest request, String actorId) {
    if (departmentRepository.existsByCode(request.code())) {
      throw new ConflictException("Mã phòng ban đã tồn tại: " + request.code());
    }
    Department department = new Department();
    department.setCode(request.code().trim());
    department.setName(request.name().trim());
    department.setStatus(DepartmentStatus.ACTIVE);
    department.setParent(resolveParent(request.parentId(), null));
    department.setManager(resolveManager(request.managerId()));
    departmentRepository.save(department);
    writeDepartmentOutbox(department, DepartmentEventTypes.CREATED, actorId, null);
    return toResponse(department);
  }

  @Transactional(readOnly = true)
  public List<DepartmentResponse> list() {
    return departmentRepository.findAllByOrderByCreatedAtAsc().stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DepartmentNode> tree() {
    List<Department> all = departmentRepository.findAllActiveWithManager();
    Map<UUID, List<Department>> childrenByParent = new HashMap<>();
    Set<UUID> ids = new HashSet<>();
    for (Department d : all) {
      ids.add(d.getId());
      childrenByParent.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d);
    }
    // chống vòng lặp dữ liệu xấu: chỉ build từ root thật, visited set
    Set<UUID> visited = new HashSet<>();
    List<DepartmentNode> roots = new ArrayList<>();
    for (Department d : all) {
      if (d.getParentId() == null || !ids.contains(d.getParentId())) {
        roots.add(buildNode(d, childrenByParent, visited));
      }
    }
    return roots;
  }

  @Transactional
  public DepartmentResponse update(UUID id, DepartmentUpdateRequest patch, String actorId) {
    Department department = find(id);
    List<String> changed = new ArrayList<>();

    if (patch.code() != null && !patch.code().isBlank()
        && !Objects.equals(patch.code().trim(), department.getCode())) {
      if (departmentRepository.existsByCodeAndIdNot(patch.code().trim(), id)) {
        throw new ConflictException("Mã phòng ban đã tồn tại: " + patch.code());
      }
      department.setCode(patch.code().trim());
      changed.add("code");
    }

    boolean renamed = false;
    if (patch.name() != null && !patch.name().isBlank()
        && !Objects.equals(patch.name().trim(), department.getName())) {
      department.setName(patch.name().trim());
      renamed = true;
      changed.add("name");
    }

    // parentId/managerId: set theo giá trị gửi (null = bỏ quan hệ)
    if (patch.parentId() != null || department.getParentId() != null) {
      if (!Objects.equals(patch.parentId(), department.getParentId())) {
        changed.add("parentId");
      }
      department.setParent(resolveParent(patch.parentId(), id));
    }
    if (patch.managerId() != null || department.getManagerId() != null) {
      if (!Objects.equals(patch.managerId(), department.getManagerId())) {
        changed.add("managerId");
      }
      department.setManager(patch.managerId() == null ? null : resolveManager(patch.managerId()));
    }

    if (patch.status() != null && !patch.status().isBlank()) {
      try {
        DepartmentStatus newStatus = DepartmentStatus.valueOf(patch.status());
        if (newStatus != department.getStatus()) {
          changed.add("status");
        }
        department.setStatus(newStatus);
      } catch (IllegalArgumentException ex) {
        throw new BusinessRuleException("Trạng thái phòng ban không hợp lệ: " + patch.status());
      }
    }

    departmentRepository.save(department);

    if (renamed) {
      syncEmployeeDepartmentName(department);
    }
    if (!changed.isEmpty()) {
      writeDepartmentOutbox(department, DepartmentEventTypes.UPDATED, actorId, changed);
    }
    return toResponse(department);
  }

  /** Soft delete: chuyển INACTIVE. Chặn khi còn phòng ban con (tránh cây mồ côi). */
  @Transactional
  public void deactivate(UUID id, String actorId) {
    Department department = find(id);
    if (department.getStatus() == DepartmentStatus.INACTIVE) {
      throw new BusinessRuleException("Phòng ban đã bị vô hiệu: " + department.getCode());
    }
    if (departmentRepository.countByParent_Id(id) > 0) {
      throw new BusinessRuleException(
          "Còn phòng ban con thuộc " + department.getCode() + " — gỡ phòng con trước");
    }
    department.setStatus(DepartmentStatus.INACTIVE);
    departmentRepository.save(department);
    writeDepartmentOutbox(department, DepartmentEventTypes.UPDATED, actorId, List.of("status"));
  }

  /** Outbox event phòng ban (cùng transaction với business data) → attendance sync projection. */
  private void writeDepartmentOutbox(Department department, String eventType, String actorId,
      List<String> changedFields) {
    outboxRepository.save(OutboxEvent.of(
        DEPARTMENT_AGGREGATE,
        department.getId().toString(),
        eventType,
        KafkaTopics.DEPARTMENT_V1,
        OutboxPayloadConverter.toJson(
            DepartmentEventPayload.from(department, actorId, changedFields))));
  }

  /** Đổi tên phòng → copy sang department_name của nhân viên + outbox event. */
  private void syncEmployeeDepartmentName(Department department) {
    List<Employee> members =
        employeeRepository.findByDepartmentIdOrderByCreatedAtAsc(department.getId());
    if (members.isEmpty()) {
      return;
    }
    for (Employee employee : members) {
      if (Objects.equals(employee.getDepartmentName(), department.getName())) {
        continue;
      }
      employee.setDepartmentName(department.getName());
      employeeRepository.save(employee);
      EmployeeEventPayload payload =
          EmployeeEventPayload.from(employee, null, List.of("departmentName"), null, null);
      outboxRepository.save(OutboxEvent.of(
          EMPLOYEE_AGGREGATE,
          employee.getId().toString(),
          EmployeeEventTypes.UPDATED,
          KafkaTopics.EMPLOYEE_V1,
          OutboxPayloadConverter.toJson(payload)));
    }
  }

  private DepartmentNode buildNode(Department department,
      Map<UUID, List<Department>> childrenByParent, Set<UUID> visited) {
    visited.add(department.getId());
    List<DepartmentNode> children = new ArrayList<>();
    for (Department child : childrenByParent.getOrDefault(department.getId(), List.of())) {
      if (visited.add(child.getId())) {
        children.add(buildNode(child, childrenByParent, visited));
      }
    }
    children.sort((a, b) -> a.code().compareToIgnoreCase(b.code()));
    return new DepartmentNode(department.getId(), department.getCode(), department.getName(),
        department.getStatus().name(), department.getManagerName(), children);
  }

  private Department resolveParent(UUID parentId, UUID selfId) {
    if (parentId == null) {
      return null;
    }
    Department parent = departmentRepository.findById(parentId)
        .orElseThrow(() -> new ResourceNotFoundException("Department", parentId));
    if (selfId != null) {
      if (parentId.equals(selfId)) {
        throw new BusinessRuleException("Phòng ban không thể là cha của chính nó");
      }
      if (isDescendant(parent, selfId)) {
        throw new BusinessRuleException("Không thể gán cha tạo thành vòng lặp trong cây phòng ban");
      }
    }
    return parent;
  }

  private boolean isDescendant(Department node, UUID ancestorId) {
    Set<UUID> seen = new HashSet<>();
    Department current = node;
    while (current != null && current.getParentId() != null) {
      if (ancestorId.equals(current.getParentId())) {
        return true;
      }
      if (!seen.add(current.getParentId())) {
        return false; // dữ liệu cũ có vòng → dừng
      }
      UUID parentId = current.getParentId();
      current = departmentRepository.findById(parentId).orElse(null);
    }
    return false;
  }

  private Employee resolveManager(UUID managerId) {
    if (managerId == null) {
      return null;
    }
    return employeeRepository.findById(managerId)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", managerId));
  }

  private DepartmentResponse toResponse(Department department) {
    return new DepartmentResponse(
        department.getId(),
        department.getCode(),
        department.getName(),
        department.getParentId(),
        department.getManagerId(),
        department.getManagerName(),
        department.getStatus().name(),
        departmentRepository.countActiveEmployees(department.getId()));
  }

  private Department find(UUID id) {
    return departmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Department", id));
  }
}
