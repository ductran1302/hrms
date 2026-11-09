package com.hrms.attendance.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hrms.attendance.schedule.EmployeeShiftRoster;
import com.hrms.attendance.schedule.EmployeeShiftRosterRepository;
import com.hrms.attendance.schedule.RosterSource;
import com.hrms.attendance.schedule.ScheduleResolver;
import com.hrms.attendance.schedule.ScheduleResolver.DayPlan;
import com.hrms.attendance.shift.ShiftType;
import com.hrms.attendance.shift.WorkShift;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RosterWorkDaySourceTest {

  @Mock private ScheduleResolver resolver;
  @Mock private EmployeeShiftRosterRepository rosterRepository;

  @InjectMocks private RosterWorkDaySource source;

  private static final LocalDate MON = LocalDate.of(2026, 1, 5); // Thứ 2
  private static final LocalDate FY_START = LocalDate.of(2026, 1, 1);

  private LeaveSettings settings;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(source, "standardWorkMinutes", 480);
    settings = new LeaveSettings();
    settings.setId(UUID.randomUUID());
    settings.setFyStartMonth(1);
    settings.setFyStartDay(1);
    settings.setPeriodStartDay(1);
    settings.setPeriodEndDay(31);
    settings.setSaturdayPolicy(SaturdayPolicy.OFF);
    settings.setMinWorkPercent(new BigDecimal("50"));
    lenient().when(rosterRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            any(), any(), any()))
        .thenReturn(List.of());
  }

  @Test
  void weights_noPolicy_returnsEmptyMap_callerFallsBack() {
    when(resolver.loadContext(any())).thenReturn(context(Map.of()));
    when(resolver.plans(eq("e-1"), eq(MON), eq(MON.plusDays(6)), any()))
        .thenReturn(Map.of());
    when(rosterRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            eq("e-1"), any(), any()))
        .thenReturn(List.of());

    Map<LocalDate, BigDecimal> weights = source.weights("e-1", MON, MON.plusDays(6));

    assertTrue(weights.isEmpty(), "không có policy → caller fallback PeriodEngine");
  }

  @Test
  void weights_manualOverride_beatsPlan() {
    WorkShift full = shift(480);
    WorkShift half = shift(240);
    Map<UUID, WorkShift> shifts = Map.of(full.getId(), full, half.getId(), half);

    when(resolver.loadContext(any())).thenReturn(context(shifts));
    when(resolver.plans(eq("e-1"), eq(MON), eq(MON.plusDays(1)), any()))
        .thenReturn(Map.of(
            MON, plan(full.getId(), false),
            MON.plusDays(1), plan(full.getId(), false)));
    when(rosterRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            eq("e-1"), any(), any()))
        .thenReturn(List.of(
            override(MON, null, true),
            override(MON.plusDays(1), half.getId(), false)));

    Map<LocalDate, BigDecimal> w = source.weights("e-1", MON, MON.plusDays(1));

    assertWeight(BigDecimal.ZERO, w.get(MON), "override nghỉ thắng plan đi làm");
    assertWeight(new BigDecimal("0.5"), w.get(MON.plusDays(1)), "override ca nửa ngày");
  }

  @Test
  void weights_byShiftMinutes_capAtOne_andDayOffIsZero() {
    WorkShift full = shift(480);
    WorkShift half = shift(240);
    WorkShift longShift = shift(720);
    UUID missingShiftId = UUID.randomUUID();
    Map<UUID, WorkShift> shifts = Map.of(
        full.getId(), full, half.getId(), half, longShift.getId(), longShift);

    when(resolver.loadContext(any())).thenReturn(context(shifts));
    when(resolver.plans(eq("e-1"), eq(MON), eq(MON.plusDays(4)), any()))
        .thenReturn(Map.of(
            MON, plan(full.getId(), false),
            MON.plusDays(1), plan(null, true),
            MON.plusDays(2), plan(half.getId(), false),
            MON.plusDays(3), plan(longShift.getId(), false),
            MON.plusDays(4), plan(missingShiftId, false)));

    Map<LocalDate, BigDecimal> w = source.weights("e-1", MON, MON.plusDays(4));

    assertWeight(new BigDecimal("1"), w.get(MON), "ca 480p = 1.0");
    assertWeight(BigDecimal.ZERO, w.get(MON.plusDays(1)), "dayOff = 0");
    assertWeight(new BigDecimal("0.5"), w.get(MON.plusDays(2)), "ca 240p = 0.5");
    assertWeight(new BigDecimal("1"), w.get(MON.plusDays(3)), "cap 1 (720p không vượt)");
    assertWeight(new BigDecimal("1"), w.get(MON.plusDays(4)), "ca mất row → tính đủ 1 ngày");
  }

  @Test
  void sum_scheduleWins_fallbackPeriodEngineForMissingDays() {
    // [T2..CN]: T2 có lịch ca nửa ngày (0.5); T3–T6 thiếu → generic 1; T7 OFF → 0; CN → 0.
    Map<LocalDate, BigDecimal> weights = Map.of(MON, new BigDecimal("0.5"));

    BigDecimal sum = source.sum(weights, MON, MON.plusDays(6), settings, FY_START);

    assertWeight(new BigDecimal("4.5"), sum, "0.5 (lịch) + 4 (fallback T3–T6)");
  }

  @Test
  void sum_saturdayDayOff_beatsGenericPolicy() {
    // Generic: Thứ 7 theo ALL → 1; nhưng lịch báo nghỉ (override / policy) → 0.
    settings.setSaturdayPolicy(SaturdayPolicy.ALL);
    LocalDate sat = LocalDate.of(2026, 1, 10);
    Map<LocalDate, BigDecimal> weights = Map.of(sat, BigDecimal.ZERO);

    BigDecimal withSchedule = source.sum(weights, sat, sat, settings, FY_START);
    BigDecimal withoutSchedule = source.sum(Map.of(), sat, sat, settings, FY_START);

    assertWeight(BigDecimal.ZERO, withSchedule, "lịch override → nghỉ");
    assertWeight(BigDecimal.ONE, withoutSchedule, "không có lịch → theo saturday_policy ALL");
  }

  @Test
  void sum_resultScalesToTwoDecimals() {
    Map<LocalDate, BigDecimal> weights = Map.of(MON, new BigDecimal("0.875"));

    BigDecimal sum = source.sum(weights, MON, MON, settings, FY_START);

    assertEquals(2, sum.scale(), "kết quả scale 2 như PeriodEngine");
    assertWeight(new BigDecimal("0.88"), sum, "0.875 → HALF_UP 2dp");
  }

  private static ScheduleResolver.Context context(Map<UUID, WorkShift> shifts) {
    return new ScheduleResolver.Context(
        new HashMap<>(), new HashMap<>(), new HashMap<>(), null, shifts);
  }

  private static DayPlan plan(UUID shiftId, boolean dayOff) {
    return new DayPlan(shiftId, dayOff, RosterSource.POLICY_AUTO, null);
  }

  private static EmployeeShiftRoster override(LocalDate date, UUID shiftId, boolean dayOff) {
    EmployeeShiftRoster row = new EmployeeShiftRoster();
    row.setEmployeeId("e-1");
    row.setWorkDate(date);
    row.setShiftId(shiftId);
    row.setDayOff(dayOff);
    row.setSource(RosterSource.MANUAL_OVERRIDE);
    return row;
  }

  private static void assertWeight(BigDecimal expected, BigDecimal actual, String message) {
    assertEquals(0, expected.compareTo(actual),
        message + " (expected=" + expected + " actual=" + actual + ")");
  }

  private static WorkShift shift(int workingMinutes) {
    WorkShift shift = new WorkShift();
    shift.setId(UUID.randomUUID());
    shift.setName("Ca " + workingMinutes);
    shift.setShiftType(ShiftType.FIXED);
    shift.setWorkingTime(workingMinutes);
    shift.setBreakingTime(0);
    shift.setActive(true);
    return shift;
  }
}
