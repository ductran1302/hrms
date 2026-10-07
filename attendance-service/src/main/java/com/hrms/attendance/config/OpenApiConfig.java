package com.hrms.attendance.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình OpenAPI: định nghĩa scheme {@code bearerAuth} (JWT) — Swagger UI hiển thị nút
 * Authorize để dán access_token (xem README — mục "Lấy token").
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(title = "Attendance API", version = "0.1.0"),
    security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {
}
