package com.hrms.attendance.shift;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkShiftRepository extends JpaRepository<WorkShift, UUID> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);

  Optional<WorkShift> findByName(String name);

  @Query("""
      select s from WorkShift s
      where (:type is null or s.shiftType = :type)
        and (:active is null or s.active = :active)
        and (:q is null or lower(s.name) like lower(concat('%', :q, '%')))
      order by s.createdAt desc
      """)
  List<WorkShift> search(
      @Param("type") ShiftType type, @Param("active") Boolean active, @Param("q") String q);
}
