package com.hrms.identity.employee;

import com.hrms.common.security.Roles;
import com.hrms.identity.department.DepartmentRepository;
import com.hrms.identity.department.DepartmentStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suy ra realm role mặc định khi tạo tài khoản mới (chỉ lúc tạo user — user cũ
 * không tự đổi role khi tạo nhân viên/hợp đồng; sửa role tay ở màn Cài đặt thắng).
 *
 * <p>Thứ tự ưu tiên:
 * <ol>
 *   <li>Quản lý phòng cấp root (parent = null) → {@code GLOBAL_LEAD} (Ban).</li>
 *   <li>Quản lý bất kỳ phòng đang ACTIVE → {@code DEPT_LEAD}.</li>
 *   <li>Có nhân viên trực thuộc còn làm việc → {@code MANAGER}.</li>
 *   <li>Còn lại → {@code EMPLOYEE}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAssignmentService {

  private final EmployeeRepository employeeRepository;
  private final DepartmentRepository departmentRepository;

  /**
   * Role mặc định cho tài khoản của nhân viên {@code employeeId} — fail-closed về
   * EMPLOYEE nếu không xác định được (khi employee chưa có manager → EMPLOYEE).
   */
  @Transactional(readOnly = true)
  public String resolveDefaultRole(UUID employeeId) {
    Employee employee = employeeRepository.findById(employeeId)
        .orElseThrow(() -> new IllegalStateException(
            "Không tìm thấy nhân viên " + employeeId + " để suy role"));
    UUID managerId = employee.getManagerId();
    if (managerId == null) {
      return Roles.EMPLOYEE;
    }
    if (departmentRepository.existsByManager_IdAndParentIsNullAndStatus(
        managerId, DepartmentStatus.ACTIVE)) {
      log.info("Auto-assign GLOBAL_LEAD cho '{}': quản lý phòng cấp root", employee.getEmployeeCode());
      return Roles.GLOBAL_LEAD;
    }
    if (departmentRepository.existsByManager_IdAndStatus(managerId, DepartmentStatus.ACTIVE)) {
      log.info("Auto-assign DEPT_LEAD cho '{}': quản lý phòng ban ACTIVE", employee.getEmployeeCode());
      return Roles.DEPT_LEAD;
    }
    if (employeeRepository.existsByManagerIdAndStatusNot(employeeId, EmployeeStatus.TERMINATED)) {
      log.info("Auto-assign MANAGER cho '{}': có nhân viên trực thuộc", employee.getEmployeeCode());
      return Roles.MANAGER;
    }
    return Roles.EMPLOYEE;
  }
}
