package com.hrms.attendance.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA cho attendance-service. Tách khỏi class chính để {@code @WebMvcTest} không khởi tạo
 * EntityManagerFactory.
 *
 * <p>Không scan toàn bộ {@code com.hrms}: hrms-common có entity {@code OutboxEvent}/repository
 * (bảng outbox_events thuộc identity_db) — nếu scan chung, Hibernate validate sẽ fail vì
 * attendance_db không có bảng đó. Chỉ thêm package cụ thể cần dùng ({@code common.audit}).
 */
@Configuration
@EnableJpaRepositories(basePackages = {"com.hrms.attendance", "com.hrms.common.audit"})
@EntityScan(basePackages = {"com.hrms.attendance", "com.hrms.common.audit"})
@EnableJpaAuditing
public class PersistenceConfig {
}
