package com.hrms.identity.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeJobHistoryRepository extends JpaRepository<EmployeeJobHistory, UUID> {

  List<EmployeeJobHistory> findByEmployee_IdOrderByEffectiveDateDesc(UUID employeeId);

  /** Số lần đổi một giá trị job info (vd. status → TERMINATED) từ {@code cutoff}. */
  @Query("""
      select count(h) from EmployeeJobHistory h
      where h.field = :field and h.newValue = :newValue and h.effectiveDate >= :cutoff
      """)
  long countChangeSince(@Param("field") String field, @Param("newValue") String newValue,
      @Param("cutoff") Instant cutoff);

  /** Các employeeId được gán làm quản lý (field = manager) kể từ {@code cutoff}. */
  @Query("""
      select distinct h.newValue from EmployeeJobHistory h
      where h.field = 'manager' and h.newValue is not null and h.effectiveDate >= :cutoff
      """)
  List<String> findNewManagerIdsSince(@Param("cutoff") Instant cutoff);
}
