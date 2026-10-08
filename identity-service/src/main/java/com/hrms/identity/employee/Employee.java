package com.hrms.identity.employee;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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

  /** FK → departments.id (V2; trước đây là string tự do). */
  @Column(name = "department_id")
  private UUID departmentId;

  @Column(name = "department_name", length = 100)
  private String departmentName;

  @Column(name = "position", length = 100)
  private String position;

  /** FK tự tham chiếu → employees.id (quản lý trực tiếp — dùng cho org chart). */
  @Column(name = "manager_id")
  private UUID managerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private EmployeeStatus status;

  @Column(name = "hired_at")
  private Instant hiredAt;

  // ---- Personal info (Bước 9 — hồ sơ mở rộng theo SF4C Personal Data) ----

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "gender", length = 10)
  private String gender;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "national_id", length = 20)
  private String nationalId;

  @Column(name = "address", length = 300)
  private String address;
}
