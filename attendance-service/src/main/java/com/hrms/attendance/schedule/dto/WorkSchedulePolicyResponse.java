package com.hrms.attendance.schedule.dto;

import com.hrms.attendance.schedule.WorkSchedulePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record WorkSchedulePolicyResponse(
    UUID id,
    UUID policyGroupId,
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
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Map<String, String> shiftNames,
    Instant createdAt,
    Instant updatedAt,
    Long version) {

  public static WorkSchedulePolicyResponse from(
      WorkSchedulePolicy policy, Map<UUID, String> shiftNames) {
    UUID group = policy.getPolicyGroupId() == null ? policy.getId() : policy.getPolicyGroupId();
    return new WorkSchedulePolicyResponse(
        policy.getId(),
        group,
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
        policy.getEffectiveFrom(),
        policy.getEffectiveTo(),
        shiftNames.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                e -> e.getKey().toString(), Map.Entry::getValue)),
        policy.getCreatedAt(),
        policy.getUpdatedAt(),
        policy.getVersion());
  }
}
