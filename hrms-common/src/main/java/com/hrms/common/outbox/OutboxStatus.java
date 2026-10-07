package com.hrms.common.outbox;

/** Trạng thái của một dòng trong bảng outbox. */
public enum OutboxStatus {
  /** Chờ publish. */
  PENDING,
  /** Đã gửi thành công lên Kafka. */
  PUBLISHED,
  /** Đã vượt số lần thử tối đa — cần xem xét thủ công (requeue / dead letter). */
  FAILED
}
