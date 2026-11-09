package com.hrms.attendance.schedule;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mẫu lịch tuần (policy): mỗi thứ trỏ tới 1 ca (null = nghỉ). Thứ 7 luân phiên dùng
 * {@code satShiftId} (tuần chẵn) và {@code satAlternateShiftId} (tuần lẻ, null = nghỉ),
 * parity neo {@code rotationAnchorDate} — phải là Thứ 7.
 */
@Entity
@Table(name = "work_schedule_policies")
@Getter
@Setter
@NoArgsConstructor
public class WorkSchedulePolicy extends BaseEntity {

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "mon_shift_id")
  private UUID monShiftId;

  @Column(name = "tue_shift_id")
  private UUID tueShiftId;

  @Column(name = "wed_shift_id")
  private UUID wedShiftId;

  @Column(name = "thu_shift_id")
  private UUID thuShiftId;

  @Column(name = "fri_shift_id")
  private UUID friShiftId;

  @Column(name = "sat_shift_id")
  private UUID satShiftId;

  @Column(name = "sat_alternate_shift_id")
  private UUID satAlternateShiftId;

  @Column(name = "sun_shift_id")
  private UUID sunShiftId;

  /** Mốc parity tuần chẵn/lẻ (Thứ 7) — bắt buộc khi {@code satAlternateShiftId} có. */
  @Column(name = "rotation_anchor_date")
  private LocalDate rotationAnchorDate;

  /** Policy mặc định cho NV chưa được gán (duy nhất — partial unique index). */
  @Column(name = "is_default", nullable = false)
  private boolean defaultPolicy;

  @Column(name = "policy_group_id", nullable = false)
  private java.util.UUID policyGroupId;

  @Column(name = "effective_from")
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(name = "version_no", nullable = false)
  private int versionNo = 1;

  @Column(name = "active", nullable = false)
  private boolean active = true;
}
