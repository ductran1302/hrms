package com.hrms.attendance.leave;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveEntitlementRepository extends JpaRepository<LeaveEntitlement, UUID> {

  Optional<LeaveEntitlement> findByEmployeeIdAndYearAndSource(
      String employeeId, int year, LeaveSource source);

  long countByYear(int year);

  List<LeaveEntitlement> findByEmployeeIdAndYearOrderBySourceAsc(String employeeId, int year);

  List<LeaveEntitlement> findByEmployeeIdAndYearAndSourceIn(
      String employeeId, int year, List<LeaveSource> sources);

  @Query("""
      select e.employeeId, e.source, coalesce(sum(e.days), 0)
      from LeaveEntitlement e
      where e.year = :year
      group by e.employeeId, e.source
      """)
  List<Object[]> sumDaysByEmployeeAndSource(@Param("year") int year);

  /** D?i employee_id khi identity t?o l?i nh?n vi?n v?i id m?i (c?ng employeeCode). */
  @Modifying
  @Query("update LeaveEntitlement e set e.employeeId = :newId where e.employeeId = :oldId")
  int rekeyEmployee(@Param("oldId") String oldId, @Param("newId") String newId);
}
