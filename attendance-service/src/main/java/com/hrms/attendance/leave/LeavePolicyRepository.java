package com.hrms.attendance.leave;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {

  /** Deprecated: giờ 1 năm nhiều version — dùng findByYearOrderByVersionNoDesc. */
  Optional<LeavePolicy> findByYearAndVersionNo(int year, int versionNo);

  List<LeavePolicy> findByYearOrderByVersionNoDesc(int year);

  Optional<LeavePolicy> findFirstByYearAndStatus(int year, PolicyStatus status);

  List<LeavePolicy> findByYearAndStatusOrderByVersionNoDesc(int year, PolicyStatus status);

  /** Lịch sử hiệu lực (từng/đang áp dụng) — dùng resolve version theo ngày (per-period). */
  List<LeavePolicy> findByYearAndStatusInOrderByEffectiveFromAsc(
      int year, java.util.Collection<PolicyStatus> statuses);

  List<LeavePolicy> findAllByOrderByYearDescVersionNoDesc();

  @Query("select max(p.versionNo) from LeavePolicy p where p.year = :year")
  Optional<Integer> findMaxVersionNo(@Param("year") int year);

  /** Version hiệu lực tại 1 ngày trong năm (ACTIVE, effective_from ≤ date ≤ effective_to). */
  @Query("""
      select p from LeavePolicy p
      where p.year = :year and p.status = com.hrms.attendance.leave.PolicyStatus.ACTIVE
        and p.effectiveFrom <= :date and p.effectiveTo >= :date
      order by p.versionNo desc
      """)
  List<LeavePolicy> findActiveAt(@Param("year") int year, @Param("date") java.time.LocalDate date);
}
