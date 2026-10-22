package com.hrms.attendance.leave.seniority;

import com.hrms.attendance.leave.LeavePolicy;
import com.hrms.attendance.leave.SeniorityRuleType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Tra {@link SeniorityRule} theo {@code leave_policies.seniority_rule}.
 *
 * <p>Spring tự nạp mọi bean {@code SeniorityRule} — thêm luật mới (Hàn/Nhật...) chỉ cần
 * enum mới + class {@code @Component}, không sửa class này.
 */
@Component
public class SeniorityRuleRegistry {

  private final Map<SeniorityRuleType, SeniorityRule> rules =
      new EnumMap<>(SeniorityRuleType.class);

  public SeniorityRuleRegistry(List<SeniorityRule> available) {
    for (SeniorityRule rule : available) {
      rules.put(rule.type(), rule);
    }
  }

  /**
   * Rule tính thâm niên cho policy. Rule chưa đăng ký / null (data cũ) → fallback LINEAR
   * (hành vi legacy, không vỡ tính toán).
   */
  public SeniorityRule resolve(LeavePolicy policy) {
    SeniorityRuleType type = policy.getSeniorityRule();
    SeniorityRule rule = type == null ? null : rules.get(type);
    return rule != null ? rule : rules.get(SeniorityRuleType.LINEAR);
  }
}
