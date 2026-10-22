package com.hrms.attendance.leave;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveWorkDayRepository extends JpaRepository<LeaveWorkDay, UUID> {

  Optional<LeaveWorkDay> findByEmployeeIdAndPeriodStart(String employeeId, LocalDate periodStart);

  List<LeaveWorkDay> findByEmployeeIdAndPeriodStartGreaterThanEqualAndPeriodStartLessThanEqualOrderByPeriodStartAsc(
      String employeeId, LocalDate from, LocalDate to);

  @Modifying
  @Query("update LeaveWorkDay w set w.employeeId = :newId where w.employeeId = :oldId")
  int rekeyEmployee(@Param("oldId") String oldId, @Param("newId") String newId);
}
