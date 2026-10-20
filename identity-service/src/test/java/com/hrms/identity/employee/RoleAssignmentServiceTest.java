package com.hrms.identity.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.security.Roles;
import com.hrms.identity.department.DepartmentRepository;
import com.hrms.identity.department.DepartmentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private DepartmentRepository departmentRepository;

  @InjectMocks
  private RoleAssignmentService service;

  private final UUID employeeId = UUID.randomUUID();
  private final UUID managerId = UUID.randomUUID();

  @Test
  void resolveDefaultRole_noManager_returnsEmployee() {
    Employee employee = new Employee();
    employee.setId(employeeId);
    employee.setEmployeeCode("NV0001");
    when(employeeRepository.findById(employeeId)).thenReturn(java.util.Optional.of(employee));

    assertEquals(Roles.EMPLOYEE, service.resolveDefaultRole(employeeId));
    verify(departmentRepository, never()).existsByManager_IdAndParentIsNullAndStatus(any(), any());
  }

  @Test
  void resolveDefaultRole_managerOfRootDept_returnsGlobalLead() {
    Employee employee = new Employee();
    employee.setId(employeeId);
    employee.setEmployeeCode("NV0001");
    employee.setManagerId(managerId);
    when(employeeRepository.findById(employeeId)).thenReturn(java.util.Optional.of(employee));
    when(departmentRepository.existsByManager_IdAndParentIsNullAndStatus(
        managerId, DepartmentStatus.ACTIVE)).thenReturn(true);

    assertEquals(Roles.GLOBAL_LEAD, service.resolveDefaultRole(employeeId));
  }

  @Test
  void resolveDefaultRole_managerOfSubDept_returnsDeptLead() {
    Employee employee = new Employee();
    employee.setId(employeeId);
    employee.setEmployeeCode("NV0001");
    employee.setManagerId(managerId);
    when(employeeRepository.findById(employeeId)).thenReturn(java.util.Optional.of(employee));
    when(departmentRepository.existsByManager_IdAndParentIsNullAndStatus(
        managerId, DepartmentStatus.ACTIVE)).thenReturn(false);
    when(departmentRepository.existsByManager_IdAndStatus(
        managerId, DepartmentStatus.ACTIVE)).thenReturn(true);

    assertEquals(Roles.DEPT_LEAD, service.resolveDefaultRole(employeeId));
  }

  @Test
  void resolveDefaultRole_hasReports_returnsManager() {
    Employee employee = new Employee();
    employee.setId(employeeId);
    employee.setEmployeeCode("NV0001");
    employee.setManagerId(managerId);
    when(employeeRepository.findById(employeeId)).thenReturn(java.util.Optional.of(employee));
    when(departmentRepository.existsByManager_IdAndParentIsNullAndStatus(
        managerId, DepartmentStatus.ACTIVE)).thenReturn(false);
    when(departmentRepository.existsByManager_IdAndStatus(
        managerId, DepartmentStatus.ACTIVE)).thenReturn(false);
    when(employeeRepository.existsByManagerIdAndStatusNot(
        employeeId, EmployeeStatus.TERMINATED)).thenReturn(true);

    assertEquals(Roles.MANAGER, service.resolveDefaultRole(employeeId));
  }

  @Test
  void resolveDefaultRole_plainEmployee_returnsEmployee() {
    Employee employee = new Employee();
    employee.setId(employeeId);
    employee.setEmployeeCode("NV0001");
    employee.setManagerId(managerId);
    when(employeeRepository.findById(employeeId)).thenReturn(java.util.Optional.of(employee));
    when(departmentRepository.existsByManager_IdAndParentIsNullAndStatus(
        managerId, DepartmentStatus.ACTIVE)).thenReturn(false);
    when(departmentRepository.existsByManager_IdAndStatus(
        managerId, DepartmentStatus.ACTIVE)).thenReturn(false);
    when(employeeRepository.existsByManagerIdAndStatusNot(
        employeeId, EmployeeStatus.TERMINATED)).thenReturn(false);

    assertEquals(Roles.EMPLOYEE, service.resolveDefaultRole(employeeId));
  }
}
