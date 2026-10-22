package com.hrms.attendance.leave;

/**
 * Trạng thái phiên bản chính sách quỹ phép (6 states — workflow approval).
 *
 * <p>DRAFT → PENDING_APPROVAL → APPROVED → ACTIVE → EXPIRED; CANCELLED (hủy trước khi áp dụng).
 * Chỉ DRAFT sửa/xóa được; version ACTIVE khi activate version sau → EXPIRED (effective_to bị cắt
 * về ngày trước version mới).
 */
public enum PolicyStatus {
  DRAFT,
  PENDING_APPROVAL,
  APPROVED,
  ACTIVE,
  EXPIRED,
  CANCELLED
}
