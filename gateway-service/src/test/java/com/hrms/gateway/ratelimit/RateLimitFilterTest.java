package com.hrms.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

class RateLimitFilterTest {

  private final ServerWebExchange exchange =
      MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/employees").build());

  @Test
  void keyFor_authenticatedPrincipal_usesUsername() {
    Principal principal = () -> "hr";

    assertThat(RateLimitFilter.keyFor(principal, exchange)).isEqualTo("hr");
  }

  @Test
  void keyFor_principalWithoutName_fallsBackToIp() {
    Principal principal = () -> null;

    assertThat(RateLimitFilter.keyFor(principal, exchange)).isEqualTo("unknown");
  }
}
