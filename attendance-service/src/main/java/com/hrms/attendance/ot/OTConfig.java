package com.hrms.attendance.ot;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cấu hình tính OT toàn công ty (chỉ duy nhất 1 dòng ACTIVE).
 * Mọi thay đổi sẽ được ghi lại vào {@link com.hrms.attendance.ot.OTConfigHistory}.
 */
@Entity
@Table(name = "ot_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OTConfig extends BaseEntity {

  /** Điểm giao đêm-ngày: 00:00 → day_start = 06:00 (trước 06:00 là OT đêm). */
  @Column(name = "day_start_time", nullable = false)
  private LocalTime dayStartTime = LocalTime.of(6, 0);

  /** Điểm giao ngày-đêm: 22:00 → trước 22:00 là OT ngày, từ 22:00 là OT đêm. */
  @Column(name = "night_start_time", nullable = false)
  private LocalTime nightStartTime = LocalTime.of(22, 0);

  /** Số phút nhân viên phải nghỉ sau ca trước khi bắt đầu OT. */
  @Column(name = "post_shift_buffer_minutes", nullable = false)
  private Integer postShiftBufferMinutes = 60;

  /** Số phút nhân viên phải kết thúc OT trước khi bắt đầu ca tiếp theo. */
  @Column(name = "pre_shift_buffer_minutes", nullable = false)
  private Integer preShiftBufferMinutes = 60;

  /** Thời gian nghỉ giải lao trong ca OT (phút). */
  @Column(name = "break_duration_minutes", nullable = false)
  private Integer breakDurationMinutes = 0;

  /**
   * Ngưỡng (phút) chỉ trừ break khi thời lượng OT vượt ngưỡng.
   * Mặc định 240 (4 giờ) — OT ngắn hơn/ bằng ngưỡng giữ nguyên giờ.
   */
  @Column(name = "break_deduction_threshold_minutes", nullable = false)
  private Integer breakDeductionThresholdMinutes = 240;

  // --- Hệ số tính tiền OT ---
  @Column(name = "rate_weekday", nullable = false, precision = 5, scale = 2)
  private BigDecimal rateWeekday = new BigDecimal("1.50");

  @Column(name = "rate_weekday_night", nullable = false, precision = 5, scale = 2)
  private BigDecimal rateWeekdayNight = new BigDecimal("2.10");

  @Column(name = "rate_weekend", nullable = false, precision = 5, scale = 2)
  private BigDecimal rateWeekend = new BigDecimal("2.00");

  @Column(name = "rate_weekend_night", nullable = false, precision = 5, scale = 2)
  private BigDecimal rateWeekendNight = new BigDecimal("2.70");

  @Column(name = "rate_holiday", nullable = false, precision = 5, scale = 2)
  private BigDecimal rateHoliday = new BigDecimal("3.00");

  @Column(name = "rate_holiday_night", nullable = false, precision = 5, scale = 2)
  private BigDecimal rateHolidayNight = new BigDecimal("3.90");

  /** ACTIVE / INACTIVE (unique constraint ở DB). */
  @Column(name = "status", nullable = false, length = 20)
  private String status = "ACTIVE";
}