package com.hrms.attendance.holiday;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayRepository extends JpaRepository<Holiday, String> {

  List<Holiday> findByCalendarCodeAndYearOrderByDateFromAsc(String calendarCode, int year);

  @Query("""
      select h from Holiday h
      where h.calendarCode = :calendarCode
        and h.dateFrom <= :date and h.dateTo >= :date
      order by h.dateFrom asc
      """)
  List<Holiday> findByCalendarCodeAndDate(@Param("calendarCode") String calendarCode,
      @Param("date") LocalDate date);

  @Query("""
      select h from Holiday h
      where (:calendarCode is null or h.calendarCode = :calendarCode)
        and (:year is null or h.year = :year)
      order by h.dateFrom asc, h.calendarCode asc
      """)
  List<Holiday> search(@Param("calendarCode") String calendarCode, @Param("year") Integer year);
}
