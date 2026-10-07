package com.hrms.common.exception;

/** 404 — ném khi entity không tồn tại (đã check, không phải bug). */
public class ResourceNotFoundException extends HrmsException {

  public ResourceNotFoundException(String resource, Object key) {
    super(ErrorCode.RESOURCE_NOT_FOUND, "%s không tồn tại: %s".formatted(resource, key));
  }

  public ResourceNotFoundException(String message) {
    super(ErrorCode.RESOURCE_NOT_FOUND, message);
  }
}
