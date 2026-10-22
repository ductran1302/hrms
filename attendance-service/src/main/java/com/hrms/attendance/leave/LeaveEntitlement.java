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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Dòng cấp phép trong ledger quỹ phép — mỗi nguồn 1 dòng, audit rõ ràng. */
@Entity
@Table(name = "leave_entitlements")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LeaveEntitlement {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "employee_id", nullable = false, length = 64)
  private String employeeId;

  @Column(name = "year", nullable = false)
  private int year;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private LeaveSource source;

  @Column(name = "days", nullable = false, precision = 8, scale = 4)
  private BigDecimal days;

  @Column(name = "reason", length = 300)
  private String reason;

  /**
   * Dòng do recalc tự quản lý (BASE/SENIORITY/POSITION/CARRYOVER auto) — recalc cập nhật/ghi đè.
   * false = ADMIN sửa tay (vd chuyển năm) → recalc giữ nguyên.
   */
  @Column(name = "is_auto", nullable = false)
  private boolean isAuto = true;

  @Column(name = "granted_by", length = 100)
  private String grantedBy;

  /**
   * Snapshot phiên bản chính sách dùng để tính dòng này (truy vết effective-dating). Dòng auto
   * theo policy → id version in-force lúc tính; BONUS/ADJUSTMENT không phụ thuộc policy → null.
   */
  @Column(name = "policy_id")
  private UUID policyId;

  /** Thời điểm tính/lần lưu cuối của dòng (audit). */
  @Column(name = "calculated_at")
  private Instant calculatedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
