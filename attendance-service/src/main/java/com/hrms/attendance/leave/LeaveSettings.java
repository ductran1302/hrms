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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Tham số global tính quỹ phép (1 dòng, self-heal nếu thiếu). */
@Entity
@Table(name = "leave_settings")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LeaveSettings {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  /** Tháng bắt đầu năm tài chính (1..12). */
  @Column(name = "fy_start_month", nullable = false)
  private int fyStartMonth;

  /** Ngày bắt đầu năm tài chính (1..28). */
  @Column(name = "fy_start_day", nullable = false)
  private int fyStartDay;

  /** Ngày bắt đầu kỳ công trong tháng (1..28). */
  @Column(name = "period_start_day", nullable = false)
  private int periodStartDay;

  /** Ngày kết thúc kỳ công (1..31) — < period_start_day → thuộc tháng sau. */
  @Column(name = "period_end_day", nullable = false)
  private int periodEndDay;

  @Enumerated(EnumType.STRING)
  @Column(name = "saturday_policy", nullable = false, length = 20)
  private SaturdayPolicy saturdayPolicy;

  /** Ngưỡng % ngày công tối thiểu trong kỳ để hưởng phép tháng (x%). */
  @Column(name = "min_work_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal minWorkPercent;

  /**
   * Bắt buộc phê duyệt chính sách trước khi kích hoạt (workflow DRAFT → PENDING → APPROVED →
   * ACTIVE). false: activate thẳng từ DRAFT.
   */
  @Column(name = "require_policy_approval", nullable = false)
  private boolean requirePolicyApproval = true;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
