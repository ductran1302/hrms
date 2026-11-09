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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolve lịch làm việc của 1 nhân viên tại 1 ngày từ <b>policy tuần</b> — thuần tính, KHÔNG ghi DB.
 *
 * <p>Thứ tự ưu tiên (fail-closed):
 *
 * <ol>
 *   <li>assignment mới nhất ≤ ngày + policy active;
 *   <li>legacy {@code shift_assignments} mới nhất ≤ ngày (T2–T6 dùng ca, T7 theo default policy,
 *       CN nghỉ) — giữ khớp dữ liệu cũ;
 *   <li>policy {@code is_default} active;
 *   <li>không có → {@code null} (caller coi như chưa có lịch).
 * </ol>
 *
 * <p>Sửa tay ({@code MANUAL_OVERRIDE}) nằm ở bảng {@code employee_shift_rosters} — caller đọc và
 * ưu tiên trước khi gọi resolver.
 */
@Component
@RequiredArgsConstructor
public class ScheduleResolver {

  private final EmployeeScheduleAssignmentRepository assignmentRepository;
  private final ShiftAssignmentRepository legacyAssignmentRepository;
  private final WorkSchedulePolicyRepository policyRepository;
  private final WorkShiftRepository workShiftRepository;

  /** Một ngày đã resolve ({@code shiftId} null = nghỉ). */
  public record DayPlan(UUID shiftId, boolean dayOff, RosterSource source, UUID policyId) {}

  /** Dữ liệu nạp 1 lần cho nhiều lần resolve (tránh N+1). */
  public record Context(
      Map<String, List<EmployeeScheduleAssignment>> assignments,
      Map<String, List<ShiftAssignment>> legacy,
      Map<UUID, WorkSchedulePolicy> policies,
      WorkSchedulePolicy defaultPolicy,
      Map<UUID, WorkShift> shifts) {}

  /** Nạp context cho tập nhân viên (mọi policy/ca). */
  public Context loadContext(Collection<String> employeeIds) {
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

  /** Lịch 1 ngày — {@code null} khi NV chưa có policy/assignment/ca cũ. */
  public DayPlan resolve(String employeeId, LocalDate date, Context context) {
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

  /** Map ngày → plan trong {@code [from..to]} — bỏ ngày không resolve được. */
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
}
