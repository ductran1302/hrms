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

/** Bonus quỹ phép theo chức vụ — match {@code employees.position} theo tên (không FK). */
@Entity
@Table(name = "leave_position_quotas")
@Getter
@Setter
@NoArgsConstructor
public class LeavePositionQuota {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @Column(name = "position_name", nullable = false, length = 100)
  private String positionName;

  @Column(name = "bonus_days", nullable = false, precision = 8, scale = 4)
  private BigDecimal bonusDays;
}
