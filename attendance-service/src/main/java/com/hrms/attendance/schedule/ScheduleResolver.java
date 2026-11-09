package com.hrms.attendance.schedule;

import com.hrms.attendance.shift.ShiftAssignment;
import com.hrms.attendance.shift.ShiftAssignmentRepository;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleResolver {

  private final EmployeeScheduleAssignmentRepository assignmentRepository;
  private final ShiftAssignmentRepository legacyAssignmentRepository;
  private final WorkSchedulePolicyRepository policyRepository;
  private final WorkShiftRepository workShiftRepository;

  public record DayPlan(UUID shiftId, boolean dayOff, RosterSource source, UUID policyId) {}

  public record Context(
      Map<String, List<EmployeeScheduleAssignment>> assignments,
      Map<String, List<ShiftAssignment>> legacy,
      Map<UUID, List<WorkSchedulePolicy>> policiesByGroup,
      List<WorkSchedulePolicy> defaultPolicies,
      Map<UUID, WorkShift> shifts) {}

  public Context loadContext(Collection<String> employeeIds) {
    Set<String> ids = new HashSet<>(employeeIds);
    Map<String, List<EmployeeScheduleAssignment>> assignments = new HashMap<>();
    for (EmployeeScheduleAssignment row
        : assignmentRepository.findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(ids)) {
      assignments.computeIfAbsent(row.getEmployeeId(), k -> new ArrayList<>()).add(row);
    }
    Map<String, List<ShiftAssignment>> legacy = new HashMap<>();
    for (ShiftAssignment row
        : legacyAssignmentRepository.findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(ids)) {
      legacy.computeIfAbsent(row.getEmployeeId(), k -> new ArrayList<>()).add(row);
    }
    Map<UUID, List<WorkSchedulePolicy>> policiesByGroup = new HashMap<>();
    for (WorkSchedulePolicy policy : policyRepository.findAll()) {
      UUID group = policy.getPolicyGroupId() == null ? policy.getId() : policy.getPolicyGroupId();
      policiesByGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(policy);
    }
    for (List<WorkSchedulePolicy> versions : policiesByGroup.values()) {
      versions.sort(Comparator.comparing(
          (WorkSchedulePolicy p) -> p.getEffectiveFrom() == null ? LocalDate.MIN : p.getEffectiveFrom())
          .reversed());
    }
    List<WorkSchedulePolicy> defaultPolicies = new ArrayList<>();
    for (WorkSchedulePolicy policy : policyRepository.findAll()) {
      if (policy.isDefaultPolicy() && policy.isActive()) {
        defaultPolicies.add(policy);
      }
    }
    defaultPolicies.sort(Comparator.comparing(
        (WorkSchedulePolicy p) -> p.getEffectiveFrom() == null ? LocalDate.MIN : p.getEffectiveFrom())
        .reversed());
    Map<UUID, WorkShift> shifts = new HashMap<>();
    for (WorkShift shift : workShiftRepository.findAll()) {
      shifts.put(shift.getId(), shift);
    }
    return new Context(assignments, legacy, policiesByGroup, defaultPolicies, shifts);
  }

  public DayPlan resolve(String employeeId, LocalDate date, Context context) {
    EmployeeScheduleAssignment assignment = latestAt(context.assignments().get(employeeId), date);
    if (assignment != null) {
      List<WorkSchedulePolicy> versions =
          context.policiesByGroup().get(assignment.getPolicyId());
      WorkSchedulePolicy policy = latestVersion(versions, date);
      if (policy != null && policy.isActive()) {
        return planFromPolicy(policy, date);
      }
    }
    ShiftAssignment legacy = latestAt(context.legacy().get(employeeId), date);
    if (legacy != null) {
      return planFromLegacy(legacy, date, latestDefault(context.defaultPolicies(), date));
    }
    WorkSchedulePolicy defaultPolicy = latestDefault(context.defaultPolicies(), date);
    if (defaultPolicy != null) {
      return planFromPolicy(defaultPolicy, date);
    }
    return null;
  }

  public Map<LocalDate, DayPlan> plans(
      String employeeId, LocalDate from, LocalDate to, Context context) {
    Map<LocalDate, DayPlan> out = new HashMap<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      DayPlan plan = resolve(employeeId, date, context);
      if (plan != null) {
        out.put(date, plan);
      }
    }
    return out;
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

  private static DayPlan planFromLegacy(
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

  static int weekIndex(LocalDate anchor, LocalDate date) {
    return (int) Math.floorDiv(ChronoUnit.DAYS.between(anchor, date), 7);
  }

  static boolean isEvenWeek(LocalDate anchor, LocalDate date) {
    return Math.floorMod(weekIndex(anchor, date), 2) == 0;
  }

  private static WorkSchedulePolicy latestVersion(List<WorkSchedulePolicy> versions, LocalDate date) {
    if (versions == null) {
      return null;
    }
    for (WorkSchedulePolicy policy : versions) {
      LocalDate from = policy.getEffectiveFrom();
      if (from == null || !from.isAfter(date)) {
        return policy;
      }
    }
    return null;
  }

  private static WorkSchedulePolicy latestDefault(List<WorkSchedulePolicy> defaults, LocalDate date) {
    for (WorkSchedulePolicy policy : defaults) {
      LocalDate from = policy.getEffectiveFrom();
      if (from == null || !from.isAfter(date)) {
        return policy;
      }
    }
    return null;
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
}
