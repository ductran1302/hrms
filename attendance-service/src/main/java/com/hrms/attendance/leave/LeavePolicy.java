package com.hrms.attendance.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
 * Một phiên bản chính sách quỹ phép của một năm tài chính (effective-dated).
 *
 * <p>Một năm có nhiều version (unique year+version_no); version có khoảng hiệu lực
 * [effective_from, effective_to] và trạng thái {@link PolicyStatus}. Version đã dùng để tính
 * quyền lợi không bị ghi đè/xóa — thay đổi giữa năm = tạo version mới rồi activate.
 */
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

  @Column(name = "year", nullable = false)
  private int year;

  /** Version trong năm tài chính (bắt đầu từ 1, tăng dần). */
  @Column(name = "version_no", nullable = false)
  private int versionNo;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PolicyStatus status;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to", nullable = false)
  private LocalDate effectiveTo;

  /** Phép hằng năm chuẩn (ngày). */
  @Column(name = "base_days", nullable = false, precision = 8, scale = 4)
  private BigDecimal baseDays;

  /**
   * Quy tắc thâm niên (VN_TIERED mặc định = bậc luật VN; LINEAR = legacy). Bậc chi tiết
   * ở {@code leave_seniority_tiers}.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "seniority_rule", nullable = false, length = 30)
  private SeniorityRuleType seniorityRule = SeniorityRuleType.VN_TIERED;

  /** LINEAR: mỗi N năm thâm niên được +bonusDays (0 = tắt tính thâm niên). */
  @Column(name = "seniority_step_years", nullable = false)
  private int seniorityStepYears;

  @Column(name = "seniority_bonus_days", nullable = false, precision = 8, scale = 4)
  private BigDecimal seniorityBonusDays;

  /** Trần tổng bonus thâm niên. */
  /** LINEAR: trần tổng bonus thâm niên (VN_TIERED không dùng — bậc đã là giá trị tuyệt đối). */
  @Column(name = "seniority_max_bonus", nullable = false, precision = 8, scale = 4)
  private BigDecimal seniorityMaxBonus;

  /** Trần phép được chuyển từ năm trước (tổng dòng CARRYOVER ≤ giá trị này). */
  @Column(name = "carryover_max", nullable = false, precision = 8, scale = 4)
  private BigDecimal carryoverMax;

  @Column(name = "created_by", length = 100)
  private String createdBy;

  @Column(name = "approved_by", length = 100)
  private String approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "activated_by", length = 100)
  private String activatedBy;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
