package com.hrms.attendance.employee;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, String> {

  Optional<Employee> findByEmployeeCode(String employeeCode);

  List<Employee> findByStatusOrderByFullNameAsc(String status);

  List<Employee> findByStatusNotOrderByFullNameAsc(String status);

  /** Trực thuộc (reports) của 1 manager — scope đọc OT/quota. */
  @Query("select e.employeeId from Employee e where e.managerId = :managerId")
  List<String> findEmployeeIdsByManagerId(@Param("managerId") String managerId);

  /** Nhân viên thuộc các phòng ban trong subtree (scope DEPT_LEAD). */
  @Query("select e.employeeId from Employee e where e.departmentId in :departmentIds")
  List<String> findEmployeeIdsByDepartmentIdIn(@Param("departmentIds") Collection<String> departmentIds);

  List<Employee> findByEmployeeCodeIn(Collection<String> employeeCodes);
}
