package com.hrms.identity.event;

import com.hrms.identity.department.Department;
import java.util.List;

/**
 * JSON payload lưu trong outbox cho event phòng ban (department.created/updated) —
 * trung gian giữa entity và Avro record (epoch/string, không phụ thuộc Jackson module).
 */
public record DepartmentEventPayload(
    String departmentId,
    String code,
    String name,
    String parentId,
    String managerId,
    String status,
    String actorId,
    List<String> changedFields) {

  public static DepartmentEventPayload from(Department department, String actorId,
      List<String> changedFields) {
    return new DepartmentEventPayload(
        department.getId() == null ? null : department.getId().toString(),
        department.getCode(),
        department.getName(),
        department.getParentId() == null ? null : department.getParentId().toString(),
        department.getManagerId() == null ? null : department.getManagerId().toString(),
        department.getStatus() == null ? null : department.getStatus().name(),
        actorId,
        changedFields == null ? List.of() : changedFields);
  }
}
