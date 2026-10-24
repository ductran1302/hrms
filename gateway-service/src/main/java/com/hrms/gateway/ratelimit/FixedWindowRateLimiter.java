package com.hrms.gateway.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter cửa sổ cố định (fixed window) trong bộ nhớ — không cần Redis.
 *
 * <p>Mỗi key (username hoặc IP) có tối đa {@code limitPerWindow} lượt trong một
 * cửa sổ {@link #WINDOW_SECONDS} giây. Đơn giản, đủ cho 1 instance gateway; nếu
 * scale nhiều instance phải thay bằng Redis/Bucket4j phân tán.
 *
 * <p>Bộ nhớ có giới hạn: khi vượt {@link #MAX_KEYS} key, dọn các cửa sổ cũ hơn
 * 1 phút để tránh phình do key rác.
 */
public final class FixedWindowRateLimiter {

  static final long WINDOW_SECONDS = 60L;
  private static final int MAX_KEYS = 50_000;

  private final int limitPerWindow;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  public FixedWindowRateLimiter(int limitPerWindow) {
    if (limitPerWindow <= 0) {
      throw new IllegalArgumentException("limitPerWindow phải > 0");
    }
    this.limitPerWindow = limitPerWindow;
  }

  /**
   * Ghi nhận 1 lượt cho {@code key} tại thời điểm {@code nowEpochSecond}.
   *
   * @return kết quả cho phép/từ chối kèm số giây nên thử lại (chỉ khi bị từ chối)
   */
  public Result tryAcquire(String key, long nowEpochSecond) {
    long windowId = Math.floorDiv(nowEpochSecond, WINDOW_SECONDS);
    Window current = windows.compute(key,
        (k, prev) -> prev == null || prev.windowId != windowId
            ? new Window(windowId, 1)
            : new Window(windowId, prev.count + 1));

    if (windows.size() > MAX_KEYS) {
      evictStale(windowId);
    }

    if (current.count <= limitPerWindow) {
      return Result.permit();
    }
    long retryAfter = WINDOW_SECONDS - Math.floorMod(nowEpochSecond, WINDOW_SECONDS);
    return Result.deny(retryAfter);
  }

  private void evictStale(long currentWindowId) {
    windows.entrySet().removeIf(e -> e.getValue().windowId < currentWindowId - 1);
  }

  private record Window(long windowId, int count) {
  }

  /** Kết quả 1 lần kiểm tra. */
  public record Result(boolean allowed, long retryAfterSeconds) {

    static Result permit() {
      return new Result(true, 0L);
    }

    static Result deny(long retryAfterSeconds) {
      return new Result(false, retryAfterSeconds);
    }
  }
}
