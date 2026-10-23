package com.hrms.identity.keycloak;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeycloakSyncTaskRepository extends JpaRepository<KeycloakSyncTask, UUID> {

  /** Khóa bi quan — scheduler và afterCommit không xử lý trùng cùng 1 task. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from KeycloakSyncTask t where t.id = :id")
  Optional<KeycloakSyncTask> findByIdForUpdate(@Param("id") UUID id);

  @Query("""
      select t.id from KeycloakSyncTask t
      where t.status = :status and t.nextAttemptAt <= :now
      order by t.nextAttemptAt asc
      """)
  List<UUID> findIdsByStatusAndNextAttemptAtBefore(@Param("status") KeycloakSyncStatus status,
      @Param("now") Instant now, Pageable pageable);

  /** Danh sách task cho màn vận hành (lọc theo status tùy chọn), mới nhất trước. */
  @Query("""
      select t from KeycloakSyncTask t
      where (:status is null or t.status = :status)
      order by t.createdAt desc, t.id desc
      """)
  List<KeycloakSyncTask> search(@Param("status") KeycloakSyncStatus status, Pageable pageable);

  @Query("""
      select count(t) from KeycloakSyncTask t
      where (:status is null or t.status = :status)
      """)
  long countSearch(@Param("status") KeycloakSyncStatus status);

  /** Dọn task DONE cũ (retention) — bulk delete để không load entity. */
  @Modifying
  @Query("delete from KeycloakSyncTask t where t.status = :status and t.updatedAt < :cutoff")
  int deleteByStatusAndUpdatedAtBefore(@Param("status") KeycloakSyncStatus status,
      @Param("cutoff") Instant cutoff);
}
