package com.hrms.gateway.ratelimit;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Giới hạn tần suất gọi API tại gateway (single entry point).
 *
 * <p>Key = username từ JWT (đã xác thực bởi Security filter chain, chạy trước
 * GlobalFilter) hoặc IP client nếu chưa có principal. Chỉ áp cho {@code /api/**};
 * actuator/health không bị giới hạn. Vượt hạn mức → 429 + {@code Retry-After}.
 *
 * <p>Cấu hình: {@code hrms.rate-limit.enabled} (default true),
 * {@code hrms.rate-limit.requests-per-minute} (default 600).
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
  private static final String API_PREFIX = "/api/";

  private final boolean enabled;
  private final FixedWindowRateLimiter limiter;

  public RateLimitFilter(
      @Value("${hrms.rate-limit.enabled:true}") boolean enabled,
      @Value("${hrms.rate-limit.requests-per-minute:600}") int requestsPerMinute) {
    this.enabled = enabled;
    this.limiter = new FixedWindowRateLimiter(requestsPerMinute);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!enabled || !exchange.getRequest().getPath().value().startsWith(API_PREFIX)) {
      return chain.filter(exchange);
    }
    return exchange.getPrincipal()
        .map(principal -> keyFor(principal, exchange))
        .defaultIfEmpty(clientIp(exchange))
        .flatMap(key -> applyLimit(exchange, chain, key));
  }

  /** Username đã xác thực; fallback IP nếu principal không có tên. */
  static String keyFor(Principal principal, ServerWebExchange exchange) {
    String name = principal.getName();
    return name != null && !name.isBlank() ? name : clientIp(exchange);
  }

  private Mono<Void> applyLimit(ServerWebExchange exchange, GatewayFilterChain chain, String key) {
    FixedWindowRateLimiter.Result result =
        limiter.tryAcquire(key, Instant.now().getEpochSecond());
    if (result.allowed()) {
      return chain.filter(exchange);
    }
    log.debug("Rate limit vượt hạn mức cho '{}' — {}s", key, result.retryAfterSeconds());
    return reject(exchange, result.retryAfterSeconds());
  }

  private Mono<Void> reject(ServerWebExchange exchange, long retryAfterSeconds) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    response.getHeaders().set("Retry-After", String.valueOf(retryAfterSeconds));
    byte[] body = ("{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":"
        + "\"Quá nhiều yêu cầu, vui lòng thử lại sau " + retryAfterSeconds + " giây\"}")
        .getBytes(StandardCharsets.UTF_8);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
  }

  private static String clientIp(ServerWebExchange exchange) {
    String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
    return remote != null ? remote.getAddress().getHostAddress() : "unknown";
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 100;
  }
}
