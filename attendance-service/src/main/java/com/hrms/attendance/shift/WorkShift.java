package com.hrms.attendance.shift;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Ca làm việc. Cả FIXED và FLEXIBLE đều có khung start/end work + start/end break;
 * workingTime (phút) = thời gian làm để đủ 1 công, breakingTime (phút) = nghỉ giữa ca.
 */
@Entity
@Table(name = "work_shifts")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class WorkShift {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "shift_type", nullable = false, length = 20)
  private ShiftType shiftType;

  @Column(name = "start_work_time", nullable = false)
  private LocalTime startWorkTime;

  @Column(name = "end_work_time", nullable = false)
  private LocalTime endWorkTime;

  /** Giờ bắt đầu nghỉ giữa ca (null = ca không có giờ nghỉ). */
  @Column(name = "start_break_time")
  private LocalTime startBreakTime;

  @Column(name = "end_break_time")
  private LocalTime endBreakTime;

  /** Thời gian làm việc để đủ 1 công — PHÚT (VD 480 = 8h). */
  @Column(name = "working_time", nullable = false)
  private int workingTime;

  /** Thời gian nghỉ giải lao giữa ca — PHÚT (VD 60 = 1h). */
  @Column(name = "breaking_time", nullable = false)
  private int breakingTime;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
