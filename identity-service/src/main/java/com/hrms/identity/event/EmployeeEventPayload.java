package com.hrms.identity.event;

import com.hrms.identity.employee.Employee;
import java.util.List;

/**
 * JSON payload lưu trong outbox — trung gian giữa entity và Avro record.
 * Epoch millis (không dùng Instant) để JSON không phụ thuộc Jackson module.
 *
 * <p>{@code roles} = realm roles KC tại thời điểm emit (null = không đụng tới roles —
 * attendance chỉ update roles khi field này khác null).
 */
public record EmployeeEventPayload(
    String employeeId,
    String employeeCode,
    String fullName,
    String email,
    String departmentId,
    String departmentName,
    String position,
    String managerId,
    String status,
    Long hiredAt,
    Long terminatedAt,
    String reason,
    String actorId,
    List<String> changedFields,
    List<String> roles) {

  public static EmployeeEventPayload from(Employee employee, String actorId,
      List<String> changedFields, String reason, Long terminatedAt) {
    return from(employee, actorId, changedFields, reason, terminatedAt, null);
  }

  public static EmployeeEventPayload from(Employee employee, String actorId,
      List<String> changedFields, String reason, Long terminatedAt, List<String> roles) {
    return new EmployeeEventPayload(
        employee.getId() == null ? null : employee.getId().toString(),
        employee.getEmployeeCode(),
        employee.getFullName(),
        employee.getEmail(),
        employee.getDepartmentId() == null ? null : employee.getDepartmentId().toString(),
        employee.getDepartmentName(),
        employee.getPosition(),
        employee.getManagerId() == null ? null : employee.getManagerId().toString(),
        employee.getStatus().name(),
        employee.getHiredAt() == null ? null : employee.getHiredAt().toEpochMilli(),
        terminatedAt,
        reason,
        actorId,
        changedFields == null ? List.of() : changedFields,
        roles);
  }
}
