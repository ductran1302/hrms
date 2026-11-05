package com.hrms.attendance.schedule;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeAccess;
import com.hrms.attendance.employee.EmployeeAccessResolver;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.schedule.dto.RosterEntryResponse;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.EmployeeScope;
import java.time.LocalDate;
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

/** Đọc roster theo scope + sửa tay (MANUAL_OVERRIDE) cho tương lai. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RosterQueryService {

  private final EmployeeShiftRosterRepository rosterRepository;
  private final EmployeeRepository employeeRepository;
  private final WorkShiftRepository workShiftRepository;
  private final EmployeeAccessResolver accessResolver;
  private final RosterGenerationService generationService;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<RosterEntryResponse> list(
      String employeeId, LocalDate from, LocalDate to, String username, EmployeeScope scope) {
    LocalDate today = LocalDate.now();
    LocalDate start = from != null ? from : today.minusDays(7);
    LocalDate end = to != null ? to : today.plusDays(30);
    if (end.isBefore(start)) {
      throw new BusinessRuleException("'to' phải ≥ 'from'");
    }
    EmployeeAccess access = scope == EmployeeScope.ALL
        ? EmployeeAccess.all() : accessResolver.resolve(username, scope);

    List<Employee> employees;
    if (employeeId != null && !employeeId.isBlank()) {
      Employee employee = employeeRepository.findById(employeeId)
          .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
      access.assertCanAccess(employee);
      employees = List.of(employee);
    } else {
      employees = employeeRepository.findAll().stream()
          .filter(access::canAccess)
          .toList();
    }
    if (employees.isEmpty()) {
      return List.of();
    }
    Set<String> ids = new HashSet<>();
    Map<String, Employee> byId = new HashMap<>();
    for (Employee employee : employees) {
      ids.add(employee.getEmployeeId());
      byId.put(employee.getEmployeeId(), employee);
    }
    Map<UUID, WorkShift> shifts = new HashMap<>();
    for (WorkShift shift : workShiftRepository.findAll()) {
      shifts.put(shift.getId(), shift);
    }
    return rosterRepository.findByEmployeeIdInAndWorkDateBetween(ids, start, end).stream()
        .map(row -> RosterEntryResponse.from(
            row,
            byId.get(row.getEmployeeId()),
            row.getShiftId() == null ? null : shifts.get(row.getShiftId())))
        .toList();
  }

  /** 1 ngày của NV — lazy resolve nếu chưa có (scope-checked). */
  @Transactional
  public RosterEntryResponse get(
      String employeeId, LocalDate date, String username, EmployeeScope scope) {
    EmployeeAccess access = scope == EmployeeScope.ALL
        ? EmployeeAccess.all() : accessResolver.resolve(username, scope);
    Employee employee = employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
    access.assertCanAccess(employee);
    return generationService.resolve(employeeId, date);
  }

  @Transactional
  public RosterEntryResponse override(
      String employeeId, LocalDate date, UUID shiftId, String note, String actor) {
    Employee employee = employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
    if (date.isBefore(LocalDate.now())) {
      throw new BusinessRuleException("Chỉ sửa tay lịch từ hôm nay — quá khứ đã chốt");
    }
    WorkShift shift = null;
    if (shiftId != null) {
      shift = workShiftRepository.findById(shiftId)
          .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", shiftId));
      if (!shift.isActive()) {
        throw new BusinessRuleException("Ca '" + shift.getName() + "' đã ngưng dùng");
      }
    }
    EmployeeShiftRoster row = rosterRepository.findByEmployeeIdAndWorkDate(employeeId, date)
        .orElseGet(() -> {
          EmployeeShiftRoster created = new EmployeeShiftRoster();
          created.setEmployeeId(employeeId);
          created.setWorkDate(date);
          return created;
        });
    row.setShiftId(shiftId);
    row.setDayOff(shiftId == null);
    row.setSource(RosterSource.MANUAL_OVERRIDE);
    row.setPolicyId(null);
    row.setNote(note);
    rosterRepository.saveAndFlush(row);
    auditService.success("ROSTER_OVERRIDE", "EmployeeShiftRoster", row.getId(),
        "employeeId=" + employeeId + " date=" + date + " shiftId=" + shiftId + " by=" + actor);
    log.info("Sửa tay lịch NV {} ngày {} → ca {}", employeeId, date, shiftId);
    return RosterEntryResponse.from(row, employee, shift);
  }

  @Transactional
  public RosterEntryResponse clearOverride(String employeeId, LocalDate date, String actor) {
    Employee employee = employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
    if (date.isBefore(LocalDate.now())) {
      throw new BusinessRuleException("Chỉ sửa tay lịch từ hôm nay — quá khứ đã chốt");
    }
    EmployeeShiftRoster row = rosterRepository.findByEmployeeIdAndWorkDate(employeeId, date)
        .orElseThrow(() -> new ResourceNotFoundException("Roster", employeeId + " " + date));
    if (row.getSource() != RosterSource.MANUAL_OVERRIDE) {
      throw new BusinessRuleException("Ngày này chưa có sửa tay (source=" + row.getSource() + ")");
    }
    rosterRepository.delete(row);
    rosterRepository.flush();
    auditService.success("ROSTER_OVERRIDE_CLEAR", "EmployeeShiftRoster", row.getId(),
        "employeeId=" + employeeId + " date=" + date + " by=" + actor);
    log.info("Bỏ sửa tay lịch NV {} ngày {}", employeeId, date);
    return generationService.resolve(employeeId, date);
  }
}
