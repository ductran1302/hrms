package com.hrms.attendance.leave;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveEmployeeNoteRepository extends JpaRepository<LeaveEmployeeNote, UUID> {

  Optional<LeaveEmployeeNote> findByEmployeeIdAndYear(String employeeId, int year);

  @Modifying
  @Query("update LeaveEmployeeNote n set n.employeeId = :newId where n.employeeId = :oldId")
  int rekeyEmployee(@Param("oldId") String oldId, @Param("newId") String newId);
}
