package com.hrms.attendance.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkSchedulePolicyRepository extends JpaRepository<WorkSchedulePolicy, UUID> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);

  Optional<WorkSchedulePolicy> findFirstByDefaultPolicyTrue();

  Optional<WorkSchedulePolicy> findFirstByDefaultPolicyTrueAndActiveTrue();

  @Query("select p from WorkSchedulePolicy p order by p.name asc")
  List<WorkSchedulePolicy> findAllOrdered();

  List<WorkSchedulePolicy> findByPolicyGroupIdOrderByEffectiveFromDesc(UUID policyGroupId);

  @Query("select p from WorkSchedulePolicy p where p.defaultPolicy = true and p.active = true "
      + "and (p.effectiveFrom is null or p.effectiveFrom <= :date) "
      + "order by p.effectiveFrom desc nulls last, p.versionNo desc")
  List<WorkSchedulePolicy> findDefaultVersions(@Param("date") LocalDate date);

  @Query("select p from WorkSchedulePolicy p where p.policyGroupId = :groupId "
      + "and (p.effectiveFrom is null or p.effectiveFrom <= :date) "
      + "order by p.effectiveFrom desc nulls last, p.versionNo desc")
  List<WorkSchedulePolicy> findVersionsAtOrBefore(@Param("groupId") UUID groupId,
      @Param("date") LocalDate date);
}
