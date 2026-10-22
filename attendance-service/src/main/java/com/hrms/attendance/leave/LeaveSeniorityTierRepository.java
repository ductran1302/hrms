package com.hrms.attendance.leave;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveSeniorityTierRepository extends JpaRepository<LeaveSeniorityTier, UUID> {

  List<LeaveSeniorityTier> findByPolicyIdOrderByMinYearsAsc(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
