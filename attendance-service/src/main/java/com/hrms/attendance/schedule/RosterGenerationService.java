package com.hrms.attendance.schedule;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.schedule.dto.GenerateRosterResponse;
import com.hrms.attendance.schedule.dto.RosterEntryResponse;
import com.hrms.attendance.shift.ShiftAssignment;
import com.hrms.attendance.shift.ShiftAssignmentRepository;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import java.time.DayOfWeek;
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
 * Sinh roster (cache) từ 3 tầng: policy assignment &gt; legacy shift_assignments &gt; default policy.
 *
 * <p>Quy tắc: work_date = ngày bắt đầu ca; ngày &lt; hôm nay coi như đã chốt (không sửa bản đã
 * có); MANUAL_OVERRIDE không bao giờ bị ghi đè; chạy lại idempotent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RosterGenerationService {

  private static final int MAX_RANGE_DAYS = 3660;

  private final EmployeeRepository employeeRepository;
  private final EmployeeScheduleAssignmentRepository assignmentRepository;
  private final EmployeeShiftRosterRepository rosterRepository;
  private final ShiftAssignmentRepository legacyAssignmentRepository;
  private final WorkSchedulePolicyRepository policyRepository;
  private final WorkShiftRepository workShiftRepository;
  private final RosterProperties properties;

  /** Một ngày đã resolve (shift null = nghỉ). */
  record DayPlan(UUID shiftId, boolean dayOff, RosterSource source, UUID policyId) {}

  @Transactional
  public GenerateRosterResponse generate(
      LocalDate from, LocalDate to, String employeeId, String actor) {
    LocalDate today = LocalDate.now();
    LocalDate start = from != null ? from : today.minusDays(properties.getPastDays());
    LocalDate end = to != null ? to : today.plusDays(properties.getFutureDays());
    if (end.isBefore(start)) {
      throw new BusinessRuleException("'to' phải ≥ 'from'");
    }
    if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
      throw new BusinessRuleException("Khoảng sinh roster quá lớn (tối đa " + MAX_RANGE_DAYS + " ngày)");
    }

    List<Employee> employees;
    if (employeeId != null && !employeeId.isBlank()) {
      Employee employee = employeeRepository.findById(employeeId)
          .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
      employees = List.of(employee);
    } else {
      employees = employeeRepository.findAll().stream()
          .filter(e -> e.getStatus() == null || !"TERMINATED".equalsIgnoreCase(e.getStatus()))
          .toList();
    }
    if (employees.isEmpty()) {
      return new GenerateRosterResponse(start, end, 0, 0, 0, 0, 0, 0);
    }

    Context context = loadContext(employees.stream().map(Employee::getEmployeeId).toList());
    Map<String, EmployeeShiftRoster> existing = new HashMap<>();
    for (EmployeeShiftRoster row : rosterRepository.findByEmployeeIdInAndWorkDateBetween(
        employees.stream().map(Employee::getEmployeeId).toList(), start, end)) {
      existing.put(key(row.getEmployeeId(), row.getWorkDate()), row);
    }

    int created = 0;
    int updated = 0;
    int unchanged = 0;
    int skipped = 0;
    int noPolicy = 0;
    List<EmployeeShiftRoster> pending = new ArrayList<>();
    int batchSize = Math.max(1, properties.getBatchSize());

    for (Employee employee : employees) {
      LocalDate hiredAt = toLocalDate(employee.getHiredAt());
      LocalDate terminatedAt = toLocalDate(employee.getTerminatedAt());
      for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
        if ((hiredAt != null && date.isBefore(hiredAt))
            || (terminatedAt != null && date.isAfter(terminatedAt))) {
          skipped++;
          continue;
        }
        EmployeeShiftRoster current = existing.get(key(employee.getEmployeeId(), date));
        if (current != null
            && (date.isBefore(today) || current.getSource() == RosterSource.MANUAL_OVERRIDE)) {
          skipped++;
          continue;
        }
        DayPlan plan = planFor(employee.getEmployeeId(), date, context);
        if (plan == null) {
          noPolicy++;
          continue;
        }
        if (current == null) {
          EmployeeShiftRoster row = new EmployeeShiftRoster();
          row.setEmployeeId(employee.getEmployeeId());
          row.setWorkDate(date);
          apply(row, plan);
          pending.add(row);
          created++;
        } else if (!java.util.Objects.equals(current.getShiftId(), plan.shiftId())
            || current.isDayOff() != plan.dayOff()
            || current.getSource() != plan.source()
            || !java.util.Objects.equals(current.getPolicyId(), plan.policyId())) {
          apply(current, plan);
          pending.add(current);
          updated++;
        } else {
          unchanged++;
        }
        if (pending.size() >= batchSize) {
          rosterRepository.saveAll(pending);
          rosterRepository.flush();
          pending.clear();
        }
      }
    }
    if (!pending.isEmpty()) {
      rosterRepository.saveAll(pending);
      rosterRepository.flush();
    }
    log.info("Sinh roster [{} → {}] NV={} created={} updated={} unchanged={} skipped={} noPolicy={} by={}",
        start, end, employees.size(), created, updated, unchanged, skipped, noPolicy, actor);
    return new GenerateRosterResponse(
        start, end, employees.size(), created, updated, unchanged, skipped, noPolicy);
  }

  /** Resolve 1 ngày — có roster thì trả, thiếu thì tính + ghi (lazy, dùng cho Phase 2). */
  @Transactional
  public RosterEntryResponse resolve(String employeeId, LocalDate date) {
    Employee employee = employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", employeeId));
    Context context = loadContext(List.of(employeeId));
    EmployeeShiftRoster row = rosterRepository.findByEmployeeIdAndWorkDate(employeeId, date)
        .orElse(null);
    if (row == null) {
      DayPlan plan = planFor(employeeId, date, context);
      if (plan == null) {
        throw new ResourceNotFoundException(
            "Lịch làm việc (NV chưa có policy, chưa gán ca và chưa có policy mặc định)");
      }
      row = new EmployeeShiftRoster();
      row.setEmployeeId(employeeId);
      row.setWorkDate(date);
      apply(row, plan);
      rosterRepository.saveAndFlush(row);
    }
    return toResponse(row, employee, context.shifts());
  }

  private void apply(EmployeeShiftRoster row, DayPlan plan) {
    row.setShiftId(plan.shiftId());
    row.setDayOff(plan.dayOff());
    row.setSource(plan.source());
    row.setPolicyId(plan.policyId());
  }

  // ---- resolve tầng ----

  private DayPlan planFor(String employeeId, LocalDate date, Context context) {
    EmployeeScheduleAssignment assignment = latestAt(context.assignments().get(employeeId), date);
    if (assignment != null) {
      WorkSchedulePolicy policy = context.policies().get(assignment.getPolicyId());
      if (policy != null && policy.isActive()) {
        return planFromPolicy(policy, date);
      }
    }
    ShiftAssignment legacy = latestAt(context.legacy().get(employeeId), date);
    if (legacy != null) {
      return planFromLegacy(legacy, date, context.defaultPolicy());
    }
    if (context.defaultPolicy() != null) {
      return planFromPolicy(context.defaultPolicy(), date);
    }
    return null;
  }

  static DayPlan planFromPolicy(WorkSchedulePolicy policy, LocalDate date) {
    DayOfWeek dow = date.getDayOfWeek();
    if (dow == DayOfWeek.SATURDAY && policy.getRotationAnchorDate() != null) {
      UUID shiftId = isEvenWeek(policy.getRotationAnchorDate(), date)
          ? policy.getSatShiftId() : policy.getSatAlternateShiftId();
      return new DayPlan(shiftId, shiftId == null, RosterSource.ROTATION_GEN, policy.getId());
    }
    UUID shiftId;
    switch (dow) {
      case MONDAY -> shiftId = policy.getMonShiftId();
      case TUESDAY -> shiftId = policy.getTueShiftId();
      case WEDNESDAY -> shiftId = policy.getWedShiftId();
      case THURSDAY -> shiftId = policy.getThuShiftId();
      case FRIDAY -> shiftId = policy.getFriShiftId();
      case SATURDAY -> shiftId = policy.getSatShiftId();
      default -> shiftId = policy.getSunShiftId();
    }
    return new DayPlan(shiftId, shiftId == null, RosterSource.POLICY_AUTO, policy.getId());
  }

  private DayPlan planFromLegacy(
      ShiftAssignment legacy, LocalDate date, WorkSchedulePolicy defaultPolicy) {
    DayOfWeek dow = date.getDayOfWeek();
    UUID defaultId = defaultPolicy == null ? null : defaultPolicy.getId();
    if (dow == DayOfWeek.SATURDAY) {
      if (defaultPolicy == null) {
        return new DayPlan(null, true, RosterSource.LEGACY_ASSIGNMENT, null);
      }
      DayPlan saturday = planFromPolicy(defaultPolicy, date);
      return new DayPlan(saturday.shiftId(), saturday.dayOff(),
          RosterSource.LEGACY_ASSIGNMENT, defaultId);
    }
    if (dow == DayOfWeek.SUNDAY) {
      return new DayPlan(null, true, RosterSource.LEGACY_ASSIGNMENT, defaultId);
    }
    return new DayPlan(legacy.getWorkShiftId(), false, RosterSource.LEGACY_ASSIGNMENT, defaultId);
  }

  /** Số tuần kể từ anchor (floor, đúng cả trước anchor) — parity chẵn/lẻ. */
  static int weekIndex(LocalDate anchor, LocalDate date) {
    return (int) Math.floorDiv(ChronoUnit.DAYS.between(anchor, date), 7);
  }

  static boolean isEvenWeek(LocalDate anchor, LocalDate date) {
    return Math.floorMod(weekIndex(anchor, date), 2) == 0;
  }

  private static <T> T latestAt(List<T> rows, LocalDate date) {
    if (rows == null) {
      return null;
    }
    for (T row : rows) {
      LocalDate effectiveFrom = row instanceof EmployeeScheduleAssignment a
          ? a.getEffectiveFrom() : ((ShiftAssignment) row).getEffectiveFrom();
      if (!effectiveFrom.isAfter(date)) {
        return row;
      }
    }
    return null;
  }

  private Context loadContext(Collection<String> employeeIds) {
    Set<String> ids = new HashSet<>(employeeIds);
    Map<String, List<EmployeeScheduleAssignment>> assignments = new HashMap<>();
    for (EmployeeScheduleAssignment row
        : assignmentRepository.findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(ids)) {
      assignments.computeIfAbsent(row.getEmployeeId(), k -> new ArrayList<>()).add(row);
    }
    Map<String, List<ShiftAssignment>> legacy = new HashMap<>();
    for (ShiftAssignment row : legacyAssignmentRepository.findAllByOrderByEffectiveFromDesc()) {
      if (ids.contains(row.getEmployeeId())) {
        legacy.computeIfAbsent(row.getEmployeeId(), k -> new ArrayList<>()).add(row);
      }
    }
    Map<UUID, WorkSchedulePolicy> policies = new HashMap<>();
    for (WorkSchedulePolicy policy : policyRepository.findAll()) {
      policies.put(policy.getId(), policy);
    }
    Map<UUID, WorkShift> shifts = new HashMap<>();
    for (WorkShift shift : workShiftRepository.findAll()) {
      shifts.put(shift.getId(), shift);
    }
    WorkSchedulePolicy defaultPolicy =
        policyRepository.findFirstByDefaultPolicyTrueAndActiveTrue().orElse(null);
    return new Context(assignments, legacy, policies, defaultPolicy, shifts);
  }

  private RosterEntryResponse toResponse(
      EmployeeShiftRoster row, Employee employee, Map<UUID, WorkShift> shifts) {
    return RosterEntryResponse.from(
        row, employee, row.getShiftId() == null ? null : shifts.get(row.getShiftId()));
  }

  private record Context(
      Map<String, List<EmployeeScheduleAssignment>> assignments,
      Map<String, List<ShiftAssignment>> legacy,
      Map<UUID, WorkSchedulePolicy> policies,
      WorkSchedulePolicy defaultPolicy,
      Map<UUID, WorkShift> shifts) {}

  private static String key(String employeeId, LocalDate date) {
    return employeeId + "|" + date;
  }

  private static LocalDate toLocalDate(java.time.Instant instant) {
    return instant == null ? null : instant.atZone(ZoneOffset.UTC).toLocalDate();
  }
}
