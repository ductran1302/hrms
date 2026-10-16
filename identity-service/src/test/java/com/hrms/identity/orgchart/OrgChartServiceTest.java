package com.hrms.identity.orgchart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hrms.identity.department.DepartmentRepository;
import com.hrms.identity.department.DepartmentStatus;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.employee.EmployeeStatus;
import com.hrms.identity.history.EmployeeJobHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrgChartServiceTest {

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private DepartmentRepository departmentRepository;

  @Mock
  private EmployeeJobHistoryRepository jobHistoryRepository;

  @InjectMocks
  private OrgChartService service;

  private static final Instant OLD = Instant.now().minusSeconds(100L * 24 * 3600);
  private static final Instant RECENT = Instant.now();

  @Test
  void tree_buildsHierarchyByManagerWithOffice() {
    Employee root = employee("NV001", "Trần Đình Khoa", null, EmployeeStatus.ACTIVE, OLD);
    Employee lead = employee("NV002", "Lê Thu Hà", root.getId(), EmployeeStatus.ACTIVE, OLD);
    lead.setOffice("Trụ sở Hà Nội");
    Employee member = employee("NV003", "Đỗ Thanh Mai", lead.getId(), EmployeeStatus.ACTIVE,
        OLD);
    when(employeeRepository.findByStatusNot(EmployeeStatus.TERMINATED))
        .thenReturn(List.of(root, lead, member));

    List<OrgChartNode> roots = service.tree();

    assertEquals(1, roots.size());
    OrgChartNode rootNode = roots.get(0);
    assertEquals(root.getId(), rootNode.id());
    assertEquals(1, rootNode.children().size());

    OrgChartNode leadNode = rootNode.children().get(0);
    assertEquals(lead.getId(), leadNode.id());
    assertEquals("Trụ sở Hà Nội", leadNode.office());
    assertEquals(1, leadNode.children().size());
    assertEquals(member.getId(), leadNode.children().get(0).id());
  }

  @Test
  void tree_managerAlreadyTerminated_nodeBecomesRoot() {
    Employee orphan = employee("NV010", "Mồ côi quản lý", UUID.randomUUID(),
        EmployeeStatus.ACTIVE, OLD);
    when(employeeRepository.findByStatusNot(EmployeeStatus.TERMINATED))
        .thenReturn(List.of(orphan));

    List<OrgChartNode> roots = service.tree();

    assertEquals(1, roots.size());
    assertEquals(orphan.getId(), roots.get(0).id());
    assertTrue(roots.get(0).children().isEmpty());
  }

  @Test
  void stats_countsAndDeltas() {
    Employee root = employee("NV001", "Giám đốc", null, EmployeeStatus.ACTIVE, OLD);
    Employee lead1 = employee("NV002", "Trưởng nhóm 1", root.getId(), EmployeeStatus.ACTIVE,
        OLD);
    Employee lead2 = employee("NV003", "Trưởng nhóm 2", root.getId(), EmployeeStatus.PROBATION,
        OLD);
    Employee fresh = employee("NV004", "Tuyển mới", lead1.getId(), EmployeeStatus.ACTIVE, RECENT);
    when(employeeRepository.findByStatusNot(EmployeeStatus.TERMINATED))
        .thenReturn(List.of(root, lead1, lead2, fresh));
    when(departmentRepository.countByStatus(DepartmentStatus.ACTIVE)).thenReturn(12L);
    when(employeeRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(1L);
    when(jobHistoryRepository.countChangeSince(any(), any(), any(Instant.class)))
        .thenReturn(0L);
    when(departmentRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(1L);
    when(jobHistoryRepository.findNewManagerIdsSince(any(Instant.class))).thenReturn(List.of());

    OrgChartStats stats = service.stats();

    assertEquals(4, stats.totalEmployees());
    assertEquals(12, stats.totalDepartments());
    // quản lý = người có cấp dưới chưa nghỉ: root + lead1 (lead2 không có con)
    assertEquals(2, stats.totalManagers());
    assertEquals(3, stats.activeEmployees());
    assertEquals(75.0, stats.activePercent());
    assertEquals(1, stats.employeeDelta());
    assertEquals(1, stats.departmentDelta());
    // root/lead1 tạo trước kỳ → không tính "quản lý mới"; fresh không phải quản lý
    assertEquals(0, stats.managerDelta());
  }

  @Test
  void stats_noEmployees_returnsZeroPercent() {
    when(employeeRepository.findByStatusNot(EmployeeStatus.TERMINATED)).thenReturn(List.of());
    when(departmentRepository.countByStatus(DepartmentStatus.ACTIVE)).thenReturn(0L);
    when(employeeRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(0L);
    when(jobHistoryRepository.countChangeSince(any(), any(), any(Instant.class)))
        .thenReturn(0L);
    when(departmentRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(0L);
    when(jobHistoryRepository.findNewManagerIdsSince(any(Instant.class))).thenReturn(List.of());

    OrgChartStats stats = service.stats();

    assertEquals(0, stats.totalEmployees());
    assertEquals(0.0, stats.activePercent());
    assertEquals(0, stats.totalManagers());
  }

  private static Employee employee(String code, String fullName, UUID managerId,
      EmployeeStatus status, Instant createdAt) {
    Employee employee = new Employee();
    employee.setId(UUID.randomUUID());
    employee.setEmployeeCode(code);
    employee.setFullName(fullName);
    employee.setManagerId(managerId);
    employee.setStatus(status);
    employee.setCreatedAt(createdAt);
    return employee;
  }
}
