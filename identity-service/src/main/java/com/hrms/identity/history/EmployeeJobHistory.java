package com.hrms.identity.history;

import com.hrms.common.base.BaseEntity;
import com.hrms.identity.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lịch sử thay đổi job info (model theo effective history của SAP SuccessFactors EC). */
@Entity
@Table(name = "employee_job_history")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeJobHistory extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @Column(name = "effective_date", nullable = false)
  private Instant effectiveDate;

  @Column(name = "field", nullable = false, length = 50)
  private String field;

  @Column(name = "old_value", length = 500)
  private String oldValue;

  @Column(name = "new_value", length = 500)
  private String newValue;

  @Column(name = "actor_id", length = 64)
  private String actorId;

  /** Lý do thay đổi (chỉ ghi cho terminate) — mô tả đầy đủ nằm ở history, không chỉ outbox. */
  @Column(name = "reason", length = 500)
  private String reason;
}
