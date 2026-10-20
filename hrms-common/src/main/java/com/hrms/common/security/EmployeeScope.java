package com.hrms.common.security;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

/**
 * Phạm vi dữ liệu nhân viên của người gọi — suy ra từ realm role trong JWT:
 *
 * <ul>
 *   <li>{@code ALL} (ADMIN/GLOBAL_LEAD): toàn bộ nhân viên, PII đầy đủ.</li>
 *   <li>{@code DEPARTMENT} (DEPT_LEAD): nhân viên các phòng mình quản lý (gồm nhánh con)
 *       + trực thuộc + self, PII đầy đủ.</li>
 *   <li>{@code SELF} (MANAGER/EMPLOYEE): trực thuộc + self; PII của người khác bị mask.</li>
 * </ul>
 */
public enum EmployeeScope {
  ALL,
  DEPARTMENT,
  SELF;

  /** Suy ra scope từ authorities ({@code ROLE_<name>}) — không rõ role → {@code SELF} (fail-closed). */
  public static EmployeeScope fromAuthorities(
      Collection<? extends GrantedAuthority> authorities) {
    Set<String> roles = authorities.stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
    if (roles.contains("ROLE_" + Roles.ADMIN) || roles.contains("ROLE_" + Roles.GLOBAL_LEAD)) {
      return ALL;
    }
    if (roles.contains("ROLE_" + Roles.DEPT_LEAD)) {
      return DEPARTMENT;
    }
    return SELF;
  }
}
