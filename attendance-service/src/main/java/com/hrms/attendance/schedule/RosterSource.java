package com.hrms.attendance.schedule;

/** Nguồn sinh dòng roster. */
public enum RosterSource {
  /** Sinh từ policy thường (T2–T6/CN). */
  POLICY_AUTO,
  /** Sinh từ phần Thứ 7 luân phiên (parity theo anchor). */
  ROTATION_GEN,
  /** HR sửa tay — regeneration không bao giờ ghi đè. */
  MANUAL_OVERRIDE,
  /** Fallback từ bảng gán ca cũ {@code shift_assignments} (chưa có policy assignment). */
  LEGACY_ASSIGNMENT
}
