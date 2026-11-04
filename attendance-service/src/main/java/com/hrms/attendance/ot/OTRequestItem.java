package com.hrms.attendance.ot;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chi tiết 1 ngày OT — con của {@link OTRequest}. */
@Entity
@Table(name = "ot_request_items")
@Getter
@Setter
@NoArgsConstructor
public class OTRequestItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "request_id", nullable = false)
  private OTRequest request;

  /** Ngày OT. */
  @Column(name = "ot_date", nullable = false)
  private java.time.LocalDate otDate;

  /** Thời gian bắt đầu/kết thúc quy đổi thực tế (có timezone nếu cần). */
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "start_time", nullable = false)
  private LocalDateTime startTime;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "end_time", nullable = false)
  private LocalDateTime endTime;

  /** Loại ngày tính hệ số. */
  @Enumerated(EnumType.STRING)
  @Column(name = "day_type", nullable = false, length = 20)
  private DayType dayType;

  /** Số giờ OT ban ngày (sau khi tính break) — dùng cho tính lương. */
  @Column(name = "day_hours", nullable = false, precision = 5, scale = 2)
  private BigDecimal dayHours = BigDecimal.ZERO;

  /** Số giờ OT ban đêm — dùng cho tính lương. */
  @Column(name = "night_hours", nullable = false, precision = 5, scale = 2)
  private BigDecimal nightHours = BigDecimal.ZERO;

  /** Thời gian check-in thực tế (nếu có). */
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "actual_start_time")
  private LocalDateTime actualStartTime;

  /** Thời gian check-out thực tế (nếu có). */
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "actual_end_time")
  private LocalDateTime actualEndTime;

  /** Giờ OT chốt tính lương = min(phê duyệt, thực tế). */
  @Column(name = "approved_hours", nullable = false, precision = 5, scale = 2)
  private BigDecimal approvedHours = BigDecimal.ZERO;
}