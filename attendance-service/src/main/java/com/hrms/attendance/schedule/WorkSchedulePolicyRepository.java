package com.hrms.attendance.schedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkSchedulePolicyRepository extends JpaRepository<WorkSchedulePolicy, UUID> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);

  Optional<WorkSchedulePolicy> findFirstByDefaultPolicyTrue();

  Optional<WorkSchedulePolicy> findFirstByDefaultPolicyTrueAndActiveTrue();

  @Query("select p from WorkSchedulePolicy p order by p.name asc")
  List<WorkSchedulePolicy> findAllOrdered();
}
