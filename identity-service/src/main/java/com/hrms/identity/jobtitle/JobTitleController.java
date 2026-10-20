package com.hrms.identity.jobtitle;

import com.hrms.identity.jobtitle.dto.JobTitleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Job Titles")
@RestController
@RequestMapping("/api/v1/job-titles")
@RequiredArgsConstructor
public class JobTitleController {

  private final JobTitleService jobTitleService;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Danh mục chức vụ (dùng cho dropdown Chức danh)")
  public List<JobTitleResponse> list() {
    return jobTitleService.list();
  }
}
