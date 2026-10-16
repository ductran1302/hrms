package com.hrms.identity.orgchart;

/**
 * Thống kê đầu trang màn Sơ đồ tổ chức.
 *
 * <p>{@code *Delta} = chênh lệch 30 ngày (hiện tại − 30 ngày trước):
 * <ul>
 *   <li>{@code employeeDelta}: tuyển mới trong kỳ − nghỉ việc trong kỳ
 *       (đối chiếu {@code employee_job_history} field status → TERMINATED)</li>
 *   <li>{@code departmentDelta}: phòng ban tạo mới trong kỳ (bỏ qua ngừng hoạt động — ít thay đổi)</li>
 *   <li>{@code managerDelta}: quản lý "mới" = tồn tại tạo trong kỳ hoặc có cấp dưới mới trong
 *       kỳ (job history field = manager). Xấp xỉ — hệ thống không snapshot lịch sử.</li>
 * </ul>
 */
public record OrgChartStats(
    long totalEmployees,
    long totalDepartments,
    long totalManagers,
    long activeEmployees,
    double activePercent,
    long employeeDelta,
    long departmentDelta,
    long managerDelta) {}
