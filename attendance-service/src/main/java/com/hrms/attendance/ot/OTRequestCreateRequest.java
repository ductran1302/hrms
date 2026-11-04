package com.hrms.attendance.ot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request tạo đơn OT master. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OTRequestCreateRequest {

  @NotBlank(message = "Lý do không được để trống")
  @Size(max = 500, message = "Lý do tối đa 500 ký tự")
  private String reason;

  /** CHẾ ĐỘ 1: Danh sách chi tiết từng ngày OT (dữ liệu cuối cùng sau preview). */
  private List<OTRequestItemCreateRequest> items;

  /** CHẾ ĐỘ 2: Tạo nhanh theo chuỗi ngày (items sẽ được sinh ra từ dateRange). */
  private LocalDate startDate;
  private LocalDate endDate;
  private LocalDateTime defaultStartTime;
  private LocalDateTime defaultEndTime;

  /** Validate: ít nhất phải có items hoặc dateRange. */
  public boolean isEmpty() {
    return (items == null || items.isEmpty()) && (startDate == null || endDate == null);
  }
}