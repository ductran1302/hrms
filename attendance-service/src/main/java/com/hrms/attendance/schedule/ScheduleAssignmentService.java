package com.hrms.attendance.schedule;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeAccess;
import com.hrms.attendance.employee.EmployeeAccessResolver;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.schedule.dto.ScheduleAssignmentRequest;
import com.hrms.attendance.schedule.dto.ScheduleAssignmentResponse;
import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.EmployeeScope;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gán policy lịch cho nhân viên theo ngày hiệu lực (chỉ ADMIN ghi). */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleAssignmentService {

  private final EmployeeScheduleAssignmentRepository assignmentRepository;
  private final WorkSchedulePolicyRepository policyRepository;
  private final EmployeeRepository employeeRepository;
  private final EmployeeAccessResolver accessResolver;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<ScheduleAssignmentResponse> list(
      String employeeId, String username, EmployeeScope scope) {
    EmployeeAccess access = scope == EmployeeScope.ALL
        ? EmployeeAccess.all() : accessResolver.resolve(username, scope);
    Employee target = null;
    if (employeeId != null && !employeeId.isBlank()) {
      target = employeeRepository.findById(employeeId)
          .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
      access.assertCanAccess(target);
    }

    List<EmployeeScheduleAssignment> rows;
    if (target != null) {
      rows = assignmentRepository.findByEmployeeIdOrderByEffectiveFromDesc(target.getEmployeeId());
    } else {
      List<Employee> visible = employeeRepository.findAll().stream()
          .filter(access::canAccess)
          .toList();
      if (visible.isEmpty()) {
        return List.of();
      }
      rows = assignmentRepository.findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(
          visible.stream().map(Employee::getEmployeeId).toList());
    }
    if (rows.isEmpty()) {
      return List.of();
    }

    Set<String> employeeIds = new HashSet<>();
    Set<UUID> policyIds = new HashSet<>();
    for (EmployeeScheduleAssignment row : rows) {
      employeeIds.add(row.getEmployeeId());
      policyIds.add(row.getPolicyId());
    }
    Map<String, Employee> employees = new HashMap<>();
    for (Employee employee : employeeRepository.findAllById(employeeIds)) {
      employees.put(employee.getEmployeeId(), employee);
    }
    Map<UUID, String> policyNames = new HashMap<>();
    for (WorkSchedulePolicy policy : policyRepository.findAllById(policyIds)) {
      policyNames.put(policy.getId(), policy.getName());
    }

    Map<String, EmployeeScheduleAssignment> latest = new HashMap<>();
    for (EmployeeScheduleAssignment row : rows) {
      latest.merge(row.getEmployeeId(), row,
          (a, b) -> a.getEffectiveFrom().isAfter(b.getEffectiveFrom()) ? a : b);
    }
    List<ScheduleAssignmentResponse> responses = new ArrayList<>();
    for (EmployeeScheduleAssignment row : rows) {
      Employee employee = employees.get(row.getEmployeeId());
      responses.add(ScheduleAssignmentResponse.from(
          row,
          employee == null ? null : employee.getEmployeeCode(),
          employee == null ? null : employee.getFullName(),
          policyNames.get(row.getPolicyId()),
          latest.get(row.getEmployeeId()) == row));
    }
    return responses;
  }

  @Transactional
  public ScheduleAssignmentResponse assign(ScheduleAssignmentRequest request, String actor) {
    Employee employee = employeeRepository.findById(request.employeeId())
        .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", request.employeeId()));
    WorkSchedulePolicy policy = policyRepository.findById(request.policyId())
        .orElseThrow(() -> new ResourceNotFoundException("Policy lịch", request.policyId()));
    if (!policy.isActive()) {
      throw new BusinessRuleException("Policy '" + policy.getName() + "' đã ngưng dùng");
    }
    if (assignmentRepository.existsByEmployeeIdAndEffectiveFrom(
        employee.getEmployeeId(), request.effectiveFrom())) {
      throw new ConflictException(
          "Nhân viên đã có bản gán policy từ ngày " + request.effectiveFrom());
    }

    EmployeeScheduleAssignment assignment = new EmployeeScheduleAssignment();
    assignment.setEmployeeId(employee.getEmployeeId());
    assignment.setPolicyId(policy.getId());
    assignment.setEffectiveFrom(request.effectiveFrom());
    assignment.setAssignedBy(actor);
    assignmentRepository.saveAndFlush(assignment);

    boolean latest = assignmentRepository
        .findTopByEmployeeIdOrderByEffectiveFromDesc(employee.getEmployeeId())
        .map(current -> current.getId().equals(assignment.getId()))
        .orElse(true);
    auditService.success("SCHEDULE_ASSIGN", "EmployeeScheduleAssignment", assignment.getId(),
        "employeeId=" + employee.getEmployeeId() + " policyId=" + policy.getId()
            + " from=" + assignment.getEffectiveFrom() + " by=" + actor);
    log.info("Gán policy '{}' cho NV {} từ {}", policy.getName(), employee.getEmployeeId(),
        assignment.getEffectiveFrom());
    return ScheduleAssignmentResponse.from(
        assignment, employee.getEmployeeCode(), employee.getFullName(), policy.getName(), latest);
  }

  @Transactional
  public void remove(UUID id, String actor) {
    EmployeeScheduleAssignment assignment = assignmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Bản gán policy", id));
    assignmentRepository.delete(assignment);
    auditService.success("SCHEDULE_UNASSIGN", "EmployeeScheduleAssignment", id,
        "employeeId=" + assignment.getEmployeeId() + " from=" + assignment.getEffectiveFrom()
            + " by=" + actor);
    log.info("Gỡ bản gán policy {} của NV {}", id, assignment.getEmployeeId());
  }

}
