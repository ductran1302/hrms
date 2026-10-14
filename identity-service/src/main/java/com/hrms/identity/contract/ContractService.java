package com.hrms.identity.contract;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.identity.contract.dto.ContractCreateRequest;
import com.hrms.identity.contract.dto.ContractExtendRequest;
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
 * Hợp đồng lao động — mỗi nhân viên tối đa 1 hợp đồng hiệu lực hoặc chờ hiệu lực
 * (rule nghiệp vụ, chặn chồng lấn). Trạng thái nghiệp vụ ({@link ContractStatus}) tách khỏi
 * trạng thái hiệu lực tính theo ngày: hợp đồng PENDING đã ký nhưng chưa đến ngày bắt đầu
 * chưa có hiệu lực; ACTIVE quá ngày hết hạn chờ job {@code ContractExpiryJob} chuyển EXPIRED.
 * Lương chỉ trả về cho HR_ADMIN (che tại service, không phụ thuộc FE).
 */
@Service
@RequiredArgsConstructor
public class ContractService {

  /** Trạng thái bị chặn chồng lấn: chỉ 1 hợp đồng/nhân viên ở trạng thái này tại một thời điểm. */
  private static final List<ContractStatus> OCCUPYING =
      List.of(ContractStatus.PENDING, ContractStatus.ACTIVE);

  private final ContractRepository contractRepository;
  private final EmployeeRepository employeeRepository;
  private final JobHistoryService jobHistoryService;

  @Transactional
  public ContractResponse create(UUID employeeId, ContractCreateRequest request) {
    Employee employee =
        employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    if (contractRepository.existsByEmployee_IdAndStatusIn(employeeId, OCCUPYING)) {
      throw new ConflictException("Nhân viên đã có hợp đồng hiệu lực hoặc chờ hiệu lực — "
          + "hết hạn/chấm dứt hợp đồng cũ trước khi ký mới");
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
    // Tách trạng thái: đã ký nhưng chưa đến ngày bắt đầu → PENDING (chưa hiệu lực).
    contract.setStatus(request.startDate().isAfter(LocalDate.now())
        ? ContractStatus.PENDING
        : ContractStatus.ACTIVE);
    contractRepository.save(contract);

    jobHistoryService.record(employeeId, "contract.type", null,
        contract.getContractType().name(), currentActor());
    jobHistoryService.record(employeeId, "contract.salary", null,
        request.salary() == null ? null : String.valueOf(request.salary()), currentActor());
    jobHistoryService.record(employeeId, "contract.status", null,
        contract.getStatus().name(), currentActor());
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

  /** Hợp đồng ACTIVE sắp hết hạn trong {@code days} ngày tới (cảnh báo). */
  @Transactional(readOnly = true)
  public List<ContractResponse> expiringSoon(int days) {
    int window = Math.max(1, Math.min(days, 365));
    LocalDate today = LocalDate.now();
    boolean includeSalary = isHrAdmin();
    return contractRepository
        .findByStatusAndEndDateBetween(ContractStatus.ACTIVE, today, today.plusDays(window))
        .stream()
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
    if (patch.endDate() != null && !patch.endDate().equals(contract.getEndDate())) {
      jobHistoryService.record(employeeId, "contract.endDate",
          contract.getEndDate() == null ? null : contract.getEndDate().toString(),
          patch.endDate().toString(), currentActor());
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
        if (OCCUPYING.contains(newStatus)
            && contractRepository.existsByEmployee_IdAndStatusInAndIdNot(
                employeeId, OCCUPYING, id)) {
          throw new ConflictException(
              "Nhân viên đã có hợp đồng hiệu lực hoặc chờ hiệu lực khác");
        }
        jobHistoryService.record(employeeId, "contract.status",
            contract.getStatus().name(), newStatus.name(), currentActor());
        contract.setStatus(newStatus);
      }
    }

    contractRepository.save(contract);
    return toResponse(contract, isHrAdmin());
  }

  /**
   * Gia hạn hợp đồng — chỉ hợp đồng PENDING/ACTIVE; ngày mới phải sau ngày bắt đầu và
   * sau ngày hết hạn cũ (nếu có). Ghi lịch sử contract.endDate.
   */
  @Transactional
  public ContractResponse extend(UUID id, ContractExtendRequest request) {
    Contract contract = find(id);
    if (contract.getStatus() != ContractStatus.PENDING
        && contract.getStatus() != ContractStatus.ACTIVE) {
      throw new BusinessRuleException(
          "Chỉ gia hạn hợp đồng đang hiệu lực hoặc chờ hiệu lực");
    }
    if (request.endDate().isBefore(contract.getStartDate())) {
      throw new BusinessRuleException("Ngày kết thúc mới phải từ ngày bắt đầu trở đi");
    }
    if (contract.getEndDate() != null && !request.endDate().isAfter(contract.getEndDate())) {
      throw new BusinessRuleException("Ngày kết thúc mới phải sau ngày hết hạn hiện tại");
    }
    jobHistoryService.record(contract.getEmployeeId(), "contract.endDate",
        contract.getEndDate() == null ? null : contract.getEndDate().toString(),
        request.endDate().toString(), currentActor());
    contract.setEndDate(request.endDate());
    if (request.note() != null && !request.note().isBlank()) {
      contract.setNote(request.note());
    }
    contractRepository.save(contract);
    return toResponse(contract, isHrAdmin());
  }

  /**
   * Thay thế hợp đồng: chấm dứt hợp đồng cũ (giữ nguyên record — audit) và tạo hợp đồng mới
   * liên kết {@code replacesContractId}. Hợp đồng cũ không xóa nên lịch sử ký thay được truy vết.
   */
  @Transactional
  public ContractResponse replace(UUID id, ContractCreateRequest request) {
    Contract old = find(id);
    if (old.getStatus() == ContractStatus.TERMINATED) {
      throw new BusinessRuleException("Hợp đồng đã chấm dứt trước đó");
    }
    UUID employeeId = old.getEmployeeId();
    jobHistoryService.record(employeeId, "contract.status",
        old.getStatus().name(), ContractStatus.TERMINATED.name(), currentActor(),
        "Thay thế bằng hợp đồng mới");
    old.setStatus(ContractStatus.TERMINATED);
    contractRepository.save(old);

    Contract contract = new Contract();
    Employee employee = old.getEmployee();
    contract.setEmployee(employee);
    contract.setContractType(parseType(request.contractType()));
    contract.setStartDate(request.startDate());
    validateDates(request.startDate(), request.endDate());
    contract.setEndDate(request.endDate());
    contract.setPosition(request.position());
    contract.setSalary(request.salary());
    contract.setSignDate(request.signDate());
    contract.setNote(request.note());
    contract.setStatus(request.startDate().isAfter(LocalDate.now())
        ? ContractStatus.PENDING
        : ContractStatus.ACTIVE);
    contract.setReplacesContractId(old.getId());
    contractRepository.save(contract);

    jobHistoryService.record(employeeId, "contract.type", old.getContractType().name(),
        contract.getContractType().name(), currentActor(),
        "Ký thay hợp đồng " + old.getId());
    jobHistoryService.record(employeeId, "contract.salary",
        old.getSalary() == null ? null : String.valueOf(old.getSalary()),
        request.salary() == null ? null : String.valueOf(request.salary()), currentActor());
    jobHistoryService.record(employeeId, "contract.status", null,
        contract.getStatus().name(), currentActor());
    return toResponse(contract, isHrAdmin());
  }

  /**
   * Chấm dứt hợp đồng (không xóa vật lý — audit). Lý do lưu vào history (cột reason).
   */
  @Transactional
  public ContractResponse terminate(UUID id, String reason) {
    Contract contract = find(id);
    if (contract.getStatus() == ContractStatus.TERMINATED) {
      throw new BusinessRuleException("Hợp đồng đã chấm dứt trước đó");
    }
    jobHistoryService.record(contract.getEmployeeId(), "contract.status",
        contract.getStatus().name(), ContractStatus.TERMINATED.name(), currentActor(), reason);
    contract.setStatus(ContractStatus.TERMINATED);
    contractRepository.save(contract);
    return toResponse(contract, isHrAdmin());
  }

  /**
   * Job nightly: ACTIVE quá ngày hết hạn → EXPIRED (cập nhật trạng thái hiệu lực theo ngày,
   * không can thiệp trạng thái nghiệp vụ khác).
   */
  @Transactional
  public int expireOverdue() {
    List<Contract> overdue =
        contractRepository.findByStatusAndEndDateBefore(ContractStatus.ACTIVE, LocalDate.now());
    for (Contract contract : overdue) {
      jobHistoryService.record(contract.getEmployeeId(), "contract.status",
          ContractStatus.ACTIVE.name(), ContractStatus.EXPIRED.name(), "system");
      contract.setStatus(ContractStatus.EXPIRED);
      contractRepository.save(contract);
    }
    return overdue.size();
  }

  /**
   * Trạng thái hiệu lực tính theo ngày — tách khỏi trạng thái nghiệp vụ lưu trong DB.
   * NOT_YET_EFFECTIVE: đã ký nhưng chưa đến ngày bắt đầu; ENDED: qua ngày hết hạn nhưng
   * chưa kịp job chuyển EXPIRED; IN_EFFECT: đang trong thời hạn.
   */
  static String effectiveStatus(Contract contract, LocalDate today) {
    return switch (contract.getStatus()) {
      case TERMINATED -> ContractStatus.TERMINATED.name();
      case EXPIRED -> ContractStatus.EXPIRED.name();
      case PENDING -> "NOT_YET_EFFECTIVE";
      case ACTIVE -> {
        if (contract.getStartDate().isAfter(today)) {
          yield "NOT_YET_EFFECTIVE";
        }
        if (contract.getEndDate() != null && contract.getEndDate().isBefore(today)) {
          yield "ENDED";
        }
        yield "IN_EFFECT";
      }
    };
  }

  private void validateDates(LocalDate start, LocalDate end) {
    if (start != null && end != null && end.isBefore(start)) {
      throw new BusinessRuleException("Ngày kết thúc phải từ ngày bắt đầu trở đi");
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
        contract.getStatus().name(),
        effectiveStatus(contract, LocalDate.now()),
        contract.getReplacesContractId());
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
