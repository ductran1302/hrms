package com.hrms.common.exception;

/** 422 — ném khi dữ liệu hợp lệ về format nhưng vi phạm quy tắc nghiệp vụ. */
public class BusinessRuleException extends HrmsException {

  public BusinessRuleException(String message) {
    super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
  }
}
