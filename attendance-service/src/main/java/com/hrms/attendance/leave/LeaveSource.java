package com.hrms.attendance.leave;

/** Nguồn cấp phép trong ledger quỹ phép. BASE/SENIORITY/POSITION tính tự động; còn lại HR nhập. */
public enum LeaveSource {
  BASE,
  SENIORITY,
  POSITION,
  BONUS,
  CARRYOVER,
  ADJUSTMENT
}
