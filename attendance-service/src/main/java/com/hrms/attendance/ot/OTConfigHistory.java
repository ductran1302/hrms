package com.hrms.attendance.ot;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lịch sử thay đổi cấu hình OT — audit trail. */
@Entity
@Table(name = "ot_config_histories",
    indexes = {@Index(columnList = "performed_at desc")})
@Getter
@Setter
@NoArgsConstructor
public class OTConfigHistory extends BaseEntity {

  @Column(name = "config_id", nullable = false)
  private java.util.UUID configId;

  /** CREATE / UPDATE / DELETE. */
  @Column(name = "action", nullable = false, length = 20)
  private String action;

  /** JSON diff chứa giá trị cũ + mới. */
  @Column(name = "changes_json", columnDefinition = "text")
  private String changesJson;

  /** Username người thực hiện (preferred_username từ JWT). */
  @Column(name = "performed_by", nullable = false, length = 100)
  private String performedBy;

  @Column(name = "performed_at", nullable = false)
  private LocalDateTime performedAt = LocalDateTime.now();

  // Snapshot cấu hình tại thời điểm thay đổi
  @Column(name = "day_start_time")
  private String dayStartTime;

  @Column(name = "night_start_time")
  private String nightStartTime;

  @Column(name = "post_shift_buffer_minutes")
  private Integer postShiftBufferMinutes;

  @Column(name = "pre_shift_buffer_minutes")
  private Integer preShiftBufferMinutes;

  @Column(name = "break_duration_minutes")
  private Integer breakDurationMinutes;

  @Column(name = "break_deduction_threshold_minutes")
  private Integer breakDeductionThresholdMinutes;

  @Column(name = "rate_weekday")
  private java.math.BigDecimal rateWeekday;

  @Column(name = "rate_weekday_night")
  private java.math.BigDecimal rateWeekdayNight;

  @Column(name = "rate_weekend")
  private java.math.BigDecimal rateWeekend;

  @Column(name = "rate_weekend_night")
  private java.math.BigDecimal rateWeekendNight;

  @Column(name = "rate_holiday")
  private java.math.BigDecimal rateHoliday;

  @Column(name = "rate_holiday_night")
  private java.math.BigDecimal rateHolidayNight;
}