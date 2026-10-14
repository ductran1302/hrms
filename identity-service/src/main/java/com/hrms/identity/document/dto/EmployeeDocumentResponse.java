package com.hrms.identity.document.dto;

import java.time.Instant;
import java.util.UUID;

public record EmployeeDocumentResponse(
    UUID id,
    UUID employeeId,
    String docType,
    String fileName,
    String contentType,
    Long fileSize,
    String note,
    String uploadedBy,
    Instant uploadedAt) {}
