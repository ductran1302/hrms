package com.hrms.attendance.shift;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkShiftRepository extends JpaRepository<WorkShift, UUID> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);

  Optional<WorkShift> findByName(String name);

  /**
   * Lấy toàn bộ danh sách theo createdAt giảm dần — filter type/active/q làm ở service
   * (in-memory, bảng danh mục nhỏ). KHÔNG dùng query param null/empty cùng lúc
   * (bind param null gây lỗi PG "lower(bytea) does not exist" với lower(name) search).
   */
  @Query("select s from WorkShift s order by s.createdAt desc")
  List<WorkShift> findAllOrdered();
}
