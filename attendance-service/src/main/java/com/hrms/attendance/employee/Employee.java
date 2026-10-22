package com.hrms.attendance.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Mirror nhân sự trong attendance_db — đọc từ Kafka event của identity (không join DB chéo).
 * PK là employeeId nhận từ event (không sinh mới).
 */
@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Employee {

  @Id
  @Column(name = "employee_id", length = 64)
  private String employeeId;

  @Column(name = "employee_code", length = 20, nullable = false)
  private String employeeCode;

  @Column(name = "full_name", length = 200, nullable = false)
  private String fullName;

  @Column(name = "email", length = 200)
  private String email;

  @Column(name = "department_id", length = 64)
  private String departmentId;

  @Column(name = "department_name", length = 100)
  private String departmentName;

  @Column(name = "position", length = 100)
  private String position;

  /** Role KC mirror từ event identity (comma-joined, vd "ADMIN,DEPT_LEAD") — tính phép theo role. */
  @Column(name = "roles", length = 200)
  private String roles;

  @Column(name = "manager_id", length = 64)
  private String managerId;

  @Column(name = "status", length = 20, nullable = false)
  private String status;

  @Column(name = "hired_at")
  private Instant hiredAt;

  @Column(name = "terminated_at")
  private Instant terminatedAt;

  @Column(name = "termination_reason", length = 500)
  private String terminationReason;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
