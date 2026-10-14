package com.hrms.identity.employee;

import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

  EmployeeResponse toResponse(Employee employee);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "calendarId", ignore = true)
  @Mapping(target = "departmentName", ignore = true)
  @Mapping(target = "photoUrl", ignore = true)
  @Mapping(target = "educationLevel", ignore = true)
  @Mapping(target = "skills", ignore = true)
  @Mapping(target = "emergencyContactName", ignore = true)
  @Mapping(target = "emergencyContactPhone", ignore = true)
  @Mapping(target = "emergencyContactRelation", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  Employee toEntity(EmployeeCreateRequest request);
}
