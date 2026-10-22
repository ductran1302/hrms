package com.hrms.attendance.leave.dto;

import com.hrms.attendance.leave.LeaveEntitlement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LeaveEntitlementResponse(
    UUID id, String source, BigDecimal days, boolean isAuto, String reason, String grantedBy,
    Instant createdAt, UUID policyId, Instant calculatedAt) {

  public static LeaveEntitlementResponse from(LeaveEntitlement e) {
    return new LeaveEntitlementResponse(
        e.getId(), e.getSource().name(), e.getDays(), e.isAuto(), e.getReason(), e.getGrantedBy(),
        e.getCreatedAt(), e.getPolicyId(), e.getCalculatedAt());
  }
}
