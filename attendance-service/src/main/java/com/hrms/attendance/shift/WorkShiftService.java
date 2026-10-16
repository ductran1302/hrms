package com.hrms.attendance.shift;

import com.hrms.attendance.shift.dto.WorkShiftRequest;
import com.hrms.attendance.shift.dto.WorkShiftResponse;
import com.hrms.attendance.shift.dto.WorkShiftUpdateRequest;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý ca làm việc. workingTime/breakingTime là PHÚT — validate tổng ≤ thời gian ca.
 * Validation dựa trên bản entity (POST ghi đủ, PATCH gộp null = giữ cũ rồi validate).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkShiftService {

  private final WorkShiftRepository workShiftRepository;
  private final ShiftAssignmentRepository shiftAssignmentRepository;

  @Transactional(readOnly = true)
  public List<WorkShiftResponse> list(String q, String type, Boolean active) {
    String keyword = q == null || q.isBlank() ? null : q.trim();
    ShiftType shiftType = type == null || type.isBlank() ? null : parseType(type);
    return workShiftRepository.search(shiftType, active, keyword).stream()
        .map(WorkShiftResponse::from)
        .toList();
  }

  @Transactional
  public WorkShiftResponse create(WorkShiftRequest request) {
    String name = request.name().trim();
    if (workShiftRepository.existsByName(name)) {
      throw new ConflictException("Tên ca đã tồn tại: " + name);
    }
    WorkShift shift = new WorkShift();
    shift.setId(UUID.randomUUID());
    shift.setName(name);
    shift.setShiftType(parseType(request.shiftType()));
    shift.setStartWorkTime(request.startWorkTime());
    shift.setEndWorkTime(request.endWorkTime());
    shift.setStartBreakTime(request.startBreakTime());
    shift.setEndBreakTime(request.endBreakTime());
    shift.setWorkingTime(request.workingTime());
    shift.setBreakingTime(request.breakingTime());
    shift.setActive(request.active() == null || request.active());
    normalize(shift);
    validate(shift);
    workShiftRepository.save(shift);
    log.info("Tạo ca làm việc '{}' ({}, {} phút làm, {} phút nghỉ)",
        shift.getName(), shift.getShiftType(), shift.getWorkingTime(), shift.getBreakingTime());
    return WorkShiftResponse.from(shift);
  }

  @Transactional
  public WorkShiftResponse update(UUID id, WorkShiftUpdateRequest request) {
    WorkShift shift = find(id);
    if (request.name() != null && !request.name().isBlank()) {
      String name = request.name().trim();
      if (!name.equals(shift.getName()) && workShiftRepository.existsByNameAndIdNot(name, id)) {
        throw new ConflictException("Tên ca đã tồn tại: " + name);
      }
      shift.setName(name);
    }
    if (request.shiftType() != null && !request.shiftType().isBlank()) {
      shift.setShiftType(parseType(request.shiftType()));
    }
    if (request.startWorkTime() != null) {
      shift.setStartWorkTime(request.startWorkTime());
    }
    if (request.endWorkTime() != null) {
      shift.setEndWorkTime(request.endWorkTime());
    }
    if (request.startBreakTime() != null) {
      shift.setStartBreakTime(request.startBreakTime());
    }
    if (request.endBreakTime() != null) {
      shift.setEndBreakTime(request.endBreakTime());
    }
    if (request.workingTime() != null) {
      shift.setWorkingTime(request.workingTime());
    }
    if (request.breakingTime() != null) {
      shift.setBreakingTime(request.breakingTime());
    }
    if (request.active() != null) {
      shift.setActive(request.active());
    }
    if (Boolean.TRUE.equals(request.clearBreak())) {
      shift.setStartBreakTime(null);
      shift.setEndBreakTime(null);
    }
    normalize(shift);
    validate(shift);
    workShiftRepository.save(shift);
    log.info("Cập nhật ca làm việc {}", shift.getName());
    return WorkShiftResponse.from(shift);
  }

  @Transactional
  public void delete(UUID id) {
    WorkShift shift = find(id);
    if (shiftAssignmentRepository.existsByWorkShiftId(id)) {
      throw new BusinessRuleException(
          "Ca '" + shift.getName() + "' còn nhân viên đang gán — đặt active=false để ngưng dùng thay vì xóa");
    }
    workShiftRepository.delete(shift);
    log.info("Xóa ca làm việc {}", shift.getName());
  }

  /**
   * Ca FLEXIBLE không dùng khung giờ nghỉ (công thức tính công chỉ cần breakingTime) —
   * tự xóa start/end break để dữ liệu nhất quán.
   */
  static void normalize(WorkShift shift) {
    if (shift.getShiftType() == ShiftType.FLEXIBLE) {
      shift.setStartBreakTime(null);
      shift.setEndBreakTime(null);
    }
  }

  /** Validate trên entity (sau gộp PATCH). */
  static void validate(WorkShift shift) {
    LocalTime start = shift.getStartWorkTime();
    LocalTime end = shift.getEndWorkTime();
    if (!start.isBefore(end)) {
      throw new BusinessRuleException("startWorkTime phải trước endWorkTime (GĐ1 không hỗ trợ ca qua nửa đêm)");
    }
    LocalTime startBreak = shift.getStartBreakTime();
    LocalTime endBreak = shift.getEndBreakTime();
    if ((startBreak == null) != (endBreak == null)) {
      throw new BusinessRuleException(
          "startBreakTime và endBreakTime phải đi kèm nhau (để trống cả hai nếu ca không có giờ nghỉ)");
    }
    if (startBreak != null) {
      if (!startBreak.isBefore(endBreak)) {
        throw new BusinessRuleException("startBreakTime phải trước endBreakTime");
      }
      if (startBreak.isBefore(start)) {
        throw new BusinessRuleException("startBreakTime không được trước startWorkTime");
      }
      if (endBreak.isAfter(end)) {
        throw new BusinessRuleException("endBreakTime không được sau endWorkTime");
      }
    }
    if (shift.getWorkingTime() <= 0) {
      throw new BusinessRuleException("workingTime (phút) phải > 0");
    }
    if (shift.getBreakingTime() < 0) {
      throw new BusinessRuleException("breakingTime (phút) phải ≥ 0");
    }
    long spanMinutes = Duration.between(start, end).toMinutes();
    long required = (long) shift.getWorkingTime() + shift.getBreakingTime();
    if (required > spanMinutes) {
      throw new BusinessRuleException(
          "workingTime + breakingTime (" + required + " phút) vượt thời gian ca (" + spanMinutes + " phút)");
    }
  }

  static ShiftType parseType(String raw) {
    try {
      return ShiftType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new BusinessRuleException("Loại ca không hợp lệ (chỉ FIXED hoặc FLEXIBLE): " + raw);
    }
  }

  private WorkShift find(UUID id) {
    return workShiftRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", id));
  }
}
