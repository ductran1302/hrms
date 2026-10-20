package com.hrms.identity.orgchart;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Org Chart")
@RestController
@RequestMapping("/api/v1/org-chart")
@RequiredArgsConstructor
public class OrgChartController {

  private final OrgChartService orgChartService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Sơ đồ tổ chức theo quản lý trực tiếp (root = không có manager)")
  public List<OrgChartNode> tree() {
    return orgChartService.tree();
  }

  @GetMapping("/stats")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Thống kê đầu trang (tổng nhân sự/phòng ban/quản lý/đang làm + delta 30 ngày)")
  public OrgChartStats stats() {
    return orgChartService.stats();
  }
}
