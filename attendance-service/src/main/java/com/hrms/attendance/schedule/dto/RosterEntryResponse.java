package com.hrms.attendance.schedule.dto;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.schedule.EmployeeShiftRoster;
import com.hrms.attendance.schedule.RosterSource;
import com.hrms.attendance.shift.WorkShift;
import java.time.LocalDate;
import java.util.UUID;

/** Một ngày lịch của nhân viên — workDate = ngày bắt đầu ca (ca đêm không đổi ngày). */
public record RosterEntryResponse(
    UUID id,
    String employeeId,
    String employeeCode,
    String fullName,
    LocalDate workDate,
    UUID shiftId,
    String shiftName,
    String shiftType,
    boolean crossesMidnight,
    boolean dayOff,
    RosterSource source,
    UUID policyId,
    String note) {

  public static RosterEntryResponse from(
      EmployeeShiftRoster roster, Employee employee, WorkShift shift) {
    return new RosterEntryResponse(
        roster.getId(),
        roster.getEmployeeId(),
        employee == null ? null : employee.getEmployeeCode(),
        employee == null ? null : employee.getFullName(),
        roster.getWorkDate(),
        roster.getShiftId(),
        shift == null ? null : shift.getName(),
        shift == null ? null : shift.getShiftType().name(),
        shift != null && shift.isCrossesMidnight(),
        roster.isDayOff(),
        roster.getSource(),
        roster.getPolicyId(),
        roster.getNote());
  }
}
