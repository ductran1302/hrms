package com.hrms.attendance.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.attendance.shift.dto.WorkShiftRequest;
import com.hrms.attendance.shift.dto.WorkShiftResponse;
import com.hrms.attendance.shift.dto.WorkShiftUpdateRequest;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import java.time.LocalTime;
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
class WorkShiftServiceTest {

  @Mock
  private WorkShiftRepository workShiftRepository;

  @Mock
  private ShiftAssignmentRepository shiftAssignmentRepository;

  @InjectMocks
  private WorkShiftService service;

  private WorkShift existing;

  @BeforeEach
  void setUp() {
    existing = new WorkShift();
    existing.setId(UUID.randomUUID());
    existing.setName("Ca 8:00-17:00");
    existing.setShiftType(ShiftType.FIXED);
    existing.setStartWorkTime(LocalTime.of(8, 0));
    existing.setEndWorkTime(LocalTime.of(17, 0));
    existing.setStartBreakTime(LocalTime.of(12, 0));
    existing.setEndBreakTime(LocalTime.of(13, 0));
    existing.setWorkingTime(480);
    existing.setBreakingTime(60);
    existing.setActive(true);
  }

  private WorkShiftRequest fixedRequest() {
    return new WorkShiftRequest(
        "Ca 8:00-17:00", "FIXED",
        LocalTime.of(8, 0), LocalTime.of(17, 0),
        LocalTime.of(12, 0), LocalTime.of(13, 0),
        480, 60, null);
  }

  @Test
  void create_withValidData_savesShift() {
    when(workShiftRepository.existsByName("Ca 8:00-17:00")).thenReturn(false);

    WorkShiftResponse response = service.create(fixedRequest());

    assertEquals("Ca 8:00-17:00", response.name());
    assertEquals("FIXED", response.shiftType());
    assertEquals(480, response.workingTime());
    assertEquals(60, response.breakingTime());
    assertTrue(response.active());
    ArgumentCaptor<WorkShift> captor = ArgumentCaptor.forClass(WorkShift.class);
    verify(workShiftRepository).save(captor.capture());
    assertEquals(LocalTime.of(12, 0), captor.getValue().getStartBreakTime());
  }

  @Test
  void create_flexibleShiftLowercaseType_parses() {
    WorkShiftRequest request = new WorkShiftRequest(
        "Linh hoạt 7:00-18:00", "flexible",
        LocalTime.of(7, 0), LocalTime.of(18, 0),
        LocalTime.of(12, 0), LocalTime.of(13, 0),
        480, 60, null);
    when(workShiftRepository.existsByName(any())).thenReturn(false);

    WorkShiftResponse response = service.create(request);

    assertEquals("FLEXIBLE", response.shiftType());
    // Ca linh hoạt không dùng khung giờ nghỉ — bị tự xóa
    assertEquals(null, response.startBreakTime());
    assertEquals(null, response.endBreakTime());
    assertEquals(60, response.breakingTime());
  }

  @Test
  void create_flexibleWithoutBreakTimes_savesOk() {
    WorkShiftRequest request = new WorkShiftRequest(
        "Linh hoạt không nghỉ", "FLEXIBLE",
        LocalTime.of(7, 0), LocalTime.of(18, 0),
        null, null,
        600, 0, null);
    when(workShiftRepository.existsByName(any())).thenReturn(false);

    WorkShiftResponse response = service.create(request);

    assertEquals("FLEXIBLE", response.shiftType());
    assertEquals(600, response.workingTime());
  }

  @Test
  void create_duplicateName_throwsConflict() {
    when(workShiftRepository.existsByName("Ca 8:00-17:00")).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.create(fixedRequest()));
    verify(workShiftRepository, never()).save(any());
  }

  @Test
  void create_breakOutsideShiftWindow_throwsBusinessRule() {
    WorkShiftRequest request = new WorkShiftRequest(
        "Ca lỗi nghỉ", "FIXED",
        LocalTime.of(8, 0), LocalTime.of(17, 0),
        LocalTime.of(7, 0), LocalTime.of(8, 0),
        480, 60, null);
    when(workShiftRepository.existsByName(any())).thenReturn(false);

    assertThrows(BusinessRuleException.class, () -> service.create(request));
  }

  @Test
  void create_unpairedBreak_throwsBusinessRule() {
    WorkShiftRequest request = new WorkShiftRequest(
        "Ca thiếu endBreak", "FIXED",
        LocalTime.of(8, 0), LocalTime.of(17, 0),
        LocalTime.of(12, 0), null,
        480, 60, null);
    when(workShiftRepository.existsByName(any())).thenReturn(false);

    assertThrows(BusinessRuleException.class, () -> service.create(request));
  }

  @Test
  void create_overnightShift_throwsBusinessRule() {
    WorkShiftRequest request = new WorkShiftRequest(
        "Ca đêm", "FIXED",
        LocalTime.of(22, 0), LocalTime.of(6, 0),
        null, null,
        480, 0, null);
    when(workShiftRepository.existsByName(any())).thenReturn(false);

    assertThrows(BusinessRuleException.class, () -> service.create(request));
  }

  @Test
  void create_workingPlusBreakExceedsSpan_throwsBusinessRule() {
    WorkShiftRequest request = new WorkShiftRequest(
        "Ca vượt tổng", "FIXED",
        LocalTime.of(8, 0), LocalTime.of(17, 0),
        LocalTime.of(12, 0), LocalTime.of(13, 0),
        500, 60, null);
    when(workShiftRepository.existsByName(any())).thenReturn(false);

    assertThrows(BusinessRuleException.class, () -> service.create(request));
  }

  @Test
  void create_invalidShiftType_throwsBusinessRule() {
    WorkShiftRequest request = new WorkShiftRequest(
        "Ca lạ", "PART_TIME",
        LocalTime.of(8, 0), LocalTime.of(12, 0),
        null, null, 240, 0, null);
    when(workShiftRepository.existsByName(any())).thenReturn(false);

    assertThrows(BusinessRuleException.class, () -> service.create(request));
  }

  @Test
  void update_partialNameOnly_keepsOldTimes() {
    when(workShiftRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
    when(workShiftRepository.existsByNameAndIdNot("Ca mới", existing.getId())).thenReturn(false);

    service.update(existing.getId(), new WorkShiftUpdateRequest(
        "Ca mới", null, null, null, null, null, null, null, null, null));

    assertEquals("Ca mới", existing.getName());
    assertEquals(LocalTime.of(8, 0), existing.getStartWorkTime());
    assertEquals(480, existing.getWorkingTime());
    verify(workShiftRepository).save(existing);
  }

  @Test
  void update_switchToFlexible_clearsBreakWindow() {
    when(workShiftRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

    service.update(existing.getId(), new WorkShiftUpdateRequest(
        null, "FLEXIBLE", null, null, null, null, null, null, null, null));

    assertEquals(ShiftType.FLEXIBLE, existing.getShiftType());
    assertEquals(null, existing.getStartBreakTime());
    assertEquals(null, existing.getEndBreakTime());
    verify(workShiftRepository).save(existing);
  }

  @Test
  void update_clearBreak_clearsBothFields() {
    when(workShiftRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

    service.update(existing.getId(), new WorkShiftUpdateRequest(
        null, null, null, null, null, null, null, null, null, true));

    assertEquals(null, existing.getStartBreakTime());
    assertEquals(null, existing.getEndBreakTime());
    verify(workShiftRepository).save(existing);
  }

  @Test
  void update_changeTimesToInvalid_revalidatesAndThrows() {
    when(workShiftRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

    assertThrows(BusinessRuleException.class, () -> service.update(existing.getId(),
        new WorkShiftUpdateRequest(
            null, null, LocalTime.of(8, 0), LocalTime.of(11, 0),
            LocalTime.of(12, 0), LocalTime.of(13, 0), null, null, null, null)));
    verify(workShiftRepository, never()).save(any());
  }

  @Test
  void delete_referencedByAssignment_throwsBusinessRule() {
    when(workShiftRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
    when(shiftAssignmentRepository.existsByWorkShiftId(existing.getId())).thenReturn(true);

    assertThrows(BusinessRuleException.class, () -> service.delete(existing.getId()));
    verify(workShiftRepository, never()).delete(any());
  }

  @Test
  void delete_unreferenced_removesShift() {
    when(workShiftRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
    when(shiftAssignmentRepository.existsByWorkShiftId(existing.getId())).thenReturn(false);

    service.delete(existing.getId());

    verify(workShiftRepository).delete(existing);
  }

  @Test
  void list_withKeywordAndType_filters() {
    when(workShiftRepository.findAllOrdered())
        .thenReturn(java.util.List.of(existing));

    var result = service.list("8:00", "fixed", true);

    assertEquals(1, result.size());
    assertFalse(result.get(0).shiftType().equals("FLEXIBLE"));
  }

  @Test
  void list_withoutParams_returnsAll() {
    when(workShiftRepository.findAllOrdered())
        .thenReturn(java.util.List.of(existing));

    var result = service.list(null, null, null);

    assertEquals(1, result.size());
  }

  @Test
  void list_keywordNotMatching_returnsEmpty() {
    when(workShiftRepository.findAllOrdered())
        .thenReturn(java.util.List.of(existing));

    assertTrue(service.list("khong-cos", null, null).isEmpty());
  }
}
