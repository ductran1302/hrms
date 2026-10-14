package com.hrms.identity.employee.dto;

import java.util.UUID;

/** Gán/bỏ gán lịch nghỉ lễ cho nhân viên (calendarId = null → fallback mặc định). */
public record EmployeeCalendarAssignRequest(UUID calendarId) {}
