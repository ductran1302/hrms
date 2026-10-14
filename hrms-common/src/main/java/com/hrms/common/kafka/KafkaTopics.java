package com.hrms.common.kafka;

/** Tên topic Kafka dùng chung — khai báo 1 nơi, mọi service import từ đây. */
public final class KafkaTopics {

  /** RecordNameStrategy: nhiều loại event (created/updated/terminated) dùng chung 1 topic. */
  public static final String EMPLOYEE_V1 = "hrms.employee.v1";

  /** Event thay đổi ngày lễ (holiday.changed) — attendance投影 sang bảng holidays. */
  public static final String HOLIDAY_V1 = "hrms.holiday.v1";

  public static final String TIMESHEET_V1 = "hrms.timesheet.v1";
  public static final String PAYROLL_RUN_V1 = "hrms.payroll-run.v1";
  public static final String NOTIFICATION_V1 = "hrms.notification.v1";

  private KafkaTopics() {}

  /** Topic chết: consumer/kafkaTemplate đẩy message lỗi không tiêu thụ được vào đây. */
  public static String dlq(String topic) {
    return topic + ".DLQ";
  }
}
