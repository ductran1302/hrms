package com.hrms.attendance.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Mirror ngày lễ trong attendance_db — đọc từ Kafka event holiday.changed của identity
 * (không join DB chéo). PK là holidayId nhận từ event (không sinh mới).
 */
@Entity
@Table(name = "holidays")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Holiday {

  @Id
  @Column(name = "holiday_id", length = 64)
  private String holidayId;

  @Column(name = "calendar_id", length = 64, nullable = false)
  private String calendarId;

  @Column(name = "calendar_code", length = 30, nullable = false)
  private String calendarCode;

  @Column(name = "name", length = 200, nullable = false)
  private String name;

  @Column(name = "date_from", nullable = false)
  private LocalDate dateFrom;

  @Column(name = "date_to", nullable = false)
  private LocalDate dateTo;

  @Column(name = "year", nullable = false)
  private int year;

  @Column(name = "holiday_type", length = 20, nullable = false)
  private String holidayType;

  @Column(name = "is_half_day", nullable = false)
  private boolean halfDay;

  @Column(name = "note", length = 300)
  private String note;

  @CreatedDate
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
