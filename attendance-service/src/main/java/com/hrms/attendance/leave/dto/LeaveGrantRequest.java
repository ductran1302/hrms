package com.hrms.attendance.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cấp tay quỹ phép. {@code expiresAt} chỉ có ý nghĩa với BONUS (tùy chỉnh thời hạn — được
 * vượt cuối năm TC); nguồn khác luôn mặc định = cuối năm tài chính (422 nếu truyền).
 */
public record LeaveGrantRequest(String source, BigDecimal days, String reason, LocalDate expiresAt) {}
