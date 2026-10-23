package com.hrms.identity.employee;

import com.hrms.common.security.EmployeeScope;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Người đang gọi API nhân viên — dùng cho scoping và mask PII:
 *
 * <ul>
 *   <li>{@code scope = ALL} (ADMIN/GLOBAL_LEAD): xem toàn bộ nhân viên, dữ liệu đầy đủ.</li>
 *   <li>{@code scope = DEPARTMENT} (DEPT_LEAD): xem nhân viên trong phạm vi phòng ban
 *       (gồm nhánh con) + trực thuộc + chính mình, PII đầy đủ.</li>
 *   <li>{@code scope = SELF} (MANAGER/EMPLOYEE): chỉ nhân viên trực thuộc + chính mình,
 *       PII nhạy cảm của người khác bị mask; ngoài phạm vi → 403.</li>
 * </ul>
 *
 * @param username {@code preferred_username} trong JWT (= employeeCode khi auto-provision)
 * @param email    {@code email} claim trong JWT (fallback khớp hồ sơ khi username không phải mã NV)
 * @param scope    phạm vi dữ liệu suy từ realm role
 */
public record EmployeeViewer(String username, String email, EmployeeScope scope) {

  /** Dựng viewer từ JWT + Authentication (username/email claim, scope suy từ authorities). */
  public static EmployeeViewer of(Jwt jwt, Authentication authentication) {
    return new EmployeeViewer(
        jwt.getClaimAsString("preferred_username"),
        jwt.getClaimAsString("email"),
        EmployeeScope.fromAuthorities(authentication.getAuthorities()));
  }

  /** true nếu xem toàn bộ, không giới hạn phạm vi và không mask. */
  public boolean unrestricted() {
    return scope == EmployeeScope.ALL;
  }

  /** true nếu PII của người khác bị mask (chỉ MANAGER/EMPLOYEE — lead "đọc đủ" trong scope). */
  public boolean maskOthers() {
    return scope == EmployeeScope.SELF;
  }
}
