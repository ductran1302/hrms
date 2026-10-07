package com.hrms.identity.employee;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee extends BaseEntity {

  @Column(name = "employee_code", nullable = false, length = 20)
  private String employeeCode;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(name = "email", length = 200)
  private String email;

  /** Chưa có department-service — lưu UUID dạng string, sẽ map sang event khi có. */
  @Column(name = "department_id", length = 64)
  private String departmentId;

  @Column(name = "department_name", length = 100)
  private String departmentName;

  @Column(name = "position", length = 100)
  private String position;

  @Column(name = "manager_id", length = 64)
  private String managerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private EmployeeStatus status;

  @Column(name = "hired_at")
  private Instant hiredAt;
}
