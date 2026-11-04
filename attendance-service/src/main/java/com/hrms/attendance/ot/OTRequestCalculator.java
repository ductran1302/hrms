package com.hrms.attendance.ot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Core calculator cho phép tính toán giờ OT và hệ số.
 *
 * <p>Các phương thức chính:
 * <ul>
 *   <li>{@code splitByDayNight}: chia interval thành giờ ban ngày/ban đêm (qua day_start/night_start).</li>
 *   <li>{@code computeItemHours}: tính giờ OT thực, nhóm theo ngày, trừ break.</li>
 *   <li>{@code calcAmount}: hệ số × giờ (dùng cho payroll).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OTRequestCalculator {

  private final OTConfigRepository otConfigRepository;

  /**
   * Chia một khoảng thời gian thành 2 nhóm: giờ ban ngày / ban đêm.
   * <p>
   * Logic:
   * - Ngày mới bắt đầu tại {@code dayStartTime} (ví dụ 06:00). Các giờ trước 06:00 là ban đêm.
   * - Ban đêm bắt đầu tại {@code nightStartTime} (ví dụ 22:00). Các giờ từ 22:00–24:00
   *   và 00:00–06:00 của ngày hôm sau là ban đêm.
   *
   * @return [0]=dayHours, [1]=nightHours (tính bằng giờ thập phân).
   */
  public BigDecimal[] splitByDayNight(LocalDateTime start, LocalDateTime end,
                                      LocalTime dayStart, LocalTime nightStart) {
    if (start == null || end == null || !start.isBefore(end)) {
      return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
    }

    long dayMinutes = 0;
    long nightMinutes = 0;

    LocalDateTime current = start;
    while (current.isBefore(end)) {
      LocalDate date = current.toLocalDate();
      LocalTime time = current.toLocalTime();

      // Các mốc thời gian trong ngày hôm nay
      LocalDateTime dayStartToday = date.atTime(dayStart);
      LocalDateTime nightStartToday = date.atTime(nightStart);
      LocalDateTime dayStartNext = date.plusDays(1).atTime(dayStart);

      // Xác định segment tiếp theo và loại (day/night)
      LocalDateTime boundary;
      boolean isNightSegment;

      if (time.isBefore(dayStart)) {
        // Ban đêm sáng: [00:00 -> dayStartToday]
        boundary = dayStartToday;
        isNightSegment = true;
      } else if (time.isBefore(nightStart)) {
        // Ban ngày: [dayStart -> nightStart]
        boundary = nightStartToday;
        isNightSegment = false;
      } else {
        // Ban đêm tối: [nightStart -> dayStartNext] — kéo dài qua đêm
        boundary = dayStartNext;
        isNightSegment = true;
      }

      // Không vượt quá endTime
      LocalDateTime segmentEnd = end.isBefore(boundary) ? end : boundary;
      long minsSegment = ChronoUnit.MINUTES.between(current, segmentEnd);

      if (isNightSegment) {
        nightMinutes += minsSegment;
      } else {
        dayMinutes += minsSegment;
      }

      current = segmentEnd;
    }

    BigDecimal dayHours = BigDecimal.valueOf(dayMinutes)
        .divide(BigDecimal.valueOf(60), 2, BigDecimal.ROUND_HALF_UP);
    BigDecimal nightHours = BigDecimal.valueOf(nightMinutes)
        .divide(BigDecimal.valueOf(60), 2, BigDecimal.ROUND_HALF_UP);

    return new BigDecimal[]{dayHours, nightHours};
  }

  /**
   * Tính giờ OT cho một item dựa trên config, trừ thời gian nghỉ giải lao.
   *
   * @param startTime      thời gian bắt đầu OT.
   * @param endTime        thời gian kết thúc OT.
   * @param config         cấu hình OT.
   * @return [0]=dayHours, [1]=nightHours (đã trừ break).
   */
  public BigDecimal[] computeItemHours(LocalDateTime startTime, LocalDateTime endTime,
                                       OTConfig config) {
    BigDecimal[] raw = splitByDayNight(startTime, endTime,
        config.getDayStartTime(), config.getNightStartTime());

    BigDecimal dayHours = raw[0];
    BigDecimal nightHours = raw[1];

    int breakMinutes = config.getBreakDurationMinutes();
    long spanMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
    int thresholdMinutes = config.getBreakDeductionThresholdMinutes() != null
        ? config.getBreakDeductionThresholdMinutes() : 240;
    // Chỉ trừ break khi thời lượng OT VƯỢT ngưỡng (mặc định 4h) — OT ngắn giữ nguyên giờ
    if (breakMinutes > 0 && spanMinutes > thresholdMinutes) {
      BigDecimal breakHours = BigDecimal.valueOf(breakMinutes)
          .divide(BigDecimal.valueOf(60), 2, BigDecimal.ROUND_HALF_UP);
      // Trừ break vào ban đêm trước (ưu tiên giảm giờ đắt hơn)
      BigDecimal breakFromNight = breakHours.min(nightHours);
      nightHours = nightHours.subtract(breakFromNight);
      BigDecimal remainingBreak = breakHours.subtract(breakFromNight);
      dayHours = dayHours.subtract(remainingBreak);
      if (dayHours.compareTo(BigDecimal.ZERO) < 0) {
        dayHours = BigDecimal.ZERO;
      }
    }

    return new BigDecimal[]{dayHours.max(BigDecimal.ZERO), nightHours.max(BigDecimal.ZERO)};
  }

  /**
   * Tính tổng số tiền OT cho 1 item dựa trên hệ số.
   *
   * @param dayHours    giờ OT ban ngày.
   * @param nightHours  giờ OT ban đêm.
   * @param dayType     loại ngày.
   * @param config      cấu hình OT.
   * @return số tiền (giờ × hệ số).
   */
  public BigDecimal calcAmount(BigDecimal dayHours, BigDecimal nightHours,
                               DayType dayType, OTConfig config) {
    BigDecimal dayRate = rateForDay(dayType, config, true);
    BigDecimal nightRate = rateForDay(dayType, config, false);

    BigDecimal dayAmount = dayHours.multiply(dayRate);
    BigDecimal nightAmount = nightHours.multiply(nightRate);
    return dayAmount.add(nightAmount);
  }

  /** Chọn hệ số dựa trên dayType và có phải ban đêm không. */
  private BigDecimal rateForDay(DayType dayType, OTConfig config, boolean isDay) {
    return switch (dayType) {
      case WEEKDAY -> isDay ? config.getRateWeekday() : config.getRateWeekdayNight();
      case WEEKEND -> isDay ? config.getRateWeekend() : config.getRateWeekendNight();
      case HOLIDAY -> isDay ? config.getRateHoliday() : config.getRateHolidayNight();
    };
  }

  /** Tổng giờ OT thực (day + night) của một danh sách item. */
  public BigDecimal sumRealHours(List<OTRequestItemCreateRequest> items, OTConfig config) {
    if (items == null || items.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (OTRequestItemCreateRequest item : items) {
      BigDecimal[] split = computeItemHours(item.getStartTime(), item.getEndTime(), config);
      sum = sum.add(split[0]).add(split[1]);
    }
    return sum;
  }
}