package com.hrms.identity.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {

  Page<EmployeeDocument> findByEmployeeId(UUID employeeId, Pageable pageable);

  Page<EmployeeDocument> findByEmployeeIdAndContractId(UUID employeeId, UUID contractId,
      Pageable pageable);

  Optional<EmployeeDocument> findByIdAndEmployeeId(UUID id, UUID employeeId);
}
