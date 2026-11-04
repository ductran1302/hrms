package com.hrms.attendance.ot;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository cho lịch sử thay đổi cấu hình OT. */
public interface OTConfigHistoryRepository extends JpaRepository<OTConfigHistory, java.util.UUID> {

  @Query("select h from OTConfigHistory h order by h.performedAt desc")
  Page<OTConfigHistory> findAllOrdered(Pageable pageable);
}