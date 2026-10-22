package com.hrms.attendance.leave.dto;

import com.hrms.attendance.leave.LeaveSettings;
import com.hrms.attendance.leave.SaturdayPolicy;
import java.math.BigDecimal;

public record LeaveSettingsResponse(
    int fyStartMonth,
    int fyStartDay,
    int periodStartDay,
    int periodEndDay,
    SaturdayPolicy saturdayPolicy,
    BigDecimal minWorkPercent,
    boolean requirePolicyApproval) {

  public static LeaveSettingsResponse from(LeaveSettings s) {
    return new LeaveSettingsResponse(
        s.getFyStartMonth(), s.getFyStartDay(), s.getPeriodStartDay(), s.getPeriodEndDay(),
        s.getSaturdayPolicy(), s.getMinWorkPercent(), s.isRequirePolicyApproval());
  }
}
