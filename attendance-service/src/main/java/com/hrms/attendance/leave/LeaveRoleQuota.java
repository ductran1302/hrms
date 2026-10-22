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

/** Bonus quỹ phép theo role KC — match employees.roles (comma-joined). */
@Entity
@Table(name = "leave_role_quotas")
@Getter
@Setter
@NoArgsConstructor
public class LeaveRoleQuota {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @Column(name = "role", nullable = false, length = 30)
  private String role;

  @Column(name = "bonus_days", nullable = false, precision = 8, scale = 4)
  private BigDecimal bonusDays;
}
