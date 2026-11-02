package com.hrms.attendance.leave.dto;

import com.hrms.attendance.leave.LeaveEntitlement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

public record LeaveEntitlementResponse(
    UUID id,
    String source,
    BigDecimal days,
    boolean isAuto,
    String reason,
    String grantedBy,
    Instant createdAt,
    UUID policyId,
    Instant calculatedAt,
    int year,
    LocalDate expiresAt,
    boolean expired) {

  public static LeaveEntitlementResponse from(LeaveEntitlement e) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    boolean expired = e.getExpiresAt() != null && e.getExpiresAt().isBefore(today);
    return new LeaveEntitlementResponse(
        e.getId(), e.getSource().name(), e.getDays(), e.isAuto(), e.getReason(), e.getGrantedBy(),
        e.getCreatedAt(), e.getPolicyId(), e.getCalculatedAt(),
        e.getYear(), e.getExpiresAt(), expired);
  }
}
