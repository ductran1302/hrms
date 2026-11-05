package com.hrms.attendance.schedule;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gán policy lịch cho nhân viên theo ngày hiệu lực — bản mới nhất có
 * {@code effectiveFrom <= ngày} là bản đang áp dụng.
 */
@Entity
@Table(name = "employee_schedule_assignments")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeScheduleAssignment extends BaseEntity {

  @Column(name = "employee_id", nullable = false, length = 64)
  private String employeeId;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  /** preferred_username của HR tạo gán (audit). */
  @Column(name = "assigned_by", length = 100)
  private String assignedBy;
}
