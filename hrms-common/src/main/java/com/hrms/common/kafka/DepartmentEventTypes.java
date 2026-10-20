package com.hrms.common.kafka;

/** Giá trị {@code eventType} của event phòng ban — khớp default trong file .avsc. */
public final class DepartmentEventTypes {

  public static final String CREATED = "department.created";
  public static final String UPDATED = "department.updated";

  private DepartmentEventTypes() {}
}
