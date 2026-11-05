package com.hrms.attendance.schedule;

import com.hrms.attendance.leave.LeaveSettings;
import com.hrms.attendance.leave.LeaveSettingsService;
import com.hrms.attendance.leave.SaturdayPolicy;
import com.hrms.attendance.shift.ShiftType;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed policy mặc định từ {@code leave_settings.saturday_policy} khi DB chưa có policy nào
 * (idempotent). Ca chuẩn được tạo nếu chưa có, để không phụ thuộc dữ liệu ca cũ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPolicySeeder implements ApplicationRunner {

  static final String FULL_SHIFT_NAME = "Ca hành chính";
  static final String HALF_SHIFT_NAME = "Ca nửa ngày sáng";

  private final WorkSchedulePolicyRepository policyRepository;
  private final WorkShiftRepository workShiftRepository;
  private final LeaveSettingsService leaveSettingsService;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (policyRepository.count() > 0) {
      log.debug("Đã có policy lịch — bỏ qua seed mặc định");
      return;
    }
    seed();
  }

  void seed() {
    LeaveSettings settings = leaveSettingsService.getOrCreateSettings();
    SaturdayPolicy saturdayPolicy =
        settings.getSaturdayPolicy() == null ? SaturdayPolicy.OFF : settings.getSaturdayPolicy();

    WorkShift full = findOrCreate(FULL_SHIFT_NAME,
        LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(12, 0), LocalTime.of(13, 0),
        480, 60);
    WorkShift half = saturdayPolicy == SaturdayPolicy.HALF
        ? findOrCreate(HALF_SHIFT_NAME,
            LocalTime.of(8, 0), LocalTime.of(12, 0), null, null, 240, 0)
        : null;

    WorkSchedulePolicy policy = new WorkSchedulePolicy();
    policy.setName(policyName(saturdayPolicy));
    policy.setDescription("Tự tạo từ leave_settings.saturday_policy=" + saturdayPolicy);
    policy.setMonShiftId(full.getId());
    policy.setTueShiftId(full.getId());
    policy.setWedShiftId(full.getId());
    policy.setThuShiftId(full.getId());
    policy.setFriShiftId(full.getId());
    policy.setSunShiftId(null);
    policy.setDefaultPolicy(true);
    policy.setActive(true);

    switch (saturdayPolicy) {
      case OFF -> policy.setSatShiftId(null);
      case ALL -> policy.setSatShiftId(full.getId());
      case HALF -> policy.setSatShiftId(half.getId());
      case ALTERNATING -> {
        policy.setSatShiftId(full.getId());
        policy.setSatAlternateShiftId(null); // tuần lẻ nghỉ
        policy.setRotationAnchorDate(
            firstSaturdayOnOrAfter(fiscalYearStart(settings, LocalDate.now())));
      }
    }
    policyRepository.saveAndFlush(policy);
    log.info("Seed policy lịch mặc định '{}' (saturday_policy={})", policy.getName(), saturdayPolicy);
  }

  private WorkShift findOrCreate(
      String name, LocalTime start, LocalTime end,
      LocalTime breakStart, LocalTime breakEnd, int workingTime, int breakingTime) {
    return workShiftRepository.findByName(name).orElseGet(() -> {
      WorkShift shift = new WorkShift();
      shift.setId(UUID.randomUUID());
      shift.setName(name);
      shift.setShiftType(ShiftType.FIXED);
      shift.setStartWorkTime(start);
      shift.setEndWorkTime(end);
      shift.setStartBreakTime(breakStart);
      shift.setEndBreakTime(breakEnd);
      shift.setWorkingTime(workingTime);
      shift.setBreakingTime(breakingTime);
      shift.setCrossesMidnight(end.isBefore(start));
      shift.setActive(true);
      log.info("Seed ca làm việc '{}' ({}–{})", name, start, end);
      return workShiftRepository.save(shift);
    });
  }

  private static LocalDate fiscalYearStart(LeaveSettings settings, LocalDate today) {
    LocalDate start = LocalDate.of(
        today.getYear(), settings.getFyStartMonth(), settings.getFyStartDay());
    return today.isBefore(start) ? start.minusYears(1) : start;
  }

  static LocalDate firstSaturdayOnOrAfter(LocalDate date) {
    int offset = (DayOfWeek.SATURDAY.getValue() - date.getDayOfWeek().getValue() + 7) % 7;
    return date.plusDays(offset);
  }

  private static String policyName(SaturdayPolicy policy) {
    return switch (policy) {
      case OFF -> "Chuẩn (nghỉ Thứ 7)";
      case ALL -> "Chuẩn (làm Thứ 7)";
      case HALF -> "Chuẩn (nửa ngày Thứ 7)";
      case ALTERNATING -> "Chuẩn (luân phiên Thứ 7)";
    };
  }
}
