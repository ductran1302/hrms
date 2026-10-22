package com.hrms.attendance.leave;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveSettingsRepository extends JpaRepository<LeaveSettings, UUID> {

  /** 1 dòng duy nhất — Optional.empty nếu chưa ai self-heal. */
  Optional<LeaveSettings> findFirstByOrderByIdAsc();
}
