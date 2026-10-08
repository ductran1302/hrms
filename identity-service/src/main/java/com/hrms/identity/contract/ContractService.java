package com.hrms.identity.contract;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.identity.contract.dto.ContractCreateRequest;
import com.hrms.identity.contract.dto.ContractResponse;
import com.hrms.identity.contract.dto.ContractUpdateRequest;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.history.JobHistoryService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hợp đồng lao động — 1 nhân viên tối đa 1 hợp đồng ACTIVE (rule nghiệp vụ).
 * Lương chỉ trả về cho HR_ADMIN (che tại service, không phụ thuộc FE).
 */
@Service
@RequiredArgsConstructor
public class ContractService {

  private final ContractRepository contractRepository;
  private final EmployeeRepository employeeRepository;
  private final JobHistoryService jobHistoryService;

  @Transactional
  public ContractResponse create(UUID employeeId, ContractCreateRequest request) {
    Employee employee =
        employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    if (contractRepository.existsByEmployee_IdAndStatus(employeeId, ContractStatus.ACTIVE)) {
      throw new ConflictException("Nhân viên đã có hợp đồng hiệu lực — "
          + "hết hạn hoặc chấm dứt hợp đồng cũ trước");
    }
    Contract contract = new Contract();
    contract.setEmployee(employee);
    contract.setContractType(parseType(request.contractType()));
    contract.setStartDate(request.startDate());
    validateDates(request.startDate(), request.endDate());
    contract.setEndDate(request.endDate());
    contract.setPosition(request.position());
    contract.setSalary(request.salary());
    contract.setSignDate(request.signDate());
    contract.setNote(request.note());
    contract.setStatus(ContractStatus.ACTIVE);
    contractRepository.save(contract);

    jobHistoryService.record(employeeId, "contract.type", null,
        contract.getContractType().name(), currentActor());
    jobHistoryService.record(employeeId, "contract.salary", null,
        request.salary() == null ? null : String.valueOf(request.salary()), currentActor());
    jobHistoryService.record(employeeId, "contract.status", null,
        ContractStatus.ACTIVE.name(), currentActor());
    return toResponse(contract, isHrAdmin());
  }

  @Transactional(readOnly = true)
  public List<ContractResponse> search(UUID employeeId, ContractStatus status) {
    boolean includeSalary = isHrAdmin();
    return contractRepository.search(employeeId, status).stream()
        .map(contract -> toResponse(contract, includeSalary))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ContractResponse> listByEmployee(UUID employeeId) {
    boolean includeSalary = isHrAdmin();
    return contractRepository.findByEmployee_IdOrderByStartDateDesc(employeeId).stream()
        .map(contract -> toResponse(contract, includeSalary))
        .toList();
  }

  @Transactional
  public ContractResponse update(UUID id, ContractUpdateRequest patch) {
    Contract contract = find(id);
    UUID employeeId = contract.getEmployeeId();

    if (patch.contractType() != null && !patch.contractType().isBlank()) {
      ContractType newType = parseType(patch.contractType());
      if (newType != contract.getContractType()) {
        jobHistoryService.record(employeeId, "contract.type",
            contract.getContractType().name(), newType.name(), currentActor());
        contract.setContractType(newType);
      }
    }
    if (patch.startDate() != null) {
      contract.setStartDate(patch.startDate());
    }
    if (patch.endDate() != null) {
      contract.setEndDate(patch.endDate());
    }
    validateDates(contract.getStartDate(), contract.getEndDate());

    if (patch.position() != null) {
      contract.setPosition(patch.position());
    }
    if (patch.salary() != null && !patch.salary().equals(contract.getSalary())) {
      jobHistoryService.record(employeeId, "contract.salary",
          contract.getSalary() == null ? null : String.valueOf(contract.getSalary()),
          String.valueOf(patch.salary()), currentActor());
      contract.setSalary(patch.salary());
    }
    if (patch.signDate() != null) {
      contract.setSignDate(patch.signDate());
    }
    if (patch.note() != null) {
      contract.setNote(patch.note());
    }

    if (patch.status() != null && !patch.status().isBlank()) {
      ContractStatus newStatus = parseStatus(patch.status());
      if (newStatus != contract.getStatus()) {
        if (newStatus == ContractStatus.ACTIVE
            && contractRepository.existsByEmployee_IdAndStatusAndIdNot(
                employeeId, ContractStatus.ACTIVE, id)) {
          throw new ConflictException("Nhân viên đã có hợp đồng hiệu lực khác");
        }
        jobHistoryService.record(employeeId, "contract.status",
            contract.getStatus().name(), newStatus.name(), currentActor());
        contract.setStatus(newStatus);
      }
    }

    contractRepository.save(contract);
    return toResponse(contract, isHrAdmin());
  }

  /** Chấm dứt hợp đồng (không xóa vật lý — audit). */
  @Transactional
  public ContractResponse terminate(UUID id) {
    Contract contract = find(id);
    if (contract.getStatus() == ContractStatus.TERMINATED) {
      throw new BusinessRuleException("Hợp đồng đã chấm dứt trước đó");
    }
    jobHistoryService.record(contract.getEmployeeId(), "contract.status",
        contract.getStatus().name(), ContractStatus.TERMINATED.name(), currentActor());
    contract.setStatus(ContractStatus.TERMINATED);
    contractRepository.save(contract);
    return toResponse(contract, isHrAdmin());
  }

  private void validateDates(LocalDate start, LocalDate end) {
    if (start != null && end != null && end.isBefore(start)) {
      throw new BusinessRuleException("Ngày kết thúc phải từ ngày trở đi");
    }
  }

  private ContractType parseType(String raw) {
    try {
      return ContractType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessRuleException("Loại hợp đồng không hợp lệ: " + raw);
    }
  }

  private ContractStatus parseStatus(String raw) {
    try {
      return ContractStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessRuleException("Trạng thái hợp đồng không hợp lệ: " + raw);
    }
  }

  private ContractResponse toResponse(Contract contract, boolean includeSalary) {
    Employee employee = contract.getEmployee();
    return new ContractResponse(
        contract.getId(),
        contract.getEmployeeId(),
        employee == null ? null : employee.getEmployeeCode(),
        employee == null ? null : employee.getFullName(),
        contract.getContractType().name(),
        contract.getStartDate(),
        contract.getEndDate(),
        contract.getPosition(),
        includeSalary ? contract.getSalary() : null,
        contract.getSignDate(),
        contract.getNote(),
        contract.getStatus().name());
  }

  private Contract find(UUID id) {
    return contractRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Contract", id));
  }

  private boolean isHrAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.getAuthorities().stream().anyMatch(a -> "ROLE_HR_ADMIN".equals(a.getAuthority()));
  }

  private String currentActor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null ? null : auth.getName();
  }
}
