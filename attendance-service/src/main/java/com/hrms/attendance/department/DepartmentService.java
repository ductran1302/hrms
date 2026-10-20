package com.hrms.attendance.department;

import com.hrms.attendance.event.ProcessedEvent;
import com.hrms.attendance.event.ProcessedEventRepository;
import com.hrms.common.avro.department.DepartmentCreatedEvent;
import com.hrms.common.avro.department.DepartmentUpdatedEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projection phòng ban trong attendance — sync từ Kafka event của identity.
 * Idempotent: mọi {@code applyX} kiểm tra {@code processed_events} trước.
 *
 * <p>Phục vụ scope DEPT_LEAD: {@link #managedSubtreeIds(String)} trả về phòng
 * mà nhân viên đang quản lý (ACTIVE) + toàn bộ nhánh con.
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

  public static final String STATUS_ACTIVE = "ACTIVE";

  private final DepartmentRepository departmentRepository;
  private final ProcessedEventRepository processedEventRepository;

  @Transactional
  public void applyCreated(DepartmentCreatedEvent event) {
    if (isDuplicate(event.getEventId())) {
      return;
    }
    Department department = findOrCreate(event.getDepartmentId());
    applyFields(department, event.getCode(), event.getName(), event.getParentId(),
        event.getManagerId(), event.getStatus());
    departmentRepository.save(department);
    markProcessed(event.getEventId(), event.getEventType());
  }

  @Transactional
  public void applyUpdated(DepartmentUpdatedEvent event) {
    if (isDuplicate(event.getEventId())) {
      return;
    }
    Department department = findOrCreate(event.getDepartmentId());
    applyFields(department, event.getCode(), event.getName(), event.getParentId(),
        event.getManagerId(), event.getStatus());
    departmentRepository.save(department);
    markProcessed(event.getEventId(), event.getEventType());
  }

  /**
   * Id các phòng ACTIVE mà {@code employeeId} là trưởng (gồm cả nhánh con qua
   * parent_id) — fail-closed: không quản lý phòng nào → rỗng.
   */
  @Transactional(readOnly = true)
  public Set<String> managedSubtreeIds(String employeeId) {
    List<Department> roots =
        departmentRepository.findByManagerIdAndStatus(employeeId, STATUS_ACTIVE);
    Set<String> result = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    for (Department root : roots) {
      if (result.add(root.getDepartmentId())) {
        queue.add(root.getDepartmentId());
      }
    }
    while (!queue.isEmpty()) {
      for (Department child : departmentRepository.findByParentId(queue.poll())) {
        if (STATUS_ACTIVE.equals(child.getStatus()) && result.add(child.getDepartmentId())) {
          queue.add(child.getDepartmentId());
        }
      }
    }
    return result;
  }

  private void applyFields(Department department, String code, String name, String parentId,
      String managerId, String status) {
    if (code != null) {
      department.setCode(code);
    }
    if (name != null) {
      department.setName(name);
    }
    department.setParentId(parentId);
    department.setManagerId(managerId);
    if (status != null) {
      department.setStatus(status);
    }
  }

  private Department findOrCreate(String departmentId) {
    Optional<Department> existing = departmentRepository.findById(departmentId);
    if (existing.isPresent()) {
      return existing.get();
    }
    Department department = new Department();
    department.setDepartmentId(departmentId);
    department.setStatus(STATUS_ACTIVE);
    return department;
  }

  private boolean isDuplicate(String eventId) {
    return processedEventRepository.existsById(eventId);
  }

  private void markProcessed(String eventId, String eventType) {
    processedEventRepository.save(new ProcessedEvent(eventId, eventType));
  }
}
