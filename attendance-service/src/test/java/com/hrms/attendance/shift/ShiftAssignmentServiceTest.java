package com.hrms.attendance.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeAccessResolver;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.shift.dto.ShiftAssignmentRequest;
import com.hrms.attendance.shift.dto.ShiftAssignmentResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.EmployeeScope;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShiftAssignmentServiceTest {

  @Mock
  private ShiftAssignmentRepository assignmentRepository;

  @Mock
  private WorkShiftRepository workShiftRepository;

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private EmployeeAccessResolver accessResolver;

  @InjectMocks
  private ShiftAssignmentService service;

  private Employee employee;
  private WorkShift shift;

  @BeforeEach
  void setUp() {
    employee = new Employee();
    employee.setEmployeeId("e-1");
    employee.setEmployeeCode("NV001");
    employee.setFullName("Nguyễn Văn A");
    employee.setDepartmentName("Phòng Backend");
    employee.setStatus("ACTIVE");

    shift = new WorkShift();
    shift.setId(UUID.randomUUID());
    shift.setName("Ca 8:00-17:00");
    shift.setShiftType(ShiftType.FIXED);
    shift.setStartWorkTime(LocalTime.of(8, 0));
    shift.setEndWorkTime(LocalTime.of(17, 0));
    shift.setWorkingTime(480);
    shift.setBreakingTime(60);
    shift.setActive(true);
  }

  private ShiftAssignment assignment(LocalDate from) {
    ShiftAssignment assignment = new ShiftAssignment();
    assignment.setId(UUID.randomUUID());
    assignment.setEmployeeId("e-1");
    assignment.setWorkShiftId(shift.getId());
    assignment.setEffectiveFrom(from);
    assignment.setAssignedBy("hr");
    return assignment;
  }

  @Test
  void assign_firstAssignment_saves() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(workShiftRepository.findById(shift.getId())).thenReturn(Optional.of(shift));
    when(assignmentRepository.findTopByEmployeeIdOrderByEffectiveFromDesc("e-1"))
        .thenReturn(Optional.empty());

    ShiftAssignmentResponse response =
        service.assign(new ShiftAssignmentRequest("e-1", shift.getId(), LocalDate.of(2026, 11, 1)), "hr");

    assertEquals(LocalDate.of(2026, 11, 1), response.effectiveFrom());
    assertTrue(response.latest());
    ArgumentCaptor<ShiftAssignment> captor = ArgumentCaptor.forClass(ShiftAssignment.class);
    verify(assignmentRepository).save(captor.capture());
    assertEquals("hr", captor.getValue().getAssignedBy());
  }

  @Test
  void assign_dateNotAfterPrevious_throwsBusinessRule() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(workShiftRepository.findById(shift.getId())).thenReturn(Optional.of(shift));
    when(assignmentRepository.findTopByEmployeeIdOrderByEffectiveFromDesc("e-1"))
        .thenReturn(Optional.of(assignment(LocalDate.of(2026, 11, 1))));

    assertThrows(BusinessRuleException.class, () ->
        service.assign(new ShiftAssignmentRequest("e-1", shift.getId(), LocalDate.of(2026, 11, 1)), "hr"));
    verify(assignmentRepository, never()).save(any());
  }

  @Test
  void assign_dateBeforePrevious_throwsBusinessRule() {
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(workShiftRepository.findById(shift.getId())).thenReturn(Optional.of(shift));
    when(assignmentRepository.findTopByEmployeeIdOrderByEffectiveFromDesc("e-1"))
        .thenReturn(Optional.of(assignment(LocalDate.of(2026, 11, 10))));

    assertThrows(BusinessRuleException.class, () ->
        service.assign(new ShiftAssignmentRequest("e-1", shift.getId(), LocalDate.of(2026, 10, 1)), "hr"));
  }

  @Test
  void assign_inactiveShift_throwsBusinessRule() {
    shift.setActive(false);
    when(employeeRepository.findById("e-1")).thenReturn(Optional.of(employee));
    when(workShiftRepository.findById(shift.getId())).thenReturn(Optional.of(shift));

    assertThrows(BusinessRuleException.class, () ->
        service.assign(new ShiftAssignmentRequest("e-1", shift.getId(), LocalDate.of(2026, 11, 1)), "hr"));
  }

  @Test
  void assign_unknownEmployee_throwsNotFound() {
    when(employeeRepository.findById("nope")).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () ->
        service.assign(new ShiftAssignmentRequest("nope", shift.getId(), LocalDate.of(2026, 11, 1)), "hr"));
  }

  @Test
  void remove_notLatestAssignment_throwsBusinessRule() {
    ShiftAssignment old = assignment(LocalDate.of(2026, 11, 1));
    ShiftAssignment latest = assignment(LocalDate.of(2026, 12, 1));
    when(assignmentRepository.findById(old.getId())).thenReturn(Optional.of(old));
    when(assignmentRepository.findTopByEmployeeIdOrderByEffectiveFromDesc("e-1"))
        .thenReturn(Optional.of(latest));

    assertThrows(BusinessRuleException.class, () -> service.remove(old.getId()));
    verify(assignmentRepository, never()).delete(any());
  }

  @Test
  void remove_latestAssignment_deletes() {
    ShiftAssignment latest = assignment(LocalDate.of(2026, 12, 1));
    when(assignmentRepository.findById(latest.getId())).thenReturn(Optional.of(latest));
    when(assignmentRepository.findTopByEmployeeIdOrderByEffectiveFromDesc("e-1"))
        .thenReturn(Optional.of(latest));

    service.remove(latest.getId());

    verify(assignmentRepository).delete(latest);
  }

  @Test
  void getMy_byEmployeeCode_returnsEffectiveShift() {
    ShiftAssignment active = assignment(LocalDate.of(2026, 11, 1));
    when(employeeRepository.findByEmployeeCode("NV001")).thenReturn(Optional.of(employee));
    when(assignmentRepository
            .findTopByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                "e-1", LocalDate.of(2026, 12, 1)))
        .thenReturn(Optional.of(active));
    when(workShiftRepository.findById(shift.getId())).thenReturn(Optional.of(shift));
    when(assignmentRepository.findTopByEmployeeIdOrderByEffectiveFromDesc("e-1"))
        .thenReturn(Optional.of(active));

    ShiftAssignmentResponse response = service.getMy("NV001", LocalDate.of(2026, 12, 1));

    assertEquals("Ca 8:00-17:00", response.shiftName());
    assertEquals("NV001", response.employeeCode());
    assertTrue(response.latest());
  }

  @Test
  void getMy_noAssignmentYet_throwsNotFound() {
    when(employeeRepository.findByEmployeeCode("NV001")).thenReturn(Optional.of(employee));
    when(assignmentRepository
            .findTopByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                any(), any()))
        .thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () ->
        service.getMy("NV001", LocalDate.of(2026, 12, 1)));
  }

  @Test
  void list_withKeyword_filtersByEmployeeCode() {
    ShiftAssignment newer = assignment(LocalDate.of(2026, 12, 1));
    ShiftAssignment older = assignment(LocalDate.of(2026, 11, 1));
    when(assignmentRepository.findAllByOrderByEffectiveFromDesc())
        .thenReturn(List.of(newer, older));
    when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
    when(workShiftRepository.findAllById(any())).thenReturn(List.of(shift));

    List<ShiftAssignmentResponse> result = service.list(null, "NV001", "hr", EmployeeScope.ALL);

    assertEquals(2, result.size());
    assertTrue(result.get(0).latest());
    assertFalse(result.get(1).latest());
  }

  @Test
  void list_withKeywordNotMatching_returnsEmpty() {
    when(assignmentRepository.findAllByOrderByEffectiveFromDesc())
        .thenReturn(List.of(assignment(LocalDate.of(2026, 11, 1))));
    when(employeeRepository.findAllById(any())).thenReturn(List.of(employee));
    when(workShiftRepository.findAllById(any())).thenReturn(List.of(shift));

    assertTrue(service.list(null, "KHONGTONTAI", "hr", EmployeeScope.ALL).isEmpty());
  }
}
