package com.hrms.attendance.schedule.dto;

import java.time.LocalDate;

/** Kết quả sinh roster (idempotent): created/updated/unchanged/skipped/noPolicy. */
public record GenerateRosterResponse(
    LocalDate from,
    LocalDate to,
    int employees,
    int created,
    int updated,
    int unchanged,
    int skipped,
    int noPolicy) {}
