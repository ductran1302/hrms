package com.hrms.attendance.schedule;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Roster sinh sẵn theo (nhân viên, ngày). {@code workDate} = NGÀY BẮT ĐẦU ca
 * (ca qua đêm vẫn thuộc ngày bắt đầu). Cache có thể tính lại lazily.
 */
@Entity
@Table(name = "employee_shift_rosters")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeShiftRoster extends BaseEntity {

  @Column(name = "employee_id", nullable = false, length = 64)
  private String employeeId;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(name = "shift_id")
  private UUID shiftId;

  @Column(name = "is_day_off", nullable = false)
  private boolean dayOff;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private RosterSource source;

  @Column(name = "policy_id")
  private UUID policyId;

  @Column(name = "note", length = 500)
  private String note;
}
