package com.hrms.attendance.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hrms.attendance.schedule.EmployeeShiftRoster;
import com.hrms.attendance.schedule.EmployeeShiftRosterRepository;
import com.hrms.attendance.schedule.RosterSource;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import com.hrms.attendance.shift.ShiftType;
import java.math.BigDecimal;
import java.time.LocalDate;
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

  @Mock private EmployeeShiftRosterRepository rosterRepository;
  @Mock private WorkShiftRepository workShiftRepository;

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
  }

  @Test
  void weights_noRows_returnsEmptyMap_callerFallsBack() {
    when(rosterRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            eq("e-1"), any(), any()))
        .thenReturn(List.of());

    Map<LocalDate, BigDecimal> weights = source.weights("e-1", MON, MON.plusDays(6));

    assertTrue(weights.isEmpty(), "không có row → caller fallback PeriodEngine");
  }

  @Test
  void weights_byShiftMinutes_capAtOne_andDayOffIsZero() {
    WorkShift full = shift(480);
    WorkShift half = shift(240);
    WorkShift longShift = shift(720);
    when(rosterRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            eq("e-1"), any(), any()))
        .thenReturn(List.of(
            row(MON, full.getId(), false),
            row(MON.plusDays(1), null, true),                 // ngày nghỉ (override)
            row(MON.plusDays(2), half.getId(), false),        // ca nửa ngày
            row(MON.plusDays(3), longShift.getId(), false),   // ca 12h → cap 1
            row(MON.plusDays(4), UUID.randomUUID(), false))); // ca đã bị xóa
    when(workShiftRepository.findAllById(any())).thenReturn(List.of(full, half, longShift));

    Map<LocalDate, BigDecimal> w = source.weights("e-1", MON, MON.plusDays(4));

    assertWeight(new BigDecimal("1"), w.get(MON), "ca 480p = 1.0");
    assertWeight(BigDecimal.ZERO, w.get(MON.plusDays(1)), "dayOff = 0");
    assertWeight(new BigDecimal("0.5"), w.get(MON.plusDays(2)), "ca 240p = 0.5");
    assertWeight(new BigDecimal("1"), w.get(MON.plusDays(3)), "cap 1 (720p không vượt)");
    assertWeight(new BigDecimal("1"), w.get(MON.plusDays(4)), "ca mất row → tính đủ 1 ngày");
  }

  @Test
  void sum_rosterWins_fallbackPeriodEngineForMissingDays() {
    // [T2..CN]: T2 có row ca nửa ngày (0.5); T3–T6 thiếu row → generic 1; T7 OFF → 0; CN → 0.
    Map<LocalDate, BigDecimal> weights = Map.of(MON, new BigDecimal("0.5"));

    BigDecimal sum = source.sum(weights, MON, MON.plusDays(6), settings, FY_START);

    assertWeight(new BigDecimal("4.5"), sum, "0.5 (roster) + 4 (fallback T3–T6)");
  }

  @Test
  void sum_saturdayOverrideDayOff_beatsGenericPolicy() {
    // Generic: Thứ 7 theo OFF → 0; nhưng đây đang là OFF nên đảo lại: settings ALL + row dayOff.
    settings.setSaturdayPolicy(SaturdayPolicy.ALL);
    LocalDate sat = LocalDate.of(2026, 1, 10);
    Map<LocalDate, BigDecimal> weights = Map.of(sat, BigDecimal.ZERO); // row: nghỉ (override)

    BigDecimal withRoster = source.sum(weights, sat, sat, settings, FY_START);
    BigDecimal withoutRoster = source.sum(Map.of(), sat, sat, settings, FY_START);

    assertWeight(BigDecimal.ZERO, withRoster, "roster override → nghỉ");
    assertWeight(BigDecimal.ONE, withoutRoster, "không row → theo saturday_policy ALL");
  }

  @Test
  void sum_resultScalesToTwoDecimals() {
    Map<LocalDate, BigDecimal> weights = Map.of(MON, new BigDecimal("0.875"));

    BigDecimal sum = source.sum(weights, MON, MON, settings, FY_START);

    assertEquals(2, sum.scale(), "kết quả scale 2 như PeriodEngine");
    assertWeight(new BigDecimal("0.88"), sum, "0.875 → HALF_UP 2dp");
  }

  private static void assertWeight(BigDecimal expected, BigDecimal actual, String message) {
    assertEquals(0, expected.compareTo(actual),
        message + " (expected=" + expected + " actual=" + actual + ")");
  }

  private static EmployeeShiftRoster row(LocalDate date, UUID shiftId, boolean dayOff) {
    EmployeeShiftRoster row = new EmployeeShiftRoster();
    row.setEmployeeId("e-1");
    row.setWorkDate(date);
    row.setShiftId(shiftId);
    row.setDayOff(dayOff);
    row.setSource(dayOff ? RosterSource.MANUAL_OVERRIDE : RosterSource.POLICY_AUTO);
    return row;
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
