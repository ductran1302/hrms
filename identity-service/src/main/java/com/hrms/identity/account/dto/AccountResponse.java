package com.hrms.identity.account.dto;

import java.util.List;

/** Tài khoản đăng nhập (Keycloak) — màn Cài đặt > Tài khoản. */
public record AccountResponse(
    String username,
    String fullName,
    String email,
    boolean enabled,
    List<String> roles,
    Long createdTimestamp,
    String createdBy,
    String updatedBy,
    String updatedAt) {}
