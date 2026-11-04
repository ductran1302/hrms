package com.hrms.attendance.ot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository cho đơn OT master. */
public interface OTRequestRepository extends JpaRepository<OTRequest, UUID> {

  /**
   * List đơn OT theo danh sách employeeId (áp dụng scope) + filter status.
   * employeeIds = null → query tất cả (ADMIN/GLOBAL_LEAD).
   */
  @Query("""
      select r from OTRequest r
      where (:employeeIds is null or r.employeeId in :employeeIds)
        and (:status is null or r.status = :status)
      order by r.createdAt desc
      """)
  Page<OTRequest> findByEmployeeIds(java.util.Collection<String> employeeIds,
                                    OTRequestStatus status, Pageable pageable);

  /** Kiểm tra requestCode đã tồn tại — chống va chạm unique khi sinh mã. */
  boolean existsByRequestCode(String requestCode);

  /** Số đơn đã tạo từ thời điểm given — nối sequence mã theo ngày. */
  long countByCreatedAtAfter(java.time.Instant time);

  /**
   * Tổng giờ OT (approved_hours) của nhân viên tại 1 ngày — gồm các đơn đang có hiệu lực
   * (PENDING/APPROVED/PENDING_CANCEL). Dùng khi TÍNH/CHỐT giờ OT (áp trần), KHÔNG chặn tạo đơn.
   * excludeRequestId = id đơn khi cần bỏ qua chính nó.
   */
  @Query("""
      select coalesce(sum(i.approvedHours), 0)
      from OTRequestItem i
      where i.request.employeeId = :employeeId
        and i.request.status in ('PENDING', 'APPROVED', 'PENDING_CANCEL')
        and i.otDate = :date
        and (:excludeRequestId is null or i.request.id <> :excludeRequestId)
      """)
  BigDecimal sumHoursOnDate(@Param("employeeId") String employeeId,
                            @Param("date") LocalDate date,
                            @Param("excludeRequestId") UUID excludeRequestId);

  /**
   * Tổng giờ OT có hiệu lực trong khoảng ngày (trừ đơn chỉ định) — dùng khi TÍNH/CHỐT
   * giờ OT để áp trần năm, KHÔNG chặn tạo đơn.
   */
  @Query("""
      select coalesce(sum(i.approvedHours), 0)
      from OTRequestItem i
      where i.request.employeeId = :employeeId
        and i.request.status in ('PENDING', 'APPROVED', 'PENDING_CANCEL')
        and i.otDate between :from and :to
        and (:excludeRequestId is null or i.request.id <> :excludeRequestId)
      """)
  BigDecimal sumHoursInPeriod(@Param("employeeId") String employeeId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("excludeRequestId") UUID excludeRequestId);
}
