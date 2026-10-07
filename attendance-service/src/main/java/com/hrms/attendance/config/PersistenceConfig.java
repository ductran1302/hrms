package com.hrms.attendance.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA cho attendance-service. Tách khỏi class chính để {@code @WebMvcTest} không khởi tạo
 * EntityManagerFactory.
 *
 * <p>Chỉ scan {@code com.hrms.attendance}: hrms-common có entity {@code OutboxEvent}/repository
 * (bảng outbox_events thuộc identity_db) — nếu scan chung {@code com.hrms}, Hibernate validate
 * sẽ fail vì attendance_db không có bảng đó.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.hrms.attendance")
@EntityScan(basePackages = "com.hrms.attendance")
@EnableJpaAuditing
public class PersistenceConfig {
}
