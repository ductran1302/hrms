package com.hrms.attendance.ot;

/** Trạng thái đơn OT. */
public enum OTRequestStatus {
  DRAFT,           // nhân viên lưu nháp
  PENDING,         // đã submit, chờ duyệt
  APPROVED,        // đã duyệt
  REJECTED,        // bị từ chối
  CANCELLED,       // nhân viên hủy (trước khi duyệt)
  PENDING_CANCEL,  // nhân viên xin hủy sau khi đã duyệt
  CANCELLED_APPROVED // leader phê duyệt hủy
}