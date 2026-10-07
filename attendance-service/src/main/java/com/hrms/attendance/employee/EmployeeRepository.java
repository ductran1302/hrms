package com.hrms.attendance.employee;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, String> {

  Optional<Employee> findByEmployeeCode(String employeeCode);

  List<Employee> findByStatusOrderByFullNameAsc(String status);
}
