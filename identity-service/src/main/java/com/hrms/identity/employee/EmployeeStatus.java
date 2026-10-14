package com.hrms.identity.employee;

/** Trạng thái nhân viên. TERMINATED chỉ đặt qua API terminate (DELETE). */
public enum EmployeeStatus {
  ACTIVE,
  PROBATION,
  ON_LEAVE,
  TERMINATED
}
