package com.hrms.attendance.ot;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request chi tiết 1 ngày OT trong danh sách đơn. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OTRequestItemCreateRequest {

  @NotNull
  @FutureOrPresent
  private LocalDateTime startTime;

  @NotNull
  private LocalDateTime endTime;
}