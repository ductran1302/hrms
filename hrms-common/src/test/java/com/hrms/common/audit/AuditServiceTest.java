package com.hrms.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.hrms.common.web.CorrelationIdFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

  @Mock
  private AuditLogRepository auditLogRepository;

  @InjectMocks
  private AuditService auditService;

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    MDC.clear();
  }

  @Test
  void success_withJwt_setsActorCorrelationAndClientIp() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("subject-1")
        .claim("preferred_username", "hr")
        .build();
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwt, List.of(), "hr"));
    MDC.put(CorrelationIdFilter.MDC_KEY, "corr-1");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    UUID targetId = UUID.randomUUID();
    auditService.success("EMPLOYEE_CREATE", "Employee", targetId, "employeeCode=E1");

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    AuditLog entry = captor.getValue();
    assertEquals("hr", entry.getActor());
    assertEquals("SUCCESS", entry.getOutcome());
    assertEquals("EMPLOYEE_CREATE", entry.getAction());
    assertEquals(targetId.toString(), entry.getTargetId());
    assertEquals("corr-1", entry.getCorrelationId());
    assertEquals("203.0.113.7", entry.getClientIp());
    assertTrue(entry.getOccurredAt() != null);
  }

  @Test
  void success_withoutAuthentication_usesSystemActor() {
    auditService.success("CONTRACT_EXPIRE", "Contract", UUID.randomUUID(), null);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    assertEquals(AuditService.SYSTEM, captor.getValue().getActor());
  }

  @Test
  void failure_truncatesDetailTo500Chars() {
    auditService.failure("EMPLOYEE_UPDATE", "Employee", UUID.randomUUID(), "x".repeat(700));

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    assertEquals(500, captor.getValue().getDetail().length());
    assertEquals("FAILURE", captor.getValue().getOutcome());
  }
}
