package com.hrms.attendance.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.schedule.dto.GenerateRosterResponse;
import com.hrms.attendance.schedule.dto.RosterEntryResponse;
import com.hrms.attendance.shift.ShiftAssignment;
import com.hrms.attendance.shift.ShiftAssignmentRepository;
import com.hrms.attendance.shift.ShiftType;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RosterGenerationServiceTest {

  @Mock private EmployeeRepository employeeRepository;
  @Mock private EmployeeScheduleAssignmentRepository assignmentRepository;
  @Mock private EmployeeShiftRosterRepository rosterRepository;
  @Mock private ShiftAssignmentRepository legacyAssignmentRepository;
  @Mock private WorkSchedulePolicyRepository policyRepository;
  @Mock private WorkShiftRepository workShiftRepository;
  @Mock private RosterProperties properties;

  @InjectMocks private RosterGenerationService service;

  private Employee employee;
  private WorkShift fullShift;
  private WorkShift otherShift;
  private WorkSchedulePolicy defaultPolicy;

  @BeforeEach
  void setUp() {
    employee = new Employee();
    employee.setEmployeeId("e-1");
    employee.setEmployeeCode("NV001");
    employee.setFullName("Nguyễn Văn A");
    employee.setStatus("ACTIVE");

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

    lenient().when(properties.getBatchSize()).thenReturn(500);
    lenient().when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    lenient().when(assignmentRepository.findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(any()))
        .thenReturn(List.of());
    lenient().when(legacyAssignmentRepository.findAllByOrderByEffectiveFromDesc())
        .thenReturn(List.of());
    lenient().when(policyRepository.findAll()).thenReturn(List.of(defaultPolicy));
    lenient().when(policyRepository.findFirstByDefaultPolicyTrueAndActiveTrue())
        .thenReturn(Optional.of(defaultPolicy));
    lenient().when(workShiftRepository.findAll()).thenReturn(List.of(fullShift, otherShift));
    lenient().when(rosterRepository.findByEmployeeIdInAndWorkDateBetween(any(), any(), any()))
        .thenReturn(List.of());
  }

  @Test
  void generate_defaultPolicy_createsWeekMonToSun() {
    LocalDate monday = LocalDate.now()
        .with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(4);
    LocalDate sunday = monday.plusDays(6);
    List<EmployeeShiftRoster> saved = captureSaved();

    GenerateRosterResponse result = service.generate(monday, sunday, "e-1", "test");

    assertEquals(7, result.created());
    assertEquals(7, saved.size());
    for (EmployeeShiftRoster row : saved) {
      if (row.getWorkDate().getDayOfWeek() == DayOfWeek.SATURDAY
          || row.getWorkDate().getDayOfWeek() == DayOfWeek.SUNDAY) {
        assertTrue(row.isDayOff());
        assertEquals(RosterSource.POLICY_AUTO, row.getSource());
      } else {
        assertFalse(row.isDayOff());
        assertEquals(fullShift.getId(), row.getShiftId());
      }
      assertEquals(defaultPolicy.getId(), row.getPolicyId());
    }
  }

  @Test
  void generate_existingPastRow_isFrozen() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    EmployeeShiftRoster existing = new EmployeeShiftRoster();
    existing.setEmployeeId("e-1");
    existing.setWorkDate(yesterday);
    existing.setShiftId(otherShift.getId());
    existing.setDayOff(false);
    existing.setSource(RosterSource.POLICY_AUTO);
    when(rosterRepository.findByEmployeeIdInAndWorkDateBetween(any(), any(), any()))
        .thenReturn(List.of(existing));

    GenerateRosterResponse result = service.generate(yesterday, yesterday, "e-1", "test");

    assertEquals(1, result.skipped());
    assertEquals(0, result.created());
    assertEquals(0, result.updated());
    verify(rosterRepository, never()).saveAll(anyList());
  }

  @Test
  void generate_manualOverrideFuture_isNeverOverwritten() {
    LocalDate tomorrow = LocalDate.now().plusDays(1);
    EmployeeShiftRoster existing = new EmployeeShiftRoster();
    existing.setEmployeeId("e-1");
    existing.setWorkDate(tomorrow);
    existing.setShiftId(null);
    existing.setDayOff(true);
    existing.setSource(RosterSource.MANUAL_OVERRIDE);
    when(rosterRepository.findByEmployeeIdInAndWorkDateBetween(any(), any(), any()))
        .thenReturn(List.of(existing));

    GenerateRosterResponse result = service.generate(tomorrow, tomorrow, "e-1", "test");

    assertEquals(1, result.skipped());
    verify(rosterRepository, never()).saveAll(anyList());
    assertTrue(existing.isDayOff());
    assertEquals(RosterSource.MANUAL_OVERRIDE, existing.getSource());
  }

  @Test
  void generate_existingFutureDayOffWithStalePolicy_updatesPolicyId() {
    LocalDate saturday = LocalDate.now()
        .with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).plusWeeks(4);
    EmployeeShiftRoster existing = new EmployeeShiftRoster();
    existing.setEmployeeId("e-1");
    existing.setWorkDate(saturday);
    existing.setShiftId(null);
    existing.setDayOff(true);
    existing.setSource(RosterSource.POLICY_AUTO);
    existing.setPolicyId(UUID.randomUUID());
    when(rosterRepository.findByEmployeeIdInAndWorkDateBetween(any(), any(), any()))
        .thenReturn(List.of(existing));
    List<EmployeeShiftRoster> saved = captureSaved();

    GenerateRosterResponse result = service.generate(saturday, saturday, "e-1", "test");

    assertEquals(1, result.updated());
    assertEquals(defaultPolicy.getId(), existing.getPolicyId());
    assertEquals(1, saved.size());
  }

  @Test
  void generate_policyAssignment_overridesDefault() {
    LocalDate monday = LocalDate.now()
        .with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(4);
    WorkSchedulePolicy custom = new WorkSchedulePolicy();
    custom.setId(UUID.randomUUID());
    custom.setName("Chỉ thứ 2");
    custom.setMonShiftId(otherShift.getId());
    custom.setDefaultPolicy(false);
    custom.setActive(true);
    when(policyRepository.findAll()).thenReturn(List.of(defaultPolicy, custom));

    EmployeeScheduleAssignment assignment = new EmployeeScheduleAssignment();
    assignment.setId(UUID.randomUUID());
    assignment.setEmployeeId("e-1");
    assignment.setPolicyId(custom.getId());
    assignment.setEffectiveFrom(monday.minusDays(30));
    when(assignmentRepository.findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(any()))
        .thenReturn(List.of(assignment));
    List<EmployeeShiftRoster> saved = captureSaved();

    service.generate(monday, monday, "e-1", "test");

    assertEquals(1, saved.size());
    assertEquals(otherShift.getId(), saved.get(0).getShiftId());
    assertEquals(custom.getId(), saved.get(0).getPolicyId());
  }

  @Test
  void generate_legacyShiftAssignment_weekdayUsesLegacyAndSaturdayOff() {
    LocalDate monday = LocalDate.now()
        .with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(4);
    LocalDate saturday = monday.plusDays(5);
    ShiftAssignment legacy = new ShiftAssignment();
    legacy.setId(UUID.randomUUID());
    legacy.setEmployeeId("e-1");
    legacy.setWorkShiftId(otherShift.getId());
    legacy.setEffectiveFrom(monday.minusDays(60));
    when(legacyAssignmentRepository.findAllByOrderByEffectiveFromDesc())
        .thenReturn(List.of(legacy));
    List<EmployeeShiftRoster> saved = captureSaved();

    service.generate(monday, saturday, "e-1", "test");

    assertEquals(6, saved.size());
    EmployeeShiftRoster mondayRow = saved.get(0);
    assertEquals(monday, mondayRow.getWorkDate());
    assertEquals(otherShift.getId(), mondayRow.getShiftId());
    assertEquals(RosterSource.LEGACY_ASSIGNMENT, mondayRow.getSource());
    EmployeeShiftRoster saturdayRow = saved.get(5);
    assertEquals(saturday, saturdayRow.getWorkDate());
    assertTrue(saturdayRow.isDayOff());
    assertEquals(RosterSource.LEGACY_ASSIGNMENT, saturdayRow.getSource());
  }

  @Test
  void resolve_missingRow_createsFromDefaultPolicy() {
    LocalDate date = LocalDate.now()
        .with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(4);
    when(rosterRepository.findByEmployeeIdAndWorkDate("e-1", date))
        .thenReturn(Optional.empty());
    when(rosterRepository.saveAndFlush(any(EmployeeShiftRoster.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RosterEntryResponse response = service.resolve("e-1", date);

    assertEquals("e-1", response.employeeId());
    assertEquals(fullShift.getId(), response.shiftId());
    assertFalse(response.dayOff());
    verify(rosterRepository).saveAndFlush(any(EmployeeShiftRoster.class));
  }

  private WorkShift shift(String name) {
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

  private List<EmployeeShiftRoster> captureSaved() {
    List<EmployeeShiftRoster> saved = new ArrayList<>();
    when(rosterRepository.saveAll(anyList())).thenAnswer(inv -> {
      List<EmployeeShiftRoster> batch = inv.getArgument(0);
      saved.addAll(batch);
      return batch;
    });
    return saved;
  }
}
