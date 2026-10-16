package com.hrms.attendance.leave.dto;

import java.math.BigDecimal;

public record LeaveGrantRequest(String source, BigDecimal days, String reason) {}
