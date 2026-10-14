package com.hrms.identity.contract;

import com.hrms.identity.contract.dto.ContractCreateRequest;
import com.hrms.identity.contract.dto.ContractExtendRequest;
import com.hrms.identity.contract.dto.ContractResponse;
import com.hrms.identity.contract.dto.ContractUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Contracts")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContractController {

  private final ContractService contractService;

  @PostMapping("/employees/{employeeId}/contracts")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Tạo hợp đồng (rule: 1 hợp đồng hiệu lực/chờ hiệu lực/nhân viên; "
      + "startDate tương lai → PENDING)")
  public ContractResponse create(@PathVariable UUID employeeId,
      @Valid @RequestBody ContractCreateRequest request) {
    return contractService.create(employeeId, request);
  }

  @GetMapping("/contracts")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER','EMPLOYEE')")
  @Operation(summary = "Tìm kiếm hợp đồng (salary chỉ hiện với HR_ADMIN)")
  public List<ContractResponse> search(
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(required = false) String status) {
    return contractService.search(employeeId, parseStatus(status));
  }

  @GetMapping("/contracts/expiring-soon")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
  @Operation(summary = "Cảnh báo hợp đồng ACTIVE sắp hết hạn trong N ngày (mặc định 30)")
  public List<ContractResponse> expiringSoon(
      @RequestParam(name = "days", defaultValue = "30") int days) {
    return contractService.expiringSoon(days);
  }

  @GetMapping("/employees/{employeeId}/contracts")
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER','EMPLOYEE')")
  @Operation(summary = "Hợp đồng của 1 nhân viên (lịch sử — hợp đồng cũ được giữ lại)")
  public List<ContractResponse> listByEmployee(@PathVariable UUID employeeId) {
    return contractService.listByEmployee(employeeId);
  }

  @PatchMapping("/contracts/{id}")
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Cập nhật hợp đồng (đổi endDate ghi lịch sử contract.endDate)")
  public ContractResponse update(@PathVariable UUID id,
      @Valid @RequestBody ContractUpdateRequest request) {
    return contractService.update(id, request);
  }

  @PostMapping("/contracts/{id}/extend")
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Gia hạn hợp đồng (PENDING/ACTIVE; ngày mới phải sau ngày hết hạn cũ)")
  public ContractResponse extend(@PathVariable UUID id,
      @Valid @RequestBody ContractExtendRequest request) {
    return contractService.extend(id, request);
  }

  @PostMapping("/contracts/{id}/replace")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Thay thế hợp đồng: chấm dứt cũ (giữ lịch sử) + tạo mới linked")
  public ContractResponse replace(@PathVariable UUID id,
      @Valid @RequestBody ContractCreateRequest request) {
    return contractService.replace(id, request);
  }

  @PatchMapping("/contracts/{id}/terminate")
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Chấm dứt hợp đồng (lý do lưu vào lịch sử)")
  public ContractResponse terminate(@PathVariable UUID id,
      @RequestParam(name = "reason", required = false) String reason) {
    return contractService.terminate(id, reason);
  }

  private ContractStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return ContractStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new com.hrms.common.exception.BusinessRuleException(
          "Trạng thái hợp đồng không hợp lệ: " + raw);
    }
  }
}
