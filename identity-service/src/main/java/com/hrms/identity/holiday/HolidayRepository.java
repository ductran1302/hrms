package com.hrms.identity.holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

  List<Holiday> findByCalendar_IdAndYearOrderByDateFromAsc(UUID calendarId, int year);

  List<Holiday> findByCalendar_CodeAndYearOrderByDateFromAsc(String calendarCode, int year);

  @Query("""
      select h from Holiday h join fetch h.calendar
      where (:code is null or h.calendar.code = :code)
        and (:year is null or h.year = :year)
        and (:qPattern is null or lower(h.name) like :qPattern)
      order by h.year desc, h.dateFrom asc
      """)
  List<Holiday> search(@Param("code") String code, @Param("year") Integer year,
      @Param("qPattern") String qPattern);

  Optional<Holiday> findByIdAndCalendar_Id(UUID id, UUID calendarId);

  long countByCalendar_Id(UUID calendarId);

  boolean existsByCalendar_IdAndNameAndDateFrom(UUID calendarId, String name, LocalDate dateFrom);

  boolean existsByCalendar_IdAndNameAndDateFromAndIdNot(UUID calendarId, String name,
      LocalDate dateFrom, UUID id);

  @Query("""
      select h from Holiday h join fetch h.calendar
      where h.dateFrom <= :today and h.dateTo >= :today
      order by h.calendar.code asc
      """)
  List<Holiday> findByDateBetweenFetchCalendar(@Param("today") LocalDate today);
}
