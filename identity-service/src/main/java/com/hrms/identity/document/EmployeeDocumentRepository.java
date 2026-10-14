package com.hrms.identity.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {

  List<EmployeeDocument> findByEmployeeIdOrderByUploadedAtDesc(UUID employeeId);

  List<EmployeeDocument> findByEmployeeIdAndContractIdOrderByUploadedAtDesc(UUID employeeId,
      UUID contractId);

  Optional<EmployeeDocument> findByIdAndEmployeeId(UUID id, UUID employeeId);
}
