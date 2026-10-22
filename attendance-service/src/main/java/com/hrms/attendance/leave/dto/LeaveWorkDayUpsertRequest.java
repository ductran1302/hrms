package com.hrms.attendance.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** HR sửa số ngày công 1 kỳ (set MANUAL). */
public record LeaveWorkDayUpsertRequest(
    LocalDate periodStart, BigDecimal workDays) {}
