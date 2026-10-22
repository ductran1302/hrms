package com.hrms.attendance.leave.seniority;

import com.hrms.attendance.leave.SeniorityRuleType;
import java.math.BigDecimal;

/**
 * Chiến lược tính bonus thâm niên — mỗi luật/quy định (VN, Hàn, Nhật...) 1 implementation.
 *
 * <p>Mở rộng: thêm {@code SeniorityRuleType} mới + class {@code @Component} implement interface
 * này (số liệu lấy từ {@code leave_seniority_tiers} theo policy) — không đổi schema, không
 * đụng {@code LeaveQuotaService}.
 */
public interface SeniorityRule {

  BigDecimal ZERO = new BigDecimal("0.0000");

  /** Kiểu rule mà implementation này đảm nhận (match {@code leave_policies.seniority_rule}). */
  SeniorityRuleType type();

  /** Bonus ngày phép thâm niên (scale 4, không âm). */
  BigDecimal bonus(SeniorityContext context);
}
