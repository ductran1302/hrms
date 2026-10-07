package com.hrms.common.kafka;

/** Giá trị {@code eventType} của các event nhân sự — khớp default trong file .avsc. */
public final class EmployeeEventTypes {

  public static final String CREATED = "employee.created";
  public static final String UPDATED = "employee.updated";
  public static final String TERMINATED = "employee.terminated";

  private EmployeeEventTypes() {}
}
