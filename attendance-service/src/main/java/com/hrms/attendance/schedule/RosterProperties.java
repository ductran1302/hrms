package com.hrms.attendance.schedule;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cấu hình sinh roster (env HRMS_ROSTER_*). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hrms.roster")
public class RosterProperties {

  /** Bật job sinh roster định kỳ. */
  private boolean enabled = true;

  /** Cửa sổ quá khứ (ngày) khi job chạy — dùng để backfill, không ghi đè ngày < hôm nay. */
  private int pastDays = 45;

  /** Cửa sổ tương lai (ngày) sinh trước. */
  private int futureDays = 90;

  /** Cron job (mặc định 02:30 hằng ngày). */
  private String cron = "0 30 2 * * *";

  /** Batch size khi saveAll. */
  private int batchSize = 500;
}
