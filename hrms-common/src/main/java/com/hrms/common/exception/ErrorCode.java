package com.hrms.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi chuẩn của HRMS — FE map thẳng theo {@code code} thay vì parse message.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
  BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa xác thực"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền truy cập"),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ"),
  CONFLICT(HttpStatus.CONFLICT, "Dữ liệu xung đột"),
  MEDIA_TYPE_NOT_SUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng không được hỗ trợ"),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File vượt quá giới hạn cho phép"),
  BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Vi phạm quy tắc nghiệp vụ"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi máy chủ");

  private final HttpStatus status;
  private final String defaultMessage;
}
