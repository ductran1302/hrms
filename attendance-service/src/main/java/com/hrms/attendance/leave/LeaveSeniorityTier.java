package com.hrms.attendance.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bậc thâm niên của một version chính sách (VN_TIERED): áp dụng bonus khi nhân viên
 * đã ĐỦ {@code minYears} năm (VD luật VN: 0/5/10/15/20 → +0/2/4/6/8).
 */
@Entity
@Table(name = "leave_seniority_tiers")
@Getter
@Setter
@NoArgsConstructor
public class LeaveSeniorityTier {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  /** Bậc áp dụng từ khi nhân viên đủ minYears năm. */
  @Column(name = "min_years", nullable = false)
  private int minYears;

  /** Bonus ngày phép của bậc này (cộng vào baseDays). */
  @Column(name = "bonus_days", nullable = false, precision = 8, scale = 4)
  private BigDecimal bonusDays;
}
