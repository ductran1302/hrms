package com.hrms.attendance.ot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** DTO chi tiết 1 ngày OT trong đơn. */
public record OTRequestItemDTO(
    UUID id,
    UUID requestId,
    LocalDate otDate,
    LocalDateTime startTime,
    LocalDateTime endTime,
    DayType dayType,
    BigDecimal dayHours,
    BigDecimal nightHours,
    LocalDateTime actualStartTime,
    LocalDateTime actualEndTime,
    BigDecimal approvedHours,
    Long version
) {
  static OTRequestItemDTO from(OTRequestItem item) {
    return new OTRequestItemDTO(
        item.getId(),
        item.getRequest() != null ? item.getRequest().getId() : null,
        item.getOtDate(), item.getStartTime(), item.getEndTime(),
        item.getDayType(), item.getDayHours(), item.getNightHours(),
        item.getActualStartTime(), item.getActualEndTime(),
        item.getApprovedHours(), item.getVersion());
  }
}