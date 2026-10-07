package com.hrms.identity.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

  boolean existsByEmployeeCode(String employeeCode);

  List<Employee> findAllByOrderByCreatedAtDesc();

  List<Employee> findByStatusOrderByCreatedAtDesc(EmployeeStatus status);
}
