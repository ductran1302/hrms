package com.hrms.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scan {@code com.hrms} để dùng lại beans từ hrms-common (error handler, correlation filter,
 * Kafka-Avro config, outbox publisher). Cấu hình JPA/scheduling nằm riêng trong
 * {@code config.PersistenceConfig} — không để annotation JPA trên class này vì
 * {@code @WebMvcTest} cũng load class chính và sẽ kéo theo EntityManagerFactory.
 */
@SpringBootApplication(scanBasePackages = "com.hrms")
public class IdentityServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(IdentityServiceApplication.class, args);
  }
}
