package com.hrms.identity.holiday;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, UUID> {

  Optional<HolidayCalendar> findByCode(String code);

  boolean existsByCode(String code);

  List<HolidayCalendar> findAllByOrderByCodeAsc();
}
