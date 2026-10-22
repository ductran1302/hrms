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

/** Số ngày công thực tế của NV trong 1 kỳ công (nguồn điều kiện x%). */
@Entity
@Table(name = "leave_work_days")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LeaveWorkDay {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "employee_id", nullable = false, length = 64)
  private String employeeId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "work_days", nullable = false, precision = 6, scale = 2)
  private BigDecimal workDays;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 10)
  private WorkDaySource source;

  @Column(name = "updated_by", length = 100)
  private String updatedBy;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
