package com.hrms.identity.account;

/** Kết quả audit role của một tài khoản Keycloak. */
public enum RoleAuditAction {
  /** Dry-run: role sẽ được sửa nếu chạy apply. */
  WOULD_CORRECT,
  /** Đã sửa role. */
  CORRECTED,
  /** Có ADMIN — không tự động sửa. */
  SKIP_ADMIN,
  /** Có nhiều role HRMS — cần admin review thủ công. */
  REVIEW,
  /** Username không khớp hồ sơ nhân viên. */
  NO_EMPLOYEE,
  /** Gọi Keycloak cập nhật role thất bại. */
  FAILED
}
