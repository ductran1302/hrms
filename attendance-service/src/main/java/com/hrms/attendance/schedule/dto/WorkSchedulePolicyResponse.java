package com.hrms.attendance.schedule.dto;

import com.hrms.attendance.schedule.WorkSchedulePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Policy lịch tuần — kèm map tên ca để FE hiển thị mà không cần gọi thêm. */
public record WorkSchedulePolicyResponse(
    UUID id,
    String name,
    String description,
    UUID monShiftId,
    UUID tueShiftId,
    UUID wedShiftId,
    UUID thuShiftId,
    UUID friShiftId,
    UUID satShiftId,
    UUID satAlternateShiftId,
    UUID sunShiftId,
    LocalDate rotationAnchorDate,
    boolean defaultPolicy,
    boolean active,
    Map<String, String> shiftNames,
    Instant createdAt,
    Instant updatedAt,
    Long version) {

  public static WorkSchedulePolicyResponse from(
      WorkSchedulePolicy policy, Map<UUID, String> shiftNames) {
    return new WorkSchedulePolicyResponse(
        policy.getId(),
        policy.getName(),
        policy.getDescription(),
        policy.getMonShiftId(),
        policy.getTueShiftId(),
        policy.getWedShiftId(),
        policy.getThuShiftId(),
        policy.getFriShiftId(),
        policy.getSatShiftId(),
        policy.getSatAlternateShiftId(),
        policy.getSunShiftId(),
        policy.getRotationAnchorDate(),
        policy.isDefaultPolicy(),
        policy.isActive(),
        shiftNames.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                e -> e.getKey().toString(), Map.Entry::getValue)),
        policy.getCreatedAt(),
        policy.getUpdatedAt(),
        policy.getVersion());
  }
}
