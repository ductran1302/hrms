package com.hrms.attendance.ot;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository cho item chi tiết từng ngày OT. */
public interface OTRequestItemRepository extends JpaRepository<OTRequestItem, UUID> {
  List<OTRequestItem> findByRequestId(UUID requestId);

  List<OTRequestItem> findByRequestIdIn(Collection<UUID> requestIds);

  void deleteByRequestId(UUID requestId);
}
