package com.hrms.common.kafka;

/** Kiểu event ngày lễ — một eventType duy nhất, phân loại qua trường action trong payload. */
public final class HolidayEventTypes {

  public static final String CHANGED = "holiday.changed";

  private HolidayEventTypes() {}
}
