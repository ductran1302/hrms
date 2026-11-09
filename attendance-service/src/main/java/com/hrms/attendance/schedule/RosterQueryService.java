package com.hrms.attendance.schedule;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeAccess;
import com.hrms.attendance.employee.EmployeeAccessResolver;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.schedule.ScheduleResolver.DayPlan;
import com.hrms.attendance.schedule.dto.RosterEntryResponse;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.EmployeeScope;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
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

/**
 * Đọc lịch làm việc theo scope — <b>suy ra on-read</b> từ policy tuần (không ghi row theo ngày).
 *
 * <p>Chỉ dòng {@link RosterSource#MANUAL_OVERRIDE} được lưu DB (bảng {@code
 * employee_shift_rosters}); mọi ngày khác resolve từ {@link ScheduleResolver} tại thời điểm đọc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RosterQueryService {

  private static final int MAX_RANGE_DAYS = 62;
  private static final int MAX_EMPLOYEES = 200;
  private static final String STATUS_TERMINATED = "TERMINATED";

  private final EmployeeShiftRosterRepository rosterRepository;
  private final EmployeeRepository employeeRepository;
  private final WorkShiftRepository workShiftRepository;
  private final EmployeeAccessResolver accessResolver;
  private final ScheduleResolver resolver;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<RosterEntryResponse> list(
      String employeeId,
      Collection<String> employeeIds,
      LocalDate from,
      LocalDate to,
      String username,
      EmployeeScope scope) {
    LocalDate today = LocalDate.now();
    LocalDate start = from != null ? from : today.minusDays(7);
    LocalDate end = to != null ? to : today.plusDays(30);
    if (end.isBefore(start)) {
      throw new BusinessRuleException("'to' phải ≥ 'from'");
    }
    if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
      throw new BusinessRuleException(
          "Khoảng xem tối đa " + MAX_RANGE_DAYS + " ngày (đang yêu cầu "
              + (ChronoUnit.DAYS.between(start, end) + 1) + " ngày)");
    }
    EmployeeAccess access = scope == EmployeeScope.ALL
        ? EmployeeAccess.all() : accessResolver.resolve(username, scope);

    List<Employee> employees;
    if (employeeId != null && !employeeId.isBlank()) {
      Employee employee = employeeRepository.findById(employeeId)
          .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
      access.assertCanAccess(employee);
      employees = List.of(employee);
    } else if (employeeIds != null && !employeeIds.isEmpty()) {
      List<String> ids = employeeIds.stream()
          .filter(id -> id != null && !id.isBlank()).distinct().toList();
      if (ids.size() > MAX_EMPLOYEES) {
        throw new BusinessRuleException("Tối đa " + MAX_EMPLOYEES + " nhân viên mỗi lần xem");
      }
      employees = employeeRepository.findAllById(ids);
      employees.forEach(access::assertCanAccess);
    } else {
      employees = employeeRepository.findByStatusNotOrderByFullNameAsc(STATUS_TERMINATED).stream()
          .filter(access::canAccess)
          .toList();
    }
    if (employees.isEmpty()) {
      return List.of();
    }
    Set<String> ids = new HashSet<>();
    for (Employee employee : employees) {
      ids.add(employee.getEmployeeId());
    }
    ScheduleResolver.Context context = resolver.loadContext(ids);
    Map<String, Map<LocalDate, EmployeeShiftRoster>> overrides = new HashMap<>();
    for (EmployeeShiftRoster row : rosterRepository.findByEmployeeIdInAndWorkDateBetweenAndSource(
        ids, start, end, RosterSource.MANUAL_OVERRIDE)) {
      overrides.computeIfAbsent(row.getEmployeeId(), k -> new HashMap<>())
          .put(row.getWorkDate(), row);
    }

    List<RosterEntryResponse> out = new ArrayList<>();
    for (Employee employee : employees) {
      String id = employee.getEmployeeId();
      LocalDate hiredAt = toLocalDate(employee.getHiredAt());
      LocalDate terminatedAt = toLocalDate(employee.getTerminatedAt());
      Map<LocalDate, EmployeeShiftRoster> empOverrides = overrides.get(id);
      for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
        if ((hiredAt != null && date.isBefore(hiredAt))
            || (terminatedAt != null && date.isAfter(terminatedAt))) {
          continue;
        }
        EmployeeShiftRoster override = empOverrides == null ? null : empOverrides.get(date);
        if (override != null) {
          out.add(RosterEntryResponse.from(
              override, employee, shift(override.getShiftId(), context)));
          continue;
        }
        DayPlan plan = resolver.resolve(id, date, context);
        if (plan == null) {
          continue;
        }
        out.add(RosterEntryResponse.derived(
            id,
            employee.getEmployeeCode(),
            employee.getFullName(),
            date,
            plan,
            shift(plan.shiftId(), context)));
      }
    }
    return out;
  }

  /** 1 ngày của NV — override nếu có, ngược lại suy ra từ policy (scope-checked). */
  @Transactional(readOnly = true)
  public RosterEntryResponse get(
      String employeeId, LocalDate date, String username, EmployeeScope scope) {
    EmployeeAccess access = scope == EmployeeScope.ALL
        ? EmployeeAccess.all() : accessResolver.resolve(username, scope);
    Employee employee = employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
    access.assertCanAccess(employee);
    LocalDate hiredAt = toLocalDate(employee.getHiredAt());
    LocalDate terminatedAt = toLocalDate(employee.getTerminatedAt());
    if ((hiredAt != null && date.isBefore(hiredAt))
        || (terminatedAt != null && date.isAfter(terminatedAt))) {
      throw new ResourceNotFoundException("Lịch làm việc (ngoài thời gian làm việc của NV)");
    }
    ScheduleResolver.Context context = resolver.loadContext(List.of(employeeId));
    EmployeeShiftRoster override = rosterRepository.findByEmployeeIdAndWorkDate(employeeId, date)
        .filter(row -> row.getSource() == RosterSource.MANUAL_OVERRIDE)
        .orElse(null);
    if (override != null) {
      return RosterEntryResponse.from(
          override, employee, shift(override.getShiftId(), context));
    }
    DayPlan plan = resolver.resolve(employeeId, date, context);
    if (plan == null) {
      throw new ResourceNotFoundException(
          "Lịch làm việc (NV chưa có policy, chưa gán ca và chưa có policy mặc định)");
    }
    return RosterEntryResponse.derived(
        employeeId,
        employee.getEmployeeCode(),
        employee.getFullName(),
        date,
        plan,
        shift(plan.shiftId(), context));
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
    ScheduleResolver.Context context = resolver.loadContext(List.of(employeeId));
    DayPlan plan = resolver.resolve(employeeId, date, context);
    if (plan == null) {
      throw new ResourceNotFoundException(
          "Lịch làm việc (NV chưa có policy, chưa gán ca và chưa có policy mặc định)");
    }
    return RosterEntryResponse.derived(
        employeeId,
        employee.getEmployeeCode(),
        employee.getFullName(),
        date,
        plan,
        shift(plan.shiftId(), context));
  }

  private static WorkShift shift(UUID shiftId, ScheduleResolver.Context context) {
    return shiftId == null ? null : context.shifts().get(shiftId);
  }

  private static LocalDate toLocalDate(java.time.Instant instant) {
    return instant == null ? null : instant.atZone(ZoneOffset.UTC).toLocalDate();
  }
}
