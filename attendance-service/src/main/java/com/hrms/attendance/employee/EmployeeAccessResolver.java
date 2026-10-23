package com.hrms.attendance.employee;

import com.hrms.attendance.department.DepartmentService;
import com.hrms.common.security.EmployeeScope;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Dựng {@link EmployeeAccess} từ (username, scope) cho attendance — giải quyết hồ sơ người gọi
 * theo {@code username = employeeCode} và phòng quản lý (DEPT_LEAD) một lần, rồi tái dùng.
 */
@Component
@RequiredArgsConstructor
public class EmployeeAccessResolver {

  private final EmployeeRepository employeeRepository;
  private final DepartmentService departmentService;

  /** ALL → toàn bộ; ngược lại resolve self theo username — fail-closed khi không khớp hồ sơ. */
  public EmployeeAccess resolve(String username, EmployeeScope scope) {
    if (scope == EmployeeScope.ALL) {
      return EmployeeAccess.all();
    }
    Employee self =
        (username == null || username.isBlank())
            ? null
            : employeeRepository.findByEmployeeCode(username).orElse(null);
    if (self == null) {
      return EmployeeAccess.none();
    }
    Set<String> deptIds =
        scope == EmployeeScope.DEPARTMENT
            ? departmentService.managedSubtreeIds(self.getEmployeeId())
            : null;
    return EmployeeAccess.of(self.getEmployeeId(), deptIds);
  }
}
