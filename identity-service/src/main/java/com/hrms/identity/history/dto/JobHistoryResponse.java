package com.hrms.identity.history.dto;

import java.time.Instant;
import java.util.UUID;

public record JobHistoryResponse(
    UUID id,
    UUID employeeId,
    Instant effectiveDate,
    String field,
    String oldValue,
    String newValue,
    String actorId) {}
