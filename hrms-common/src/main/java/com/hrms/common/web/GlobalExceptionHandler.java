package com.hrms.common.web;

import com.hrms.common.exception.ErrorCode;
import com.hrms.common.exception.HrmsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Exception handler dùng chung. Mọi service MVC chỉ cần component-scan {@code com.hrms}
 * là có sẵn body lỗi + correlationId.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(HrmsException.class)
  public ResponseEntity<ApiError> handleHrms(HrmsException ex, HttpServletRequest req) {
    ErrorCode errorCode = ex.getErrorCode();
    return respond(errorCode.getStatus(), errorCode.name(), ex.getMessage(), req, null, ex);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
      HttpServletRequest req) {
    List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
        .map(this::toViolation)
        .toList();
    return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(),
        ErrorCode.VALIDATION_FAILED.getDefaultMessage(), req, violations, ex);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
      HttpServletRequest req) {
    List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
        .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
        .toList();
    return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(),
        ErrorCode.VALIDATION_FAILED.getDefaultMessage(), req, violations, ex);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
      HttpServletRequest req) {
    return respond(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.name(),
        "Request body không hợp lệ hoặc sai định dạng", req, null, ex);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
      HttpServletRequest req) {
    return respond(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.name(),
        "Thiếu tham số bắt buộc: " + ex.getParameterName(), req, null, ex);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
      HttpServletRequest req) {
    return respond(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.name(),
        "Tham số '%s' sai kiểu dữ liệu".formatted(ex.getName()), req, null, ex);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
      HttpServletRequest req) {
    return respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.name(),
        "Phương thức %s không được hỗ trợ".formatted(ex.getMethod()), req, null, ex);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
      HttpServletRequest req) {
    return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.MEDIA_TYPE_NOT_SUPPORTED.name(),
        "Content-Type không được hỗ trợ", req, null, ex);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
      HttpServletRequest req) {
    return respond(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.name(),
        "Không tìm thấy tài nguyên: " + ex.getResourcePath(), req, null, ex);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
      HttpServletRequest req) {
    return respond(HttpStatus.CONFLICT, ErrorCode.CONFLICT.name(),
        "Dữ liệu vi phạm ràng buộc duy nhất (unique constraint)", req, null, ex);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
      HttpServletRequest req) {
    return respond(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.name(),
        ErrorCode.FORBIDDEN.getDefaultMessage(), req, null, ex);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
    return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
        ErrorCode.INTERNAL_ERROR.getDefaultMessage(), req, null, ex);
  }

  private ResponseEntity<ApiError> respond(HttpStatus status, String code, String message,
      HttpServletRequest req, List<ApiError.FieldViolation> fieldErrors, Exception ex) {
    ApiError body = new ApiError(
        java.time.Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        code,
        message,
        req.getRequestURI(),
        MDC.get(CorrelationIdFilter.MDC_KEY),
        fieldErrors);

    if (ex == null) {
      log.warn("{} {} -> {}: {}", code, req.getRequestURI(), status.value(), message);
    } else if (status.is5xxServerError()) {
      log.error("{} {} -> {}", code, req.getRequestURI(), status.value(), ex);
    } else {
      log.debug("{} {} -> {}: {}", code, req.getRequestURI(), status.value(), message, ex);
    }
    return ResponseEntity.status(status).body(body);
  }

  private ApiError.FieldViolation toViolation(FieldError fe) {
    return new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage());
  }
}
