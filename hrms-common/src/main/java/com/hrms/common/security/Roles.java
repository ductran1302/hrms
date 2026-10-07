package com.hrms.common.security;

/** Tên role realm Keycloak — dùng cho {@code @PreAuthorize("hasRole('HR_ADMIN')")} etc. */
public final class Roles {

  public static final String HR_ADMIN = "HR_ADMIN";
  public static final String MANAGER = "MANAGER";
  public static final String EMPLOYEE = "EMPLOYEE";

  private Roles() {}
}
