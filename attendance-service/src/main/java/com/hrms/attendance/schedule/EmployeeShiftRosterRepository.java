package com.hrms.attendance.schedule;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeShiftRosterRepository
    extends JpaRepository<EmployeeShiftRoster, UUID> {

  List<EmployeeShiftRoster> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
      String employeeId, LocalDate from, LocalDate to);

  List<EmployeeShiftRoster> findByEmployeeIdInAndWorkDateBetween(
      Collection<String> employeeIds, LocalDate from, LocalDate to);

  Optional<EmployeeShiftRoster> findByEmployeeIdAndWorkDate(String employeeId, LocalDate workDate);

  boolean existsByPolicyId(UUID policyId);

  boolean existsByShiftId(UUID shiftId);
}
