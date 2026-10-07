package com.hrms.common.exception;

/** 409 — ném khi trùng unique constraint nghiệp vụ (vd: employeeCode đã tồn tại). */
public class ConflictException extends HrmsException {

  public ConflictException(String message) {
    super(ErrorCode.CONFLICT, message);
  }
}
