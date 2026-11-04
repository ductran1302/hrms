package com.hrms.attendance.ot;

import java.util.UUID;

/** DTO lịch sử thay đổi cấu hình (Tab 2). */
public record OTConfigHistoryDTO(
    UUID id,
    UUID configId,
    String action,
    String changesJson,
    String performedBy,
    java.time.LocalDateTime performedAt,
    String dayStartTime,
    String nightStartTime,
    Integer postShiftBufferMinutes,
    Integer preShiftBufferMinutes,
    Integer breakDurationMinutes,
    Integer breakDeductionThresholdMinutes,
    java.math.BigDecimal rateWeekday,
    java.math.BigDecimal rateWeekdayNight,
    java.math.BigDecimal rateWeekend,
    java.math.BigDecimal rateWeekendNight,
    java.math.BigDecimal rateHoliday,
    java.math.BigDecimal rateHolidayNight
) {
  static OTConfigHistoryDTO from(OTConfigHistory h) {
    return new OTConfigHistoryDTO(
        h.getId(), h.getConfigId(), h.getAction(), h.getChangesJson(),
        h.getPerformedBy(), h.getPerformedAt(),
        h.getDayStartTime(), h.getNightStartTime(),
        h.getPostShiftBufferMinutes(), h.getPreShiftBufferMinutes(),
        h.getBreakDurationMinutes(),
        h.getBreakDeductionThresholdMinutes(),
        h.getRateWeekday(), h.getRateWeekdayNight(),
        h.getRateWeekend(), h.getRateWeekendNight(),
        h.getRateHoliday(), h.getRateHolidayNight());
  }
}