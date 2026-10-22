package com.hrms.attendance.leave.dto;

import com.hrms.attendance.leave.LeaveWorkDay;
import com.hrms.attendance.leave.WorkDaySource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveWorkDayResponse(
    UUID id,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal periodWorkingDays,
    BigDecimal workDays,
    WorkDaySource source) {

  public static LeaveWorkDayResponse from(LeaveWorkDay w, BigDecimal periodWorkingDays) {
    return new LeaveWorkDayResponse(
        w.getId(), w.getPeriodStart(), w.getPeriodEnd(), periodWorkingDays,
        w.getWorkDays(), w.getSource());
  }
}
