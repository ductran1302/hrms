package com.hrms.attendance.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hrms.attendance.schedule.ScheduleResolver.DayPlan;
import com.hrms.attendance.shift.ShiftAssignment;
import com.hrms.attendance.shift.ShiftAssignmentRepository;
import com.hrms.attendance.shift.ShiftType;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Resolve lịch từ policy tuần — thuần tính, không ghi DB. */
@ExtendWith(MockitoExtension.class)
class ScheduleResolverTest {

  @Mock private EmployeeScheduleAssignmentRepository assignmentRepository;
  @Mock private ShiftAssignmentRepository legacyAssignmentRepository;
  @Mock private WorkSchedulePolicyRepository policyRepository;
  @Mock private WorkShiftRepository workShiftRepository;

  @InjectMocks private ScheduleResolver resolver;

  private WorkShift fullShift;
  private WorkShift otherShift;
  private WorkSchedulePolicy defaultPolicy;

  @BeforeEach
  void setUp() {
    fullShift = shift("Ca hành chính");
    otherShift = shift("Ca khác");

    defaultPolicy = new WorkSchedulePolicy();
    defaultPolicy.setId(UUID.randomUUID());
    defaultPolicy.setName("Chuẩn");
    defaultPolicy.setMonShiftId(fullShift.getId());
    defaultPolicy.setTueShiftId(fullShift.getId());
    defaultPolicy.setWedShiftId(fullShift.getId());
    defaultPolicy.setThuShiftId(fullShift.getId());
    defaultPolicy.setFriShiftId(fullShift.getId());
    defaultPolicy.setDefaultPolicy(true);
    defaultPolicy.setActive(true);
  }

  private void stubDefaultContext() {
    lenient().when(assignmentRepository
            .findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(any()))
        .thenReturn(List.of());
    lenient().when(legacyAssignmentRepository
            .findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(any()))
        .thenReturn(List.of());
    lenient().when(policyRepository.findAll()).thenReturn(List.of(defaultPolicy));
    lenient().when(policyRepository.findFirstByDefaultPolicyTrueAndActiveTrue())
        .thenReturn(Optional.of(defaultPolicy));
    lenient().when(workShiftRepository.findAll()).thenReturn(List.of(fullShift, otherShift));
  }

  @Test
  void defaultPolicy_resolvesWeekMonToSun() {
    stubDefaultContext();
    ScheduleResolver.Context context = resolver.loadContext(List.of("e-1"));
    LocalDate monday = nextMonday();

    for (int i = 0; i < 5; i++) {
      DayPlan plan = resolver.resolve("e-1", monday.plusDays(i), context);
      assertEquals(fullShift.getId(), plan.shiftId(), "T2–T6 dùng ca");
      assertFalse(plan.dayOff());
      assertEquals(RosterSource.POLICY_AUTO, plan.source());
      assertEquals(defaultPolicy.getId(), plan.policyId());
    }
    DayPlan saturday = resolver.resolve("e-1", monday.plusDays(5), context);
    assertTrue(saturday.dayOff(), "T7 không khai báo → nghỉ");
    assertNull(saturday.shiftId());
    DayPlan sunday = resolver.resolve("e-1", monday.plusDays(6), context);
    assertTrue(sunday.dayOff(), "CN không khai báo → nghỉ");
  }

  @Test
  void assignment_overridesDefaultPolicy() {
    WorkSchedulePolicy custom = new WorkSchedulePolicy();
    custom.setId(UUID.randomUUID());
    custom.setName("Chỉ thứ 2");
    custom.setMonShiftId(otherShift.getId());
    custom.setDefaultPolicy(false);
    custom.setActive(true);

    LocalDate monday = nextMonday();
    EmployeeScheduleAssignment assignment = new EmployeeScheduleAssignment();
    assignment.setId(UUID.randomUUID());
    assignment.setEmployeeId("e-1");
    assignment.setPolicyId(custom.getId());
    assignment.setEffectiveFrom(monday.minusDays(30));

    stubDefaultContext();
    when(policyRepository.findAll()).thenReturn(List.of(defaultPolicy, custom));
    when(assignmentRepository
        .findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(any()))
        .thenReturn(List.of(assignment));

    DayPlan plan = resolver.resolve("e-1", monday, resolver.loadContext(List.of("e-1")));

    assertEquals(otherShift.getId(), plan.shiftId());
    assertEquals(custom.getId(), plan.policyId());
  }

  @Test
  void assignmentToInactivePolicy_fallsBackToDefault() {
    WorkSchedulePolicy inactive = new WorkSchedulePolicy();
    inactive.setId(UUID.randomUUID());
    inactive.setName("Ngưng dùng");
    inactive.setMonShiftId(otherShift.getId());
    inactive.setActive(false);

    LocalDate monday = nextMonday();
    EmployeeScheduleAssignment assignment = new EmployeeScheduleAssignment();
    assignment.setId(UUID.randomUUID());
    assignment.setEmployeeId("e-1");
    assignment.setPolicyId(inactive.getId());
    assignment.setEffectiveFrom(monday.minusDays(30));

    stubDefaultContext();
    when(policyRepository.findAll()).thenReturn(List.of(defaultPolicy, inactive));
    when(assignmentRepository
        .findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(any()))
        .thenReturn(List.of(assignment));

    DayPlan plan = resolver.resolve("e-1", monday, resolver.loadContext(List.of("e-1")));

    assertEquals(fullShift.getId(), plan.shiftId(), "policy ngưng dùng → bỏ qua, dùng default");
    assertEquals(defaultPolicy.getId(), plan.policyId());
  }

  @Test
  void legacyAssignment_weekdayUsesLegacySaturdayOff() {
    LocalDate monday = nextMonday();
    ShiftAssignment legacy = new ShiftAssignment();
    legacy.setId(UUID.randomUUID());
    legacy.setEmployeeId("e-1");
    legacy.setWorkShiftId(otherShift.getId());
    legacy.setEffectiveFrom(monday.minusDays(60));

    stubDefaultContext();
    when(legacyAssignmentRepository
            .findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(any()))
        .thenReturn(List.of(legacy));

    ScheduleResolver.Context context = resolver.loadContext(List.of("e-1"));
    DayPlan weekday = resolver.resolve("e-1", monday, context);
    assertEquals(otherShift.getId(), weekday.shiftId());
    assertEquals(RosterSource.LEGACY_ASSIGNMENT, weekday.source());

    DayPlan saturday = resolver.resolve("e-1", monday.plusDays(5), context);
    assertTrue(saturday.dayOff(), "T7 theo default policy (không khai báo) → nghỉ");
    assertEquals(RosterSource.LEGACY_ASSIGNMENT, saturday.source());

    DayPlan sunday = resolver.resolve("e-1", monday.plusDays(6), context);
    assertTrue(sunday.dayOff(), "CN luôn nghỉ với legacy");
  }

  @Test
  void noPolicyNoAssignment_returnsNull() {
    lenient().when(assignmentRepository
            .findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(any()))
        .thenReturn(List.of());
    lenient().when(legacyAssignmentRepository
            .findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(any()))
        .thenReturn(List.of());
    lenient().when(policyRepository.findAll()).thenReturn(List.of());
    lenient().when(policyRepository.findFirstByDefaultPolicyTrueAndActiveTrue())
        .thenReturn(Optional.empty());
    lenient().when(workShiftRepository.findAll()).thenReturn(List.of());

    DayPlan plan = resolver.resolve("e-1", nextMonday(), resolver.loadContext(List.of("e-1")));

    assertNull(plan, "không có nguồn nào → null (caller coi như chưa có lịch)");
  }

  @Test
  void plans_returnsOnlyResolvableDays() {
    stubDefaultContext();
    LocalDate monday = nextMonday();
    ScheduleResolver.Context context = resolver.loadContext(List.of("e-1"));

    Map<LocalDate, DayPlan> plans = resolver.plans("e-1", monday, monday.plusDays(6), context);

    assertEquals(7, plans.size(), "có policy → phủ đủ 7 ngày (T7/CN dayOff)");
    assertTrue(plans.get(monday.plusDays(5)).dayOff());
    assertEquals(DayOfWeek.SUNDAY, monday.plusDays(6).getDayOfWeek());
  }

  private static LocalDate nextMonday() {
    return LocalDate.now()
        .with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(4);
  }

  private static WorkShift shift(String name) {
    WorkShift shift = new WorkShift();
    shift.setId(UUID.randomUUID());
    shift.setName(name);
    shift.setShiftType(ShiftType.FIXED);
    shift.setStartWorkTime(LocalTime.of(8, 0));
    shift.setEndWorkTime(LocalTime.of(17, 0));
    shift.setStartBreakTime(LocalTime.of(12, 0));
    shift.setEndBreakTime(LocalTime.of(13, 0));
    shift.setWorkingTime(480);
    shift.setBreakingTime(60);
    shift.setActive(true);
    return shift;
  }
}
