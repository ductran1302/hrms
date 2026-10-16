package com.hrms.attendance.shift;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.shift.dto.ShiftAssignmentRequest;
import com.hrms.attendance.shift.dto.ShiftAssignmentResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gán ca cho nhân viên theo ngày hiệu lực — mỗi lần gán 1 dòng mới (giữ lịch sử).
 * Quy tắc: ngày hiệu lực phải sau bản trước; chỉ gỡ được bản mới nhất;
 * module chấm công sau đọc findAssignmentFor(employeeId, date).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftAssignmentService {

  private final ShiftAssignmentRepository assignmentRepository;
  private final WorkShiftRepository workShiftRepository;
  private final EmployeeRepository employeeRepository;

  @Transactional(readOnly = true)
  public List<ShiftAssignmentResponse> list(String employeeId, String q) {
    List<ShiftAssignment> assignments =
        employeeId != null && !employeeId.isBlank()
            ? assignmentRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId)
            : assignmentRepository.findAllByOrderByEffectiveFromDesc();
    if (assignments.isEmpty()) {
      return List.of();
    }

    Map<String, ShiftAssignment> latestByEmployee = new HashMap<>();
    for (ShiftAssignment assignment : assignments) {
      latestByEmployee.merge(
          assignment.getEmployeeId(),
          assignment,
          (a, b) -> a.getEffectiveFrom().isAfter(b.getEffectiveFrom()) ? a : b);
    }

    Set<String> employeeIds = new HashSet<>();
    Set<UUID> shiftIds = new HashSet<>();
    for (ShiftAssignment assignment : assignments) {
      employeeIds.add(assignment.getEmployeeId());
      shiftIds.add(assignment.getWorkShiftId());
    }
    Map<String, Employee> employees = new HashMap<>();
    employeeRepository.findAllById(employeeIds)
        .forEach(e -> employees.put(e.getEmployeeId(), e));
    Map<UUID, WorkShift> shifts = new HashMap<>();
    workShiftRepository.findAllById(shiftIds).forEach(s -> shifts.put(s.getId(), s));

    String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    List<ShiftAssignmentResponse> result = new ArrayList<>();
    for (ShiftAssignment assignment : assignments) {
      Employee employee = employees.get(assignment.getEmployeeId());
      WorkShift shift = shifts.get(assignment.getWorkShiftId());
      if (employee == null || shift == null) {
        continue;
      }
      if (!keyword.isEmpty()
          && !matches(employee.getEmployeeCode(), keyword)
          && !matches(employee.getFullName(), keyword)
          && !matches(employee.getDepartmentName(), keyword)) {
        continue;
      }
      boolean latest =
          latestByEmployee.get(assignment.getEmployeeId()).getId().equals(assignment.getId());
      result.add(ShiftAssignmentResponse.from(
          assignment,
          employee.getEmployeeCode(),
          employee.getFullName(),
          employee.getDepartmentName(),
          shift.getName(),
          shift.getShiftType().name(),
          latest));
    }
    return result;
  }

  @Transactional
  public ShiftAssignmentResponse assign(ShiftAssignmentRequest request, String actorId) {
    Employee employee = employeeRepository.findById(request.employeeId())
        .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", request.employeeId()));
    WorkShift shift = workShiftRepository.findById(request.workShiftId())
        .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", request.workShiftId()));
    if (!shift.isActive()) {
      throw new BusinessRuleException("Ca '" + shift.getName() + "' đã ngưng hoạt động");
    }
    LocalDate from = request.effectiveFrom();
    Optional<ShiftAssignment> previous =
        assignmentRepository.findTopByEmployeeIdOrderByEffectiveFromDesc(employee.getEmployeeId());
    if (previous.isPresent() && !from.isAfter(previous.get().getEffectiveFrom())) {
      throw new BusinessRuleException(
          "Ngày hiệu lực phải sau bản gán trước ("
              + previous.get().getEffectiveFrom()
              + ") — mỗi ngày chỉ 1 ca áp dụng");
    }
    ShiftAssignment assignment = new ShiftAssignment();
    assignment.setId(UUID.randomUUID());
    assignment.setEmployeeId(employee.getEmployeeId());
    assignment.setWorkShiftId(shift.getId());
    assignment.setEffectiveFrom(from);
    assignment.setAssignedBy(actorId);
    assignmentRepository.save(assignment);
    log.info("Gán ca '{}' cho NV {} từ {}", shift.getName(), employee.getEmployeeCode(), from);
    return ShiftAssignmentResponse.from(
        assignment,
        employee.getEmployeeCode(),
        employee.getFullName(),
        employee.getDepartmentName(),
        shift.getName(),
        shift.getShiftType().name(),
        true);
  }

  @Transactional
  public void remove(UUID id) {
    ShiftAssignment assignment = assignmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Gán ca", id));
    Optional<ShiftAssignment> latest =
        assignmentRepository.findTopByEmployeeIdOrderByEffectiveFromDesc(assignment.getEmployeeId());
    if (latest.isPresent() && !latest.get().getId().equals(id)) {
      throw new BusinessRuleException(
          "Chỉ gỡ được bản gán mới nhất — bản kế tiếp đã hiệu lực từ " + latest.get().getEffectiveFrom());
    }
    assignmentRepository.delete(assignment);
    log.info("Gỡ gán ca của NV {} (từ {})", assignment.getEmployeeId(), assignment.getEffectiveFrom());
  }

  /** Ca của chính mình tại 1 ngày (mặc định hôm nay) — FE trang 'Quyền phép/Quản lý của tôi'. */
  @Transactional(readOnly = true)
  public ShiftAssignmentResponse getMy(String username, LocalDate date) {
    LocalDate on = date != null ? date : LocalDate.now();
    Employee employee = employeeRepository.findByEmployeeCode(username)
        .or(() -> employeeRepository.findById(username))
        .orElseThrow(() -> new ResourceNotFoundException(
            "Nhân viên (username " + username + ")"));
    ShiftAssignment assignment = assignmentRepository
        .findTopByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            employee.getEmployeeId(), on)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Chưa được gán ca (từ " + on + ") — username " + username));
    WorkShift shift = workShiftRepository.findById(assignment.getWorkShiftId())
        .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", assignment.getWorkShiftId()));
    boolean latest = assignmentRepository
        .findTopByEmployeeIdOrderByEffectiveFromDesc(employee.getEmployeeId())
        .map(l -> l.getId().equals(assignment.getId()))
        .orElse(true);
    return ShiftAssignmentResponse.from(
        assignment,
        employee.getEmployeeCode(),
        employee.getFullName(),
        employee.getDepartmentName(),
        shift.getName(),
        shift.getShiftType().name(),
        latest);
  }

  private static boolean matches(String value, String keyword) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
  }
}
