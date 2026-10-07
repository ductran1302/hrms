package com.hrms.common.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Base exception của HRMS. Service ném exception này, {@code GlobalExceptionHandler}
 * tự map sang HTTP status + body chuẩn.
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class HrmsException extends RuntimeException {

  private final ErrorCode errorCode;

  public HrmsException(ErrorCode errorCode) {
    super(errorCode.getDefaultMessage());
    this.errorCode = errorCode;
  }

  public HrmsException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public HrmsException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }
}
