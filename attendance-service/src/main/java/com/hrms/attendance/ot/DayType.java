package com.hrms.attendance.ot;

/** Loại ngày cho tính toán hệ số OT. */
public enum DayType {
  WEEKDAY, // thứ 2–6, không phải ngày lễ
  WEEKEND, // thứ 7, CN
  HOLIDAY  // ngày lễ (theo hrms.holiday.v1)
}