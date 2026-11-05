package com.hrms.attendance.schedule;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeScheduleAssignmentRepository
    extends JpaRepository<EmployeeScheduleAssignment, UUID> {

  List<EmployeeScheduleAssignment> findByEmployeeIdOrderByEffectiveFromDesc(String employeeId);

  Optional<EmployeeScheduleAssignment> findTopByEmployeeIdOrderByEffectiveFromDesc(
      String employeeId);

  List<EmployeeScheduleAssignment> findByEmployeeIdInOrderByEmployeeIdAscEffectiveFromDesc(
      Collection<String> employeeIds);

  Optional<EmployeeScheduleAssignment>
      findTopByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
          String employeeId, LocalDate date);

  boolean existsByPolicyId(UUID policyId);

  boolean existsByEmployeeIdAndEffectiveFrom(String employeeId, LocalDate effectiveFrom);
}
