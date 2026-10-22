package com.hrms.attendance.leave;

/** Chính sách thứ 7 trong tuần làm việc (T2-T6 luôn làm, CN nghỉ). */
public enum SaturdayPolicy {
  /** Nghỉ thứ 7. */
  OFF,
  /** Làm nửa ngày thứ 7 (ca sáng 4h = 0.5 ngày). */
  HALF,
  /** Luân phiên thứ 7 — đánh số từ đầu năm TC, Sat #0 = đi làm. */
  ALTERNATING,
  /** Làm đủ ngày thứ 7. */
  ALL
}
