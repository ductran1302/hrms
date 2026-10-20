package com.hrms.attendance.employee;

import com.hrms.attendance.department.DepartmentService;
import com.hrms.attendance.event.ProcessedEvent;
import com.hrms.attendance.event.ProcessedEventRepository;
import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import com.hrms.common.avro.employee.EmployeeTerminatedEvent;
import com.hrms.common.avro.employee.EmployeeUpdatedEvent;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.EmployeeScope;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projection + query cho attendance. Idempotent: mọi {@code applyX} kiểm tra
 * {@code processed_events} trước — replay cùng eventId không ghi thêm gì.
 *
 * <p>Thứ tự event theo employee được đảm bảo (cùng partition key) nên không cần
 * so version khi upsert.
 *
 * <p>Scoping (5-role): ALL (ADMIN/GLOBAL_LEAD) và SELF (MANAGER) → toàn bộ
 * (giữ nguyên hành vi attendance); DEPARTMENT (DEPT_LEAD) → chỉ nhân sự thuộc
 * phòng mình quản lý (gồm nhánh con) + trực thuộc + self — fail-closed khi
 * không khớp hồ sơ.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

  public static final String STATUS_TERMINATED = "TERMINATED";

  private final EmployeeRepository employeeRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final DepartmentService departmentService;

  @Transactional
  public void applyCreated(EmployeeCreatedEvent event) {
    if (isDuplicate(event.getEventId())) {
      return;
    }
    Employee employee = findOrCreate(event.getEmployeeId(), event.getEmployeeCode());
    employee.setFullName(event.getFullName());
    employee.setEmail(event.getEmail());
    employee.setDepartmentId(event.getDepartmentId());
    employee.setDepartmentName(event.getDepartmentName());
    employee.setPosition(event.getPosition());
    employee.setManagerId(event.getManagerId());
    employee.setStatus(event.getStatus());
    employee.setHiredAt(event.getHiredAt());
    employeeRepository.save(employee);
    markProcessed(event.getEventId(), event.getEventType());
  }

  @Transactional
  public void applyUpdated(EmployeeUpdatedEvent event) {
    if (isDuplicate(event.getEventId())) {
      return;
    }
    Employee employee = findOrCreate(event.getEmployeeId(), event.getEmployeeCode());
    employee.setFullName(event.getFullName());
    employee.setEmail(event.getEmail());
    employee.setDepartmentId(event.getDepartmentId());
    employee.setDepartmentName(event.getDepartmentName());
    employee.setPosition(event.getPosition());
    employee.setManagerId(event.getManagerId());
    employee.setStatus(event.getStatus());
    employee.setHiredAt(event.getHiredAt());
    employeeRepository.save(employee);
    markProcessed(event.getEventId(), event.getEventType());
  }

  @Transactional
  public void applyTerminated(EmployeeTerminatedEvent event) {
    if (isDuplicate(event.getEventId())) {
      return;
    }
    Employee employee = findOrCreate(event.getEmployeeId(), event.getEmployeeCode());
    employee.setFullName(event.getFullName());
    employee.setStatus(STATUS_TERMINATED);
    employee.setTerminatedAt(event.getTerminatedAt());
    employee.setTerminationReason(event.getReason());
    employeeRepository.save(employee);
    markProcessed(event.getEventId(), event.getEventType());
  }

  /** Danh sách nhân sự theo scope của người gọi (xem class javadoc). */
  @Transactional(readOnly = true)
  public List<Employee> list(String status, EmployeeScope scope, String username) {
    List<Employee> all;
    if (status == null || status.isBlank()) {
      all = employeeRepository.findAll();
    } else {
      all = employeeRepository.findByStatusOrderByFullNameAsc(status);
    }
    if (scope != EmployeeScope.DEPARTMENT) {
      return all;
    }
    Optional<Employee> self = resolveSelf(username);
    if (self.isEmpty()) {
      return List.of(); // fail-closed: không khớp hồ sơ → không thấy ai
    }
    String selfId = self.get().getEmployeeId();
    Set<String> deptIds = departmentService.managedSubtreeIds(selfId);
    return all.stream()
        .filter(e -> deptIds.contains(e.getDepartmentId())
            || selfId.equals(e.getManagerId())
            || selfId.equals(e.getEmployeeId()))
        .toList();
  }

  @Transactional(readOnly = true)
  public Employee getByCode(String employeeCode, EmployeeScope scope, String username) {
    Employee employee = employeeRepository
        .findByEmployeeCode(employeeCode)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeCode));
    if (scope == EmployeeScope.DEPARTMENT) {
      Optional<Employee> self = resolveSelf(username);
      if (self.isEmpty()) {
        throw new AccessDeniedException("Bạn không có quyền xem nhân sự này");
      }
      String selfId = self.get().getEmployeeId();
      boolean inScope = selfId.equals(employee.getEmployeeId())
          || selfId.equals(employee.getManagerId())
          || departmentService.managedSubtreeIds(selfId).contains(employee.getDepartmentId());
      if (!inScope) {
        throw new AccessDeniedException("Bạn không có quyền xem nhân sự này");
      }
    }
    return employee;
  }

  /** Khớp username đăng nhập (= employeeCode) với projection — không khớp → rỗng. */
  private Optional<Employee> resolveSelf(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    return employeeRepository.findByEmployeeCode(username);
  }

  private Employee findOrCreate(String employeeId, String employeeCode) {
    Optional<Employee> existing = employeeRepository.findById(employeeId);
    if (existing.isPresent()) {
      return existing.get();
    }
    Employee employee = new Employee();
    employee.setEmployeeId(employeeId);
    employee.setEmployeeCode(employeeCode);
    employee.setStatus("ACTIVE");
    return employee;
  }

  private boolean isDuplicate(String eventId) {
    return processedEventRepository.existsById(eventId);
  }

  private void markProcessed(String eventId, String eventType) {
    processedEventRepository.save(new ProcessedEvent(eventId, eventType));
  }
}
