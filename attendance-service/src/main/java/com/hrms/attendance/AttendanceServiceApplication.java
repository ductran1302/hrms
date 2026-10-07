package com.hrms.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scan {@code com.hrms} để dùng lại beans từ hrms-common (error handler, correlation filter,
 * Kafka-Avro config). Cấu hình JPA nằm riêng trong {@code config.PersistenceConfig} — không để
 * annotation JPA trên class này vì {@code @WebMvcTest} cũng load class chính.
 */
@SpringBootApplication(scanBasePackages = "com.hrms")
public class AttendanceServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AttendanceServiceApplication.class, args);
  }
}
