package com.hrms.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

  private static final long NOW = 1_700_000_010L;

  @Test
  void tryAcquire_withinLimit_allows() {
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3);

    assertThat(limiter.tryAcquire("hr", NOW).allowed()).isTrue();
    assertThat(limiter.tryAcquire("hr", NOW).allowed()).isTrue();
    assertThat(limiter.tryAcquire("hr", NOW).allowed()).isTrue();
  }

  @Test
  void tryAcquire_exceedingLimit_deniesWithRetryAfter() {
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2);
    limiter.tryAcquire("hr", NOW);
    limiter.tryAcquire("hr", NOW);

    FixedWindowRateLimiter.Result result = limiter.tryAcquire("hr", NOW);

    assertThat(result.allowed()).isFalse();
    assertThat(result.retryAfterSeconds()).isEqualTo(30L);
  }

  @Test
  void tryAcquire_nextWindow_resetsCounter() {
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1);
    assertThat(limiter.tryAcquire("hr", NOW).allowed()).isTrue();
    assertThat(limiter.tryAcquire("hr", NOW).allowed()).isFalse();

    assertThat(limiter.tryAcquire("hr", NOW + FixedWindowRateLimiter.WINDOW_SECONDS).allowed())
        .isTrue();
  }

  @Test
  void tryAcquire_differentKeys_independentCounters() {
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1);
    assertThat(limiter.tryAcquire("hr", NOW).allowed()).isTrue();
    assertThat(limiter.tryAcquire("hr", NOW).allowed()).isFalse();
    assertThat(limiter.tryAcquire("manager1", NOW).allowed()).isTrue();
  }

  @Test
  void constructor_withNonPositiveLimit_throws() {
    assertThatThrownBy(() -> new FixedWindowRateLimiter(0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
