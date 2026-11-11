package com.hrms.identity.jobtitle;

import com.hrms.identity.jobtitle.dto.JobTitleCreateRequest;
import com.hrms.identity.jobtitle.dto.JobTitleResponse;
import com.hrms.identity.jobtitle.dto.JobTitleUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Job Titles")
@RestController
@RequestMapping("/api/v1/job-titles")
@RequiredArgsConstructor
public class JobTitleController {

  private final JobTitleService jobTitleService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Tạo chức vụ mới")
  public JobTitleResponse create(@Valid @RequestBody JobTitleCreateRequest request) {
    return jobTitleService.create(request);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Danh mục chức vụ (dùng cho dropdown Chức danh)")
  public List<JobTitleResponse> list() {
    return jobTitleService.list();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Chi tiết chức vụ")
  public JobTitleResponse get(@PathVariable UUID id) {
    return jobTitleService.get(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Cập nhật chức vụ (field null = không đổi)")
  public JobTitleResponse update(@PathVariable UUID id,
      @Valid @RequestBody JobTitleUpdateRequest request) {
    return jobTitleService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Xóa chức vụ (chặn nếu đang được gán cho nhân viên)")
  public void delete(@PathVariable UUID id) {
    jobTitleService.delete(id);
  }
}
