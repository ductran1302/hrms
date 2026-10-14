package com.hrms.identity.employee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

  boolean existsByEmployeeCode(String employeeCode);

  Optional<Employee> findByEmployeeCode(String employeeCode);

  boolean existsByEmployeeCodeAndIdNot(String employeeCode, UUID id);

  List<Employee> findAllByOrderByCreatedAtDesc();

  List<Employee> findByStatusOrderByCreatedAtDesc(EmployeeStatus status);

  List<Employee> findByDepartmentIdOrderByCreatedAtAsc(UUID departmentId);

  List<Employee> findByStatusNot(EmployeeStatus status);

  @Query("""
      select e from Employee e
      where (:status is null or e.status = :status)
        and (:departmentId is null or e.departmentId = :departmentId)
        and (:position = ''
             or lower(coalesce(e.position, '')) like lower(concat('%', :position, '%')))
        and (:q = ''
             or lower(e.employeeCode) like lower(concat('%', :q, '%'))
             or lower(e.fullName) like lower(concat('%', :q, '%'))
             or lower(coalesce(e.email, '')) like lower(concat('%', :q, '%')))
      """)
  List<Employee> search(@Param("status") EmployeeStatus status,
      @Param("departmentId") UUID departmentId,
      @Param("position") String position,
      @Param("q") String q,
      Pageable pageable);

  @Query("""
      select count(e) from Employee e
      where (:status is null or e.status = :status)
        and (:departmentId is null or e.departmentId = :departmentId)
        and (:position = ''
             or lower(coalesce(e.position, '')) like lower(concat('%', :position, '%')))
        and (:q = ''
             or lower(e.employeeCode) like lower(concat('%', :q, '%'))
             or lower(e.fullName) like lower(concat('%', :q, '%'))
             or lower(coalesce(e.email, '')) like lower(concat('%', :q, '%')))
      """)
  long countSearch(@Param("status") EmployeeStatus status,
      @Param("departmentId") UUID departmentId,
      @Param("position") String position,
      @Param("q") String q);
}
