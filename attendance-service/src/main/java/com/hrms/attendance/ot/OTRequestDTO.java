package com.hrms.attendance.ot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTO chi tiết đơn OT (dùng cho list, detail và form edit). */
public record OTRequestDTO(
    UUID id,
    String requestCode,
    String employeeId,
    String employeeCode,
    String fullName,
    UUID departmentId,
    String reason,
    BigDecimal totalHours,
    OTRequestStatus status,
    String approverId,
    String approverName,
    String approverEmployeeCode,
    LocalDateTime approvedAt,
    String rejectionReason,
    LocalDateTime cancelledAt,
    String cancellationReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long version,
    List<OTRequestItemDTO> items
) {
  static OTRequestDTO from(OTRequest r, List<OTRequestItemDTO> items,
                           String approverName, String approverEmployeeCode) {
    java.time.ZoneId zone = java.time.ZoneId.systemDefault();
    return new OTRequestDTO(
        r.getId(), r.getRequestCode(), r.getEmployeeId(), r.getEmployeeCode(),
        r.getFullName(), r.getDepartmentId(), r.getReason(), r.getTotalHours(),
        r.getStatus(), r.getApproverId(), approverName, approverEmployeeCode,
        r.getApprovedAt(), r.getRejectionReason(), r.getCancelledAt(), r.getCancellationReason(),
        r.getCreatedAt() != null ? LocalDateTime.ofInstant(r.getCreatedAt(), zone) : null,
        r.getUpdatedAt() != null ? LocalDateTime.ofInstant(r.getUpdatedAt(), zone) : null,
        r.getVersion(), items);
  }
}
