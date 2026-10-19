package com.hrms.identity.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin đặt lại mật khẩu cho tài khoản khác (mật khẩu mới, không phải temporary). */
public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 64) String newPassword) {}
