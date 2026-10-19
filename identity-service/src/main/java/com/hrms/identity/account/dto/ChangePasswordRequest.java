package com.hrms.identity.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Đổi mật khẩu của chính mình — phải xác thực mật khẩu hiện tại trước. */
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 8, max = 64) String newPassword) {}
