package com.hrms.identity.keycloak;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
