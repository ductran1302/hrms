package com.hrms.attendance.leave;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeavePositionQuotaRepository extends JpaRepository<LeavePositionQuota, UUID> {

  List<LeavePositionQuota> findByPolicyIdOrderByPositionNameAsc(UUID policyId);

  void deleteByPolicyId(UUID policyId);
}
