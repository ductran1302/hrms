package com.hrms.attendance.department;

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
 * Mirror phòng ban trong attendance_db — đọc từ Kafka event của identity
 * (không join DB chéo). PK là departmentId nhận từ event (không sinh mới).
 * Dùng tính scope DEPT_LEAD: phòng mình quản lý + nhánh con.
 */
@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Department {

  @Id
  @Column(name = "department_id", length = 64)
  private String departmentId;

  @Column(name = "code", length = 50, nullable = false)
  private String code;

  @Column(name = "name", length = 100, nullable = false)
  private String name;

  @Column(name = "parent_id", length = 64)
  private String parentId;

  @Column(name = "manager_id", length = 64)
  private String managerId;

  @Column(name = "status", length = 20, nullable = false)
  private String status;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
