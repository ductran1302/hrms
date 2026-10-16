package com.hrms.attendance.leave;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {

  Optional<LeavePolicy> findByYear(int year);
}
