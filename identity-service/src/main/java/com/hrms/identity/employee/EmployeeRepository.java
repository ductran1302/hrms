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

  Optional<Employee> findFirstByEmail(String email);

  boolean existsByEmployeeCodeAndIdNot(String employeeCode, UUID id);

  long countByCalendarId(UUID calendarId);

  List<Employee> findAllByOrderByCreatedAtDesc();

  List<Employee> findByStatusOrderByCreatedAtDesc(EmployeeStatus status);

  List<Employee> findByDepartmentIdOrderByCreatedAtAsc(UUID departmentId);

  List<Employee> findByStatusNot(EmployeeStatus status);

  long countByStatusNot(EmployeeStatus status);

  long countByStatus(EmployeeStatus status);

  long countByCreatedAtAfter(java.time.Instant cutoff);

  /** true nếu có nhân viên khác đang trực thuộc {@code managerId} (còn làm việc) — auto-assign MANAGER. */
  boolean existsByManagerIdAndStatusNot(UUID managerId, EmployeeStatus status);

  /**
   * Tìm kiếm nhân viên có scoping:
   * <ul>
   *   <li>{@code managerScope != null} → nhân viên trực thuộc ({@code managerId = managerScope})
   *       hoặc chính người xem ({@code id = viewerId}).</li>
   *   <li>{@code deptScope != null} (DEPT_LEAD) → thêm nhân viên thuộc các phòng ban
   *       trong danh sách ({@code departmentId in deptScope}) — union với trên.</li>
   *   <li>Cả hai null → không giới hạn (ADMIN/GLOBAL_LEAD).</li>
   * </ul>
   */
  @Query("""
      select e from Employee e
      where (:status is null or e.status = :status)
        and (:departmentId is null or e.departmentId = :departmentId)
        and (:position = ''
             or lower(coalesce(e.position, '')) like lower(concat('%', :position, '%')))
        and (:office = ''
             or lower(coalesce(e.office, '')) like lower(concat('%', :office, '%')))
        and (:q = ''
             or lower(e.employeeCode) like lower(concat('%', :q, '%'))
             or lower(e.fullName) like lower(concat('%', :q, '%'))
             or lower(coalesce(e.email, '')) like lower(concat('%', :q, '%')))
        and (:managerScope is null
             or e.managerId = :managerScope
             or e.id = :viewerId)
        and (:deptScope is null
             or e.departmentId in :deptScope
             or e.managerId = :viewerId
             or e.id = :viewerId)
      """)
  List<Employee> search(@Param("status") EmployeeStatus status,
      @Param("departmentId") UUID departmentId,
      @Param("position") String position,
      @Param("office") String office,
      @Param("q") String q,
      @Param("managerScope") UUID managerScope,
      @Param("deptScope") List<UUID> deptScope,
      @Param("viewerId") UUID viewerId,
      Pageable pageable);

  /** Count song song với {@link #search} — cùng mệnh đề scoping {@code managerScope/deptScope}. */
  @Query("""
      select count(e) from Employee e
      where (:status is null or e.status = :status)
        and (:departmentId is null or e.departmentId = :departmentId)
        and (:position = ''
             or lower(coalesce(e.position, '')) like lower(concat('%', :position, '%')))
        and (:office = ''
             or lower(coalesce(e.office, '')) like lower(concat('%', :office, '%')))
        and (:q = ''
             or lower(e.employeeCode) like lower(concat('%', :q, '%'))
             or lower(e.fullName) like lower(concat('%', :q, '%'))
             or lower(coalesce(e.email, '')) like lower(concat('%', :q, '%')))
        and (:managerScope is null
             or e.managerId = :managerScope
             or e.id = :viewerId)
        and (:deptScope is null
             or e.departmentId in :deptScope
             or e.managerId = :viewerId
             or e.id = :viewerId)
      """)
  long countSearch(@Param("status") EmployeeStatus status,
      @Param("departmentId") UUID departmentId,
      @Param("position") String position,
      @Param("office") String office,
      @Param("q") String q,
      @Param("managerScope") UUID managerScope,
      @Param("deptScope") List<UUID> deptScope,
      @Param("viewerId") UUID viewerId);
}
