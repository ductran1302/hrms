package com.hrms.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

  /** Ghi {@code @Transactional} ngay tại repository để mỗi lần update là 1 transaction ngắn. */
  @Transactional
  @Modifying
  @Query("""
      update OutboxEvent e
      set e.status = :status, e.publishedAt = :publishedAt, e.lastError = null
      where e.id = :id
      """)
  int markPublished(@Param("id") UUID id, @Param("status") OutboxStatus status,
      @Param("publishedAt") Instant publishedAt);

  @Transactional
  @Modifying
  @Query("""
      update OutboxEvent e
      set e.attempts = e.attempts + 1, e.lastError = :error
      where e.id = :id
      """)
  int recordFailure(@Param("id") UUID id, @Param("error") String error);

  @Transactional
  @Modifying
  @Query("""
      update OutboxEvent e
      set e.status = :status, e.attempts = e.attempts + 1, e.lastError = :error
      where e.id = :id
      """)
  int markDead(@Param("id") UUID id, @Param("status") OutboxStatus status,
      @Param("error") String error);
}
