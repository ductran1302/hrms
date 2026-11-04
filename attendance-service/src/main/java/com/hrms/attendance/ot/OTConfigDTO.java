package com.hrms.attendance.ot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** DTO cấu hình OT (trả về frontend). */
public record OTConfigDTO(
    UUID id,
    LocalTime dayStartTime,
    LocalTime nightStartTime,
    int postShiftBufferMinutes,
    int preShiftBufferMinutes,
    int breakDurationMinutes,
    int breakDeductionThresholdMinutes,
    BigDecimal rateWeekday,
    BigDecimal rateWeekdayNight,
    BigDecimal rateWeekend,
    BigDecimal rateWeekendNight,
    BigDecimal rateHoliday,
    BigDecimal rateHolidayNight,
    String status,
    Long version
) {
  static OTConfigDTO from(OTConfig c) {
    return new OTConfigDTO(
        c.getId(), c.getDayStartTime(), c.getNightStartTime(),
        c.getPostShiftBufferMinutes(), c.getPreShiftBufferMinutes(), c.getBreakDurationMinutes(),
        c.getBreakDeductionThresholdMinutes(),
        c.getRateWeekday(), c.getRateWeekdayNight(), c.getRateWeekend(),
        c.getRateWeekendNight(), c.getRateHoliday(), c.getRateHolidayNight(),
        c.getStatus(), c.getVersion());
  }
}