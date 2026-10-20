package com.hrms.common.security;

import java.util.Collection;
import java.util.Set;

/**
 * Tên role realm Keycloak — dùng cho {@code @PreAuthorize("hasRole('ADMIN')")} etc.
 *
 * <p>5 role, 2 nhóm:
 * <ul>
 *   <li><b>Role vận hành hệ thống</b> (không thuộc cấp bậc công ty):
 *       {@code ADMIN} (toàn quyền), {@code MANAGER} (đặc quyền thao tác, scope trực thuộc).</li>
 *   <li><b>Cấp bậc thật của công ty</b>: {@code GLOBAL_LEAD} (Ban/toàn công ty),
 *       {@code DEPT_LEAD} (phòng ban, gồm cả nhánh con), {@code EMPLOYEE}.</li>
 * </ul>
 */
public final class Roles {

  public static final String ADMIN = "ADMIN";
  public static final String GLOBAL_LEAD = "GLOBAL_LEAD";
  public static final String DEPT_LEAD = "DEPT_LEAD";
  public static final String MANAGER = "MANAGER";
  public static final String EMPLOYEE = "EMPLOYEE";

  private Roles() {}

  /** Tập role hợp lệ — validate API đổi role ở màn Cài đặt. */
  public static Set<String> all() {
    return Set.of(ADMIN, GLOBAL_LEAD, DEPT_LEAD, MANAGER, EMPLOYEE);
  }

  /**
   * Thứ bậc quyền: {@code EMPLOYEE(0) < MANAGER(1) < DEPT_LEAD(2)
   * < GLOBAL_LEAD(3) < ADMIN(4)}; role lạ = -1.
   */
  public static int level(String role) {
    return switch (role) {
      case EMPLOYEE -> 0;
      case MANAGER -> 1;
      case DEPT_LEAD -> 2;
      case GLOBAL_LEAD -> 3;
      case ADMIN -> 4;
      default -> -1;
    };
  }

  /** Cấp cao nhất trong tập role (rỗng/null → -1) — chặn tự hạ role của chính mình. */
  public static int maxLevel(Collection<String> roles) {
    return roles == null ? -1 : roles.stream().mapToInt(Roles::level).max().orElse(-1);
  }
}
