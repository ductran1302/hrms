package com.hrms.attendance.ot;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Đơn đăng ký OT master — 1 đơn gồm N item (mỗi item = 1 ngày). */
@Entity
@Table(name = "ot_requests")
@Getter
@Setter
@NoArgsConstructor
public class OTRequest extends BaseEntity {

  /** Mã đơn: OT20261029001. */
  @Column(name = "request_code", length = 30, unique = true, nullable = false)
  private String requestCode;

  /** FK plain UUID — không @ManyToOne (không join chéo DB). */
  @Column(name = "employee_id", length = 64, nullable = false)
  private String employeeId;

  @Column(name = "employee_code", length = 20, nullable = false)
  private String employeeCode;

  @Column(name = "full_name", length = 200)
  private String fullName;

  /** FK nguyên thủy (theo chuẩn project: plain UUID, có thể null). */
  @Column(name = "department_id")
  private UUID departmentId;

  /** Lý do OT. */
  @Column(name = "reason", columnDefinition = "text")
  private String reason;

  /** Tổng giờ OT dự kiến (số giờ thực, không áp dụng hệ số). */
  @Column(name = "total_hours", nullable = false, precision = 6, scale = 2)
  private BigDecimal totalHours = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OTRequestStatus status = OTRequestStatus.PENDING;

  /** Người phê duyệt. */
  @Column(name = "approver_id", length = 64)
  private String approverId;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Column(name = "rejection_reason", columnDefinition = "text")
  private String rejectionReason;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  @Column(name = "cancellation_reason", columnDefinition = "text")
  private String cancellationReason;
}