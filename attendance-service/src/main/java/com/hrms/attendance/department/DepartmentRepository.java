package com.hrms.attendance.department;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, String> {

  List<Department> findByParentId(String parentId);

  List<Department> findByManagerIdAndStatus(String managerId, String status);
}
