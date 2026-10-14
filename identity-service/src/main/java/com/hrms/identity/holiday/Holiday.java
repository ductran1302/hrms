package com.hrms.identity.holiday;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một ngày/nhóm ngày lễ trong năm — year denormalize từ dateFrom để filter nhanh. */
@Entity
@Table(name = "holidays")
@Getter
@Setter
@NoArgsConstructor
public class Holiday extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "calendar_id", nullable = false)
  private HolidayCalendar calendar;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "date_from", nullable = false)
  private LocalDate dateFrom;

  @Column(name = "date_to", nullable = false)
  private LocalDate dateTo;

  @Column(name = "year", nullable = false)
  private int year;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private HolidayType type;

  @Column(name = "is_half_day", nullable = false)
  private boolean halfDay;

  @Column(name = "note", length = 300)
  private String note;

  public UUID getCalendarId() {
    return calendar == null ? null : calendar.getId();
  }

  public String getCalendarCode() {
    return calendar == null ? null : calendar.getCode();
  }
}
