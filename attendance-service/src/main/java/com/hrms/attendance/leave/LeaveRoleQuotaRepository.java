package com.hrms.attendance.leave;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRoleQuotaRepository extends JpaRepository<LeaveRoleQuota, UUID> {

  List<LeaveRoleQuota> findByPolicyIdOrderByRoleAsc(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
