package com.hrms.attendance.employee;

import com.hrms.attendance.event.ProcessedEvent;
import com.hrms.attendance.event.ProcessedEventRepository;
import com.hrms.attendance.leave.LeaveEmployeeNoteRepository;
import com.hrms.attendance.leave.LeaveEntitlementRepository;
import com.hrms.attendance.leave.LeaveWorkDayRepository;
import com.hrms.attendance.shift.ShiftAssignmentRepository;
import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import com.hrms.common.avro.employee.EmployeeTerminatedEvent;
import com.hrms.common.avro.employee.EmployeeUpdatedEvent;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.EmployeeScope;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projection + query cho attendance. Idempotent: mọi {@code applyX} kiểm tra
 * {@code processed_events} trước — replay cùng eventId không ghi thêm gì.
 *
 * <p>Thứ tự event theo employee được đảm bảo (cùng partition key) nên không cần
 * so version khi upsert.
 *
 * <p>Scoping (5-role, xem {@link EmployeeAccess}): ALL (ADMIN/GLOBAL_LEAD) → toàn bộ;
 * DEPARTMENT (DEPT_LEAD) → phòng mình quản lý (gồm nhánh con) + trực thuộc + self;
 * SELF (MANAGER/EMPLOYEE) → trực thuộc + self — fail-closed khi không khớp hồ sơ.
 *
 * <p>Đối chiếu {@code ROLE_ADMIN} khác biệt của attendance: {@code EMPLOYEE} bị chặn ở
 * {@code @PreAuthorize}; {@code MANAGER} trước đây thấy toàn bộ — nay siết còn self ∪ trực thuộc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

  public static final String STATUS_TERMINATED = "TERMINATED";

  private final EmployeeRepository employeeRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final EmployeeAccessResolver accessResolver;
  private final ShiftAssignmentRepository shiftAssignmentRepository;
  private final LeaveEntitlementRepository leaveEntitlementRepository;
  private final LeaveEmployeeNoteRepository leaveNoteRepository;
  private final LeaveWorkDayRepository leaveWorkDayRepository;

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
    applyRoles(employee, event.getRoles());
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
    applyRoles(employee, event.getRoles());
    employeeRepository.save(employee);
    markProcessed(event.getEventId(), event.getEventType());
  }

  /** roles = null (event cũ/không đụng roles) → giữ nguyên; khác null → mirror (join ","). */
  private void applyRoles(Employee employee, List<String> roles) {
    if (roles != null) {
      employee.setRoles(roles.isEmpty() ? null : String.join(",", roles));
    }
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
    if (scope == EmployeeScope.ALL) {
      return all;
    }
    EmployeeAccess access = accessResolver.resolve(username, scope);
    return all.stream().filter(access::canAccess).toList();
  }

  /** Chi tiết theo mã NV — ngoài scope → 403 (fail-closed khi không khớp hồ sơ người gọi). */
  @Transactional(readOnly = true)
  public Employee getByCode(String employeeCode, EmployeeScope scope, String username) {
    Employee employee = employeeRepository
        .findByEmployeeCode(employeeCode)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeCode));
    if (scope != EmployeeScope.ALL) {
      accessResolver.resolve(username, scope).assertCanAccess(employee);
    }
    return employee;
  }

  private Employee findOrCreate(String employeeId, String employeeCode) {
    Optional<Employee> existing = employeeRepository.findById(employeeId);
    if (existing.isPresent()) {
      return existing.get();
    }
    // Identity tạo lại nhân viên (id mới, cùng employeeCode) — không insert mới
    // (vi phạm uq_attendance_emp_code) mà re-key dòng cũ + FK gắn cũ tham chiếu id cũ.
    Optional<Employee> byCode = employeeRepository.findByEmployeeCode(employeeCode);
    if (byCode.isPresent()) {
      Employee old = byCode.get();
      String oldId = old.getEmployeeId();
      if (!oldId.equals(employeeId)) {
        int movedAssignments = shiftAssignmentRepository.rekeyEmployee(oldId, employeeId);
        int movedEntitlements = leaveEntitlementRepository.rekeyEmployee(oldId, employeeId);
        int movedNotes = leaveNoteRepository.rekeyEmployee(oldId, employeeId);
        int movedWorkDays = leaveWorkDayRepository.rekeyEmployee(oldId, employeeId);
        // Hibernate cấm đổi @Id của managed entity (JpaSystemException "identifier
        // altered" khi flush → consumer retry vô hạn) — xóa row cũ + chèn row mới
        // với id từ event, copy toàn bộ projection.
        Employee fresh = copyWithId(old, employeeId);
        employeeRepository.delete(old);
        employeeRepository.flush(); // DELETE chạy ngay — nhường uq_attendance_emp_code cho row mới
        log.warn(
            "Re-key attendance nhân sự {} ({} -> {}) — {} gán ca, {} quota phép, {} ghi chú,"
                + " {} ngày công dời theo",
            employeeCode, oldId, employeeId, movedAssignments, movedEntitlements, movedNotes,
            movedWorkDays);
        return fresh; // caller save() — insert row mới sau khi row cũ đã xóa
      }
      return old;
    }
    Employee employee = new Employee();
    employee.setEmployeeId(employeeId);
    employee.setEmployeeCode(employeeCode);
    employee.setStatus("ACTIVE");
    return employee;
  }

  private static Employee copyWithId(Employee source, String newId) {
    Employee copy = new Employee();
    copy.setEmployeeId(newId);
    copy.setEmployeeCode(source.getEmployeeCode());
    copy.setFullName(source.getFullName());
    copy.setEmail(source.getEmail());
    copy.setDepartmentId(source.getDepartmentId());
    copy.setDepartmentName(source.getDepartmentName());
    copy.setPosition(source.getPosition());
    copy.setRoles(source.getRoles());
    copy.setManagerId(source.getManagerId());
    copy.setStatus(source.getStatus());
    copy.setHiredAt(source.getHiredAt());
    copy.setTerminatedAt(source.getTerminatedAt());
    copy.setTerminationReason(source.getTerminationReason());
    return copy;
  }

  private boolean isDuplicate(String eventId) {
    return processedEventRepository.existsById(eventId);
  }

  private void markProcessed(String eventId, String eventType) {
    processedEventRepository.save(new ProcessedEvent(eventId, eventType));
  }
}
