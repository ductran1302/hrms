package com.hrms.attendance.leave.seniority;

import com.hrms.attendance.leave.LeavePolicy;
import com.hrms.attendance.leave.LeaveSeniorityTier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu đầu vào cho 1 lần tính bonus thâm niên.
 *
 * @param hiredAt ngày vào làm của nhân viên
 * @param year năm tài chính đang tính (LINEAR dùng năm dương lịch)
 * @param asOf ngày tính (VN_TIERED: đủ năm tính đến asOf — thường là ngày recalculate)
 * @param policy version chính sách in-force (cấu hình số liệu)
 * @param tiers bậc thâm niên của policy (VN_TIERED)
 */
public record SeniorityContext(
    Instant hiredAt,
    int year,
    LocalDate asOf,
    LeavePolicy policy,
    List<LeaveSeniorityTier> tiers) {}
