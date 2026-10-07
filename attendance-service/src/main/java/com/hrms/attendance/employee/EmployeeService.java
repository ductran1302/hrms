package com.hrms.attendance.employee;

import com.hrms.attendance.event.ProcessedEvent;
import com.hrms.attendance.event.ProcessedEventRepository;
import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import com.hrms.common.avro.employee.EmployeeTerminatedEvent;
import com.hrms.common.avro.employee.EmployeeUpdatedEvent;
import com.hrms.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projection + query cho attendance. Idempotent: mọi {@code applyX} kiểm tra
 * {@code processed_events} trước — replay cùng eventId không ghi thêm gì.
 *
 * <p>Thứ tự event theo employee được đảm bảo (cùng partition key) nên không cần
 * so version khi upsert.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

  public static final String STATUS_TERMINATED = "TERMINATED";

  private final EmployeeRepository employeeRepository;
  private final ProcessedEventRepository processedEventRepository;

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

  @Transactional(readOnly = true)
  public List<Employee> list(String status) {
    if (status == null || status.isBlank()) {
      return employeeRepository.findAll();
    }
    return employeeRepository.findByStatusOrderByFullNameAsc(status);
  }

  @Transactional(readOnly = true)
  public Employee getByCode(String employeeCode) {
    return employeeRepository
        .findByEmployeeCode(employeeCode)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeCode));
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
