package com.hrms.attendance.leave.dto;

import com.hrms.attendance.leave.SaturdayPolicy;
import java.math.BigDecimal;

public record LeaveSettingsRequest(
    int fyStartMonth,
    int fyStartDay,
    int periodStartDay,
    int periodEndDay,
    SaturdayPolicy saturdayPolicy,
    BigDecimal minWorkPercent,
    Boolean requirePolicyApproval) {}
