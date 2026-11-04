package com.hrms.attendance.ot;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request cập nhật/cấu hình OT (ADMIN ghi). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OTConfigRequest {

  private LocalTime dayStartTime = LocalTime.of(6, 0);
  private LocalTime nightStartTime = LocalTime.of(22, 0);
  private Integer postShiftBufferMinutes = 60;
  private Integer preShiftBufferMinutes = 60;
  private Integer breakDurationMinutes = 0;

  @Min(0)
  private Integer breakDeductionThresholdMinutes = 240;

  @DecimalMin("0.0")
  private BigDecimal rateWeekday = new BigDecimal("1.50");
  @DecimalMin("0.0")
  private BigDecimal rateWeekdayNight = new BigDecimal("2.10");
  @DecimalMin("0.0")
  private BigDecimal rateWeekend = new BigDecimal("2.00");
  @DecimalMin("0.0")
  private BigDecimal rateWeekendNight = new BigDecimal("2.70");
  @DecimalMin("0.0")
  private BigDecimal rateHoliday = new BigDecimal("3.00");
  @DecimalMin("0.0")
  private BigDecimal rateHolidayNight = new BigDecimal("3.90");
}