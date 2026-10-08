package com.hrms.identity.history;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeJobHistoryRepository extends JpaRepository<EmployeeJobHistory, UUID> {

  List<EmployeeJobHistory> findByEmployee_IdOrderByEffectiveDateDesc(UUID employeeId);
}
