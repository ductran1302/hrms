package com.hrms.attendance.schedule;

import com.hrms.attendance.schedule.dto.GenerateRosterResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sinh roster rolling window hằng ngày (idempotent, không đè manual/past). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "hrms.roster", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RosterGenerationJob {

  private final RosterGenerationService generationService;
  private final RosterProperties properties;

  @Scheduled(cron = "${hrms.roster.cron:0 30 2 * * *}")
  public void generateRollingWindow() {
    LocalDate today = LocalDate.now();
    try {
      GenerateRosterResponse result = generationService.generate(
          today.minusDays(properties.getPastDays()),
          today.plusDays(properties.getFutureDays()),
          null,
          "system");
      log.info("Job roster xong: {}", result);
    } catch (Exception ex) {
      log.error("Job sinh roster lỗi", ex);
    }
  }
}
