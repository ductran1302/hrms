package com.hrms.identity.audit;

import com.hrms.common.audit.AuditLog;
import com.hrms.common.audit.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit/logs")
@RequiredArgsConstructor
public class AuditLogController {

  private final AuditLogRepository auditLogRepository;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public Page<AuditLog> list(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String targetId,
      @RequestParam(required = false) String outcome,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    Specification<AuditLog> spec = (root, query, cb) -> {
      List<Predicate> preds = new ArrayList<>();
      if (actor != null && !actor.isBlank()) {
        preds.add(cb.like(cb.lower(root.get("actor")), "%" + actor.toLowerCase() + "%"));
      }
      if (action != null && !action.isBlank()) {
        preds.add(cb.equal(root.get("action"), action));
      }
      if (targetType != null && !targetType.isBlank()) {
        preds.add(cb.equal(root.get("targetType"), targetType));
      }
      if (targetId != null && !targetId.isBlank()) {
        preds.add(cb.equal(root.get("targetId"), targetId));
      }
      if (outcome != null && !outcome.isBlank()) {
        preds.add(cb.equal(root.get("outcome"), outcome));
      }
      if (from != null) {
        preds.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
      }
      if (to != null) {
        preds.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
      }
      return cb.and(preds.toArray(new Predicate[0]));
    };
    Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "occurredAt"));
    return auditLogRepository.findAll(spec, pageable);
  }
}
