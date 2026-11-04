package com.hrms.attendance.ot;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository cho {@link OTConfig} (chỉ duy nhất 1 row ACTIVE). */
public interface OTConfigRepository extends JpaRepository<OTConfig, java.util.UUID> {

  @Query("select c from OTConfig c where c.status = 'ACTIVE'")
  Optional<OTConfig> findActive();

  @Query("select c from OTConfig c where c.status = 'INACTIVE' order by c.createdAt desc")
  java.util.List<OTConfig> findInactiveOrdered();
}