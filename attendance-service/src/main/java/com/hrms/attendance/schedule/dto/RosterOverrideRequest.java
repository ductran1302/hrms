package com.hrms.attendance.schedule.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Sửa tay 1 ngày: shiftId null = nghỉ; note ≤ 500 ký tự. */
public record RosterOverrideRequest(UUID shiftId, @Size(max = 500) String note) {}
