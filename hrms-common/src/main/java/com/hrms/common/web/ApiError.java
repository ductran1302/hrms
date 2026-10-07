package com.hrms.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Body lỗi chuẩn của mọi service HRMS. FE map theo {@code code}, hiển thị {@code message}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    String correlationId,
    List<FieldViolation> fieldErrors) {

  public record FieldViolation(String field, String message) {}
}
