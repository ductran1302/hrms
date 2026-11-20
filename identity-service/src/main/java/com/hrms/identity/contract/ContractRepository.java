package com.hrms.identity.contract;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

  boolean existsByEmployee_IdAndStatus(UUID employeeId, ContractStatus status);

  boolean existsByEmployee_IdAndStatusIn(UUID employeeId, Collection<ContractStatus> statuses);

  boolean existsByEmployee_IdAndStatusInAndIdNot(UUID employeeId,
      Collection<ContractStatus> statuses, UUID id);

  @EntityGraph(attributePaths = "employee")
  Page<Contract> findByEmployee_Id(UUID employeeId, Pageable pageable);

  @EntityGraph(attributePaths = "employee")
  @Query("""
      select c from Contract c
      where (:employeeId is null or c.employee.id = :employeeId)
        and (:status is null or c.status = :status)
        and (:scopeIds is null or c.employee.id in :scopeIds)
      """)
  Page<Contract> search(@Param("employeeId") UUID employeeId,
      @Param("status") ContractStatus status,
      @Param("scopeIds") Collection<UUID> scopeIds,
      Pageable pageable);

  List<Contract> findByStatusAndEndDateBefore(ContractStatus status, LocalDate endDate);

  List<Contract> findByStatusAndEndDateBetween(ContractStatus status, LocalDate from,
      LocalDate to);
}
