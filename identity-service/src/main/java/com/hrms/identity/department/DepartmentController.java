package com.hrms.identity.department;

import com.hrms.identity.department.dto.DepartmentCreateRequest;
import com.hrms.identity.department.dto.DepartmentNode;
import com.hrms.identity.department.dto.DepartmentResponse;
import com.hrms.identity.department.dto.DepartmentUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Departments")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

  private final DepartmentService departmentService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Tạo phòng ban mới")
  public DepartmentResponse create(@Valid @RequestBody DepartmentCreateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return departmentService.create(request, jwt.getSubject());
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Danh sách phòng ban (phẳng, kèm số nhân viên active)")
  public List<DepartmentResponse> list() {
    return departmentService.list();
  }

  @GetMapping("/tree")
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD','MANAGER')")
  @Operation(summary = "Sơ đồ phòng ban (cây theo phòng cha)")
  public List<DepartmentNode> tree() {
    return departmentService.tree();
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Cập nhật phòng ban (parentId/managerId null = bỏ quan hệ)")
  public DepartmentResponse update(@PathVariable UUID id,
      @Valid @RequestBody DepartmentUpdateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return departmentService.update(id, request, jwt.getSubject());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Vô hiệu hóa phòng ban (soft delete)")
  public void deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    departmentService.deactivate(id, jwt.getSubject());
  }
}
