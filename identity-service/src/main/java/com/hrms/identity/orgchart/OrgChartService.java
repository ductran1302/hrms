package com.hrms.identity.orgchart;

import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.employee.EmployeeStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sơ đồ tổ chức — cây theo {@code manager_id} (mô hình SuccessFactors Org Chart). */
@Service
@RequiredArgsConstructor
public class OrgChartService {

  private final EmployeeRepository employeeRepository;

  @Transactional(readOnly = true)
  public List<OrgChartNode> tree() {
    List<Employee> employees = employeeRepository.findByStatusNot(EmployeeStatus.TERMINATED);
    Map<UUID, List<Employee>> childrenByManager = new HashMap<>();
    Set<UUID> ids = new HashSet<>();
    for (Employee employee : employees) {
      ids.add(employee.getId());
      if (employee.getManagerId() != null) {
        childrenByManager.computeIfAbsent(employee.getManagerId(), k -> new ArrayList<>())
            .add(employee);
      }
    }
    Set<UUID> visited = new HashSet<>();
    List<OrgChartNode> roots = new ArrayList<>();
    for (Employee employee : employees) {
      // root = không có manager hoặc manager không còn trong danh sách (đã nghỉ/ngoài hệ thống)
      if (employee.getManagerId() == null || !ids.contains(employee.getManagerId())) {
        roots.add(buildNode(employee, childrenByManager, visited));
      }
    }
    roots.sort((a, b) -> a.fullName().compareToIgnoreCase(b.fullName()));
    return roots;
  }

  private OrgChartNode buildNode(Employee employee, Map<UUID, List<Employee>> childrenByManager,
      Set<UUID> visited) {
    visited.add(employee.getId());
    List<OrgChartNode> children = new ArrayList<>();
    for (Employee child : childrenByManager.getOrDefault(employee.getId(), List.of())) {
      if (visited.add(child.getId())) {
        children.add(buildNode(child, childrenByManager, visited));
      }
    }
    children.sort((a, b) -> a.fullName().compareToIgnoreCase(b.fullName()));
    return new OrgChartNode(
        employee.getId(),
        employee.getEmployeeCode(),
        employee.getFullName(),
        employee.getPosition(),
        employee.getDepartmentName(),
        employee.getStatus().name(),
        employee.getPhotoUrl(),
        children);
  }
}
