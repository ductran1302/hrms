package com.hrms.identity.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JPA + scheduling cho identity-service. Tách khỏi class chính để {@code @WebMvcTest}
 * không khởi tạo EntityManagerFactory.
 *
 * <p>{@code @EnableJpaAuditing} bắt buộc — outbox publisher order theo {@code createdAt}.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.hrms")
@EntityScan(basePackages = "com.hrms")
@EnableJpaAuditing
@EnableScheduling
public class PersistenceConfig {
}
