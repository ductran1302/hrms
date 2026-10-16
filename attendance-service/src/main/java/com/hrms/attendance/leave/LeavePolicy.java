package com.hrms.attendance.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Chính sách quỹ phép theo năm — 1 policy/năm, tạo mặc định nếu HR chưa cấu hình. */
@Entity
@Table(name = "leave_policies")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LeavePolicy {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "year", nullable = false, unique = true)
  private int year;

  /** Phép hằng năm chuẩn (ngày). */
  @Column(name = "base_days", nullable = false, precision = 8, scale = 4)
  private BigDecimal baseDays;

  /** Mỗi N năm thâm niên được +bonusDays (0 = tắt tính thâm niên). */
  @Column(name = "seniority_step_years", nullable = false)
  private int seniorityStepYears;

  @Column(name = "seniority_bonus_days", nullable = false, precision = 8, scale = 4)
  private BigDecimal seniorityBonusDays;

  /** Trần tổng bonus thâm niên. */
  @Column(name = "seniority_max_bonus", nullable = false, precision = 8, scale = 4)
  private BigDecimal seniorityMaxBonus;

  /** Trần phép được chuyển từ năm trước (tổng dòng CARRYOVER ≤ giá trị này). */
  @Column(name = "carryover_max", nullable = false, precision = 8, scale = 4)
  private BigDecimal carryoverMax;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
