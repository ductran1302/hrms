package com.hrms.identity.employee;

/**
 * Người đang gọi API nhân viên — dùng cho scoping và mask PII (P1 bảo mật):
 *
 * <ul>
 *   <li>{@code hrAdmin = true} (HR_ADMIN): xem toàn bộ nhân viên, dữ liệu đầy đủ.</li>
 *   <li>Các role khác (MANAGER): chỉ xem chính mình + nhân viên trực thuộc
 *       ({@code managerId = mình}), PII nhạy cảm bị mask; ngoài phạm vi → 403.</li>
 * </ul>
 *
 * @param username {@code preferred_username} trong JWT (= employeeCode khi auto-provision)
 * @param email    {@code email} claim trong JWT (fallback khớp hồ sơ khi username không phải mã NV)
 * @param hrAdmin  true nếu caller có role {@code HR_ADMIN}
 */
public record EmployeeViewer(String username, String email, boolean hrAdmin) {}
