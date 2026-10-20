package com.hrms.attendance.shift;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {

  boolean existsByWorkShiftId(UUID workShiftId);

  List<ShiftAssignment> findAllByOrderByEffectiveFromDesc();

  List<ShiftAssignment> findByEmployeeIdOrderByEffectiveFromDesc(String employeeId);

  Optional<ShiftAssignment> findTopByEmployeeIdOrderByEffectiveFromDesc(String employeeId);

  /** B?n g?n hi?u l?c t?i 1 ng?y: effective_from <= date, m?i nh?t. */
  Optional<ShiftAssignment>
      findTopByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
          String employeeId, LocalDate date);

  /** D?i employee_id khi identity t?o l?i nh?n vi?n v?i id m?i (c?ng employeeCode). */
  @Modifying
  @Query("update ShiftAssignment a set a.employeeId = :newId where a.employeeId = :oldId")
  int rekeyEmployee(@Param("oldId") String oldId, @Param("newId") String newId);
}
