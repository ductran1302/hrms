package com.hrms.identity.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

  boolean existsByEmployee_IdAndStatus(UUID employeeId, ContractStatus status);

  boolean existsByEmployee_IdAndStatusAndIdNot(UUID employeeId, ContractStatus status, UUID id);

  List<Contract> findByEmployee_IdOrderByStartDateDesc(UUID employeeId);

  @Query("""
      select c from Contract c join fetch c.employee
      where (:employeeId is null or c.employee.id = :employeeId)
        and (:status is null or c.status = :status)
      order by c.createdAt desc
      """)
  List<Contract> search(@Param("employeeId") UUID employeeId,
      @Param("status") ContractStatus status);

  List<Contract> findByStatusAndEndDateBefore(ContractStatus status, LocalDate endDate);
}
