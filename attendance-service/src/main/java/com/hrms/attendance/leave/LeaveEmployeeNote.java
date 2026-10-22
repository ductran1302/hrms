package com.hrms.attendance.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Ghi chú HR theo từng NV/năm trên màn Quỹ phép. */
@Entity
@Table(name = "leave_employee_notes")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LeaveEmployeeNote {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "employee_id", nullable = false, length = 64)
  private String employeeId;

  @Column(name = "year", nullable = false)
  private int year;

  @Column(name = "note", length = 500)
  private String note;

  @Column(name = "updated_by", length = 100)
  private String updatedBy;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
