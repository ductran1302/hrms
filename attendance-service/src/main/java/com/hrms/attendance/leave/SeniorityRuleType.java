package com.hrms.attendance.leave;

/**
 * Quy tắc tính bonus thâm niên — mở rộng được (Hàn/Nhật...) bằng cách thêm enum +
 * {@link com.hrms.attendance.leave.seniority.SeniorityRule} mới, không đổi schema
 * (số liệu theo bảng {@code leave_seniority_tiers}).
 */
public enum SeniorityRuleType {

  /**
   * Luật VN (NĐ 145/2020): bậc theo số năm hoàn thành — đủ 5/10/15/20 năm → tổng
   * 14/16/18/20 ngày (base 12 + bonus 2/4/6/8). Tính tại ngày tính (asOf).
   */
  VN_TIERED,

  /**
   * Logic cũ (legacy): mỗi {@code seniorityStepYears} năm được {@code seniorityBonusDays},
   * tính theo năm dương lịch, trần {@code seniorityMaxBonus}.
   */
  LINEAR
}
