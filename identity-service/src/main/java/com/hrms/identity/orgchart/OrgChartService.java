package com.hrms.identity.orgchart;

import com.hrms.identity.department.DepartmentRepository;
import com.hrms.identity.department.DepartmentStatus;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.employee.EmployeeStatus;
import com.hrms.identity.history.EmployeeJobHistoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sơ đồ tổ chức — cây theo {@code manager_id} (mô hình SuccessFactors Org Chart). */
@Service
@RequiredArgsConstructor
public class OrgChartService {

  /** Cửa sổ so sánh cho delta thống kê đầu trang. */
  private static final Duration STAT_WINDOW = Duration.ofDays(30);

  private final EmployeeRepository employeeRepository;
  private final DepartmentRepository departmentRepository;
  private final EmployeeJobHistoryRepository jobHistoryRepository;

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

  /** Thống kê đầu trang: tổng nhân sự / phòng ban / quản lý / đang làm (+ delta 30 ngày). */
  @Transactional(readOnly = true)
  public OrgChartStats stats() {
    List<Employee> employees = employeeRepository.findByStatusNot(EmployeeStatus.TERMINATED);
    long totalEmployees = employees.size();
    long activeEmployees = employees.stream()
        .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE)
        .count();

    // Quản lý = nhân viên chưa nghỉ mà ít nhất một nhân viên khác trỏ manager_id vào mình
    Set<UUID> activeIds = employees.stream().map(Employee::getId).collect(Collectors.toSet());
    Set<UUID> managerIds = employees.stream()
        .map(Employee::getManagerId)
        .filter(Objects::nonNull)
        .filter(activeIds::contains)
        .collect(Collectors.toSet());

    long totalDepartments = departmentRepository.countByStatus(DepartmentStatus.ACTIVE);
    double activePercent = totalEmployees == 0
        ? 0.0
        : Math.round(activeEmployees * 1000.0 / totalEmployees) / 10.0;

    Instant cutoff = Instant.now().minus(STAT_WINDOW);
    long employeeDelta = employeeRepository.countByCreatedAtAfter(cutoff)
        - jobHistoryRepository.countChangeSince("status", EmployeeStatus.TERMINATED.name(),
            cutoff);
    long departmentDelta = departmentRepository.countByCreatedAtAfter(cutoff);
    long managerDelta = managerDelta(managerIds, employees, cutoff);

    return new OrgChartStats(totalEmployees, totalDepartments, managerIds.size(),
        activeEmployees, activePercent, employeeDelta, departmentDelta, managerDelta);
  }

  /** Delta quản lý (xấp xỉ): quản lý tạo trong kỳ hoặc nhận thêm cấp dưới trong kỳ. */
  private long managerDelta(Set<UUID> managerIds, List<Employee> employees, Instant cutoff) {
    Set<UUID> createdInPeriod = employees.stream()
        .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(cutoff))
        .map(Employee::getId)
        .collect(Collectors.toSet());
    Set<UUID> gainedReportInPeriod = jobHistoryRepository.findNewManagerIdsSince(cutoff).stream()
        .map(OrgChartService::parseUuid)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    return managerIds.stream()
        .filter(id -> createdInPeriod.contains(id) || gainedReportInPeriod.contains(id))
        .count();
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (RuntimeException ex) {
      return null;
    }
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
        employee.getOffice(),
        employee.getStatus().name(),
        employee.getPhotoUrl(),
        children);
  }
}
