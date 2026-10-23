package com.hrms.attendance.employee;

import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

/**
 * Phạm vi nhân sự của người gọi trong attendance (projection {@code employees}) —
 * dùng chung cho mọi endpoint đọc có tham chiếu nhân sự (leave quota, ghi chú,
 * ngày công, gán ca).
 *
 * <ul>
 *   <li>{@code ALL} (ADMIN/GLOBAL_LEAD): toàn bộ nhân sự.</li>
 *   <li>{@code DEPARTMENT} (DEPT_LEAD): phòng mình quản lý (gồm nhánh con) ∪ trực thuộc ∪ self.</li>
 *   <li>{@code SELF} (MANAGER/EMPLOYEE): trực thuộc ∪ self.</li>
 * </ul>
 *
 * <p>Fail-closed: không khớp hồ sơ nhân sự → không truy cập được ai.
 */
public final class EmployeeAccess {

  private final boolean unrestricted;
  private final String selfId;
  private final Set<String> deptIds;

  private EmployeeAccess(boolean unrestricted, String selfId, Set<String> deptIds) {
    this.unrestricted = unrestricted;
    this.selfId = selfId;
    this.deptIds = deptIds;
  }

  /** ALL — không giới hạn. */
  public static EmployeeAccess all() {
    return new EmployeeAccess(true, null, null);
  }

  /** Fail-closed — không thấy nhân sự nào (không resolve được người gọi). */
  public static EmployeeAccess none() {
    return new EmployeeAccess(false, null, null);
  }

  /**
   * @param selfId  employeeId của người gọi (khớp theo username = employeeCode)
   * @param deptIds phòng quản lý (chỉ scope DEPARTMENT; {@code null} = không có)
   */
  public static EmployeeAccess of(String selfId, Set<String> deptIds) {
    return new EmployeeAccess(false, selfId, deptIds);
  }

  public boolean unrestricted() {
    return unrestricted;
  }

  /** true nếu người gọi được xem {@code target}. */
  public boolean canAccess(Employee target) {
    if (unrestricted) {
      return true;
    }
    if (selfId == null) {
      return false;
    }
    if (selfId.equals(target.getEmployeeId())) {
      return true;
    }
    if (selfId.equals(target.getManagerId())) {
      return true; // trực thuộc
    }
    return deptIds != null
        && target.getDepartmentId() != null
        && deptIds.contains(target.getDepartmentId());
  }

  public void assertCanAccess(Employee target) {
    if (!canAccess(target)) {
      throw new AccessDeniedException("Bạn không có quyền xem dữ liệu nhân sự này");
    }
  }
}
