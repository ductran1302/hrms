package com.hrms.identity.department;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

  boolean existsByCode(String code);

  boolean existsByCodeAndIdNot(String code, UUID id);

  Optional<Department> findByCode(String code);

  // left join fetch: phòng chưa có quản lý (cây tổ chức mới seed) vẫn phải xuất hiện trong tree
  @Query("select d from Department d left join fetch d.manager where d.status = com.hrms.identity.department.DepartmentStatus.ACTIVE order by d.createdAt asc")
  List<Department> findAllActiveWithManager();

  long countByParent_Id(UUID parentId);

  long countByStatus(DepartmentStatus status);

  long countByCreatedAtAfter(java.time.Instant cutoff);

  @Query("select count(e) from Employee e where e.departmentId = :departmentId and e.status <> com.hrms.identity.employee.EmployeeStatus.TERMINATED")
  long countActiveEmployees(UUID departmentId);

  /** Đếm nhân viên chưa nghỉ theo từng phòng ban bằng 1 query cho cả trang. */
  @Query("""
      select e.departmentId as departmentId, count(e) as activeCount
      from Employee e
      where e.departmentId in :departmentIds
        and e.status <> com.hrms.identity.employee.EmployeeStatus.TERMINATED
      group by e.departmentId
      """)
  List<ActiveEmployeeCount> countActiveEmployeesByDepartmentIds(
      @Param("departmentIds") Collection<UUID> departmentIds);

  interface ActiveEmployeeCount {
    UUID getDepartmentId();

    long getActiveCount();
  }

  /** Id các phòng đang ACTIVE mà employee là trưởng phòng (scope DEPT_LEAD + auto-assign role). */
  java.util.List<UUID> findIdsByManager_IdAndStatus(UUID managerId, DepartmentStatus status);

  /** true nếu employee là trưởng phòng cấp root (parent = null) — auto-assign GLOBAL_LEAD. */
  boolean existsByManager_IdAndParentIsNullAndStatus(UUID managerId, DepartmentStatus status);

  /** true nếu employee là trưởng phòng bất kỳ đang ACTIVE — auto-assign DEPT_LEAD. */
  boolean existsByManager_IdAndStatus(UUID managerId, DepartmentStatus status);
}
