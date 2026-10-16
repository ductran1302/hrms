package com.hrms.attendance.leave;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveEntitlementRepository extends JpaRepository<LeaveEntitlement, UUID> {

  Optional<LeaveEntitlement> findByEmployeeIdAndYearAndSource(
      String employeeId, int year, LeaveSource source);

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
}
