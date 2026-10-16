package com.hrms.attendance.shift;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {

  boolean existsByWorkShiftId(UUID workShiftId);

  List<ShiftAssignment> findAllByOrderByEffectiveFromDesc();

  List<ShiftAssignment> findByEmployeeIdOrderByEffectiveFromDesc(String employeeId);

  Optional<ShiftAssignment> findTopByEmployeeIdOrderByEffectiveFromDesc(String employeeId);

  /** Bản gán hiệu lực tại 1 ngày: effective_from ≤ date, mới nhất. */
  Optional<ShiftAssignment>
      findTopByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
          String employeeId, LocalDate date);
}
