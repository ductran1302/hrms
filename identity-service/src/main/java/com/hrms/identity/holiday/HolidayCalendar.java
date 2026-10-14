package com.hrms.identity.holiday;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lịch nghỉ lễ (theo country hoặc company-wide). Code bất biến sau tạo. */
@Entity
@Table(name = "holiday_calendars")
@Getter
@Setter
@NoArgsConstructor
public class HolidayCalendar extends BaseEntity {

  @Column(name = "code", nullable = false, length = 30, unique = true)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  /** ISO 3166-1 alpha-2: VN, JP, US... */
  @Column(name = "country", nullable = false, length = 5)
  private String country;

  @Column(name = "description", length = 300)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private HolidayCalendarStatus status;
}
