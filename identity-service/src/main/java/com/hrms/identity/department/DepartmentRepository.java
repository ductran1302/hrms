package com.hrms.identity.department;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

  boolean existsByCode(String code);

  boolean existsByCodeAndIdNot(String code, UUID id);

  List<Department> findAllByOrderByCreatedAtAsc();

  Optional<Department> findByCode(String code);

  @Query("select d from Department d join fetch d.manager where d.status = com.hrms.identity.department.DepartmentStatus.ACTIVE order by d.createdAt asc")
  List<Department> findAllActiveWithManager();

  long countByParent_Id(UUID parentId);

  @Query("select count(e) from Employee e where e.departmentId = :departmentId and e.status <> com.hrms.identity.employee.EmployeeStatus.TERMINATED")
  long countActiveEmployees(UUID departmentId);
}
