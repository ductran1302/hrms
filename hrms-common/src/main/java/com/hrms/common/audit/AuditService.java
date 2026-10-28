package com.hrms.common.audit;

import com.hrms.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi audit log (append-only) cho thao tác nhạy cảm.
 *
 * <p>Actor lấy từ SecurityContext (JWT {@code preferred_username}, fallback subject);
 * không có người dùng (Kafka consumer/scheduler) → {@value #SYSTEM}. Correlation id lấy từ
 * MDC do {@link CorrelationIdFilter} set; IP lấy từ request hiện tại (nếu có).
 *
 * <p>{@code @Transactional} REQUIRED: join transaction nghiệp vụ nếu có (audit và dữ liệu
 * commit/rollback cùng nhau); nếu gọi ngoài transaction (vd AccountService — thao tác trên
 * Keycloak) thì tự mở transaction riêng.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

  public static final String SYSTEM = "system";
  private static final Logger log = LoggerFactory.getLogger(AuditService.class);
  private static final int MAX_DETAIL = 500;

  private final AuditLogRepository auditLogRepository;

  @Transactional
  public void success(String action, String targetType, Object targetId, String detail) {
    write(action, targetType, targetId, "SUCCESS", detail);
  }

  @Transactional
  public void failure(String action, String targetType, Object targetId, String detail) {
    write(action, targetType, targetId, "FAILURE", detail);
  }

  private void write(String action, String targetType, Object targetId, String outcome,
      String detail) {
    AuditLog entry = new AuditLog();
    entry.setOccurredAt(Instant.now());
    entry.setActor(currentActor());
    entry.setAction(action);
    entry.setTargetType(targetType);
    entry.setTargetId(targetId == null ? null : targetId.toString());
    entry.setOutcome(outcome);
    entry.setDetail(truncate(detail));
    entry.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
    entry.setClientIp(clientIp());
    auditLogRepository.save(entry);
    log.info("AUDIT action={} actor={} target={}:{} outcome={} correlationId={}",
        action, entry.getActor(), targetType, entry.getTargetId(), outcome,
        entry.getCorrelationId());
  }

  private static String currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return SYSTEM;
    }
    if (authentication.getPrincipal() instanceof Jwt jwt) {
      String username = jwt.getClaimAsString("preferred_username");
      if (username != null && !username.isBlank()) {
        return username;
      }
      return jwt.getSubject() != null ? jwt.getSubject() : SYSTEM;
    }
    String name = authentication.getName();
    return name != null && !name.isBlank() ? name : SYSTEM;
  }

  private static String clientIp() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servlet) {
      HttpServletRequest request = servlet.getRequest();
      String forwardedFor = request.getHeader("X-Forwarded-For");
      if (forwardedFor != null && !forwardedFor.isBlank()) {
        return forwardedFor.split(",")[0].trim();
      }
      return request.getRemoteAddr();
    }
    return null;
  }

  private static String truncate(String detail) {
    if (detail == null || detail.length() <= MAX_DETAIL) {
      return detail;
    }
    return detail.substring(0, MAX_DETAIL);
  }
}
