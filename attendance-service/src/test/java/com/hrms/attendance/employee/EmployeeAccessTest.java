package com.hrms.attendance.employee;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class EmployeeAccessTest {

  private Employee employee(String id, String managerId, String deptId) {
    Employee e = new Employee();
    e.setEmployeeId(id);
    e.setManagerId(managerId);
    e.setDepartmentId(deptId);
    return e;
  }

  @Test
  void all_allowsAnyTarget() {
    EmployeeAccess access = EmployeeAccess.all();
    assertTrue(access.unrestricted());
    assertTrue(access.canAccess(employee("e-9", null, null)));
  }

  @Test
  void none_deniesEverything() {
    EmployeeAccess access = EmployeeAccess.none();
    assertFalse(access.canAccess(employee("e-1", null, null)));
    assertThrows(AccessDeniedException.class,
        () -> access.assertCanAccess(employee("e-1", null, null)));
  }

  @Test
  void self_allowsSelfAndDirectReportsOnly() {
    EmployeeAccess access = EmployeeAccess.of("m-1", null);
    assertTrue(access.canAccess(employee("m-1", null, "d-1")));
    assertTrue(access.canAccess(employee("e-2", "m-1", "d-9")));
    assertFalse(access.canAccess(employee("e-3", "m-2", "d-9")));
  }

  @Test
  void department_allowsManagedDeptSubtreeMembers() {
    EmployeeAccess access = EmployeeAccess.of("lead-1", Set.of("d-1", "d-2"));
    assertTrue(access.canAccess(employee("e-4", null, "d-2")));
    assertFalse(access.canAccess(employee("e-5", null, "d-9")));
  }

  @Test
  void department_nullDeptTarget_isDenied() {
    EmployeeAccess access = EmployeeAccess.of("lead-1", Set.of("d-1"));
    assertFalse(access.canAccess(employee("e-6", null, null)));
  }
}
