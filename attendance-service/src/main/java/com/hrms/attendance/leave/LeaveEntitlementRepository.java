package com.hrms.attendance.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveEntitlementRepository extends JpaRepository<LeaveEntitlement, UUID> {

  Optional<LeaveEntitlement> findByEmployeeIdAndYearAndSource(
      String employeeId, int year, LeaveSource source);

  long countByYear(int year);

  List<LeaveEntitlement> findByEmployeeIdAndYearOrderBySourceAsc(String employeeId, int year);

  List<LeaveEntitlement> findByEmployeeIdAndYearAndSourceIn(
      String employeeId, int year, List<LeaveSource> sources);

  /**
   * Tổng hợp tính Tổng/Còn lại của năm {@code year}:
   *
   * <ul>
   *   <li>Dòng của năm — bỏ dòng đã hết hạn ({@code expires_at} &lt; hôm nay; hết hạn từ
   *       0h hôm sau ngày expires_at).
   *   <li>Dòng BONUS của năm TRƯỚC còn hạn mà {@code expires_at >= fyStart(year)} — auto-spill
   *       (bonus tùy chỉnh thời hạn vượt năm TC được cộng sang năm mới).
   * </ul>
   *
   * @param spillSource nguồn được spill (LUÔN là BONUS — truyền tham số cho dễ test JPQL)
   */
  @Query("""
      select e.employeeId, e.source, coalesce(sum(e.days), 0)
      from LeaveEntitlement e
      where (e.year = :year and (e.expiresAt is null or e.expiresAt >= :today))
         or (e.source = :spillSource and e.year < :year and e.expiresAt is not null
             and e.expiresAt >= :fyStart and e.expiresAt >= :today)
      group by e.employeeId, e.source
      """)
  List<Object[]> sumDays(
      @Param("year") int year,
      @Param("spillSource") LeaveSource spillSource,
      @Param("fyStart") LocalDate fyStart,
      @Param("today") LocalDate today);

  /**
   * Dòng hiển thị ở Chi tiết quota năm {@code year}: mọi dòng của năm (KỂ CẢ đã hết hạn —
   * hiện tag đỏ để đối chiếu) + BONUS năm trước còn hạn tại fyStart (dòng spill — đánh dấu
   * "từ năm X" ở FE).
   */
  @Query("""
      select e from LeaveEntitlement e
      where e.employeeId = :employeeId
        and (e.year = :year
             or (e.source = :spillSource and e.year < :year and e.expiresAt is not null
                 and e.expiresAt >= :fyStart))
      order by e.source asc, e.year asc
      """)
  List<LeaveEntitlement> findDetail(
      @Param("employeeId") String employeeId,
      @Param("year") int year,
      @Param("spillSource") LeaveSource spillSource,
      @Param("fyStart") LocalDate fyStart);

  /** D?i employee_id khi identity t?o l?i nh?n vi?n v?i id m?i (c?ng employeeCode). */
  @Modifying
  @Query("update LeaveEntitlement e set e.employeeId = :newId where e.employeeId = :oldId")
  int rekeyEmployee(@Param("oldId") String oldId, @Param("newId") String newId);
}
