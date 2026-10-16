package com.hrms.attendance.shift;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Gán ca cho nhân viên theo ngày hiệu lực — mỗi lần gán 1 dòng mới (lịch sử),
 * module chấm công đọc bản gán có effective_from ≤ ngày công.
 */
@Entity
@Table(name = "shift_assignments")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ShiftAssignment {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "employee_id", nullable = false, length = 64)
  private String employeeId;

  @Column(name = "work_shift_id", nullable = false)
  private UUID workShiftId;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  /** preferred_username của HR tạo gán (audit). */
  @Column(name = "assigned_by", length = 100)
  private String assignedBy;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
