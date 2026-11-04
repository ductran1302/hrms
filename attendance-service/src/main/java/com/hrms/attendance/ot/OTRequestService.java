package com.hrms.attendance.ot;

import com.hrms.attendance.employee.Employee;
import com.hrms.attendance.employee.EmployeeAccess;
import com.hrms.attendance.employee.EmployeeAccessResolver;
import com.hrms.attendance.employee.EmployeeRepository;
import com.hrms.attendance.department.DepartmentService;
import com.hrms.attendance.holiday.HolidayService;
import com.hrms.attendance.leave.LeaveSettings;
import com.hrms.attendance.leave.LeaveSettingsService;
import com.hrms.attendance.leave.PeriodEngine;
import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.security.EmployeeScope;
import com.hrms.common.security.Roles;
import com.hrms.common.web.PageResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý lifecycle đơn OT (tạo, approve, reject, cancel, delete) + list theo scope.
 *
 * <p>Approval flow:
 * - Đơn tạo thẳng PENDING; approverId = employeeCode của manager trực tiếp (dữ liệu cũ
 *   có thể là employeeId — toDTO/assertCanApprove chấp nhận cả hai).
 * - Quyền duyệt không dựa role thuần: ADMIN/GLOBAL_LEAD toàn quyền; còn lại phải là
 *   approver trên đơn, manager trực tiếp của người tạo, hoặc DEPT_LEAD có scope phủ
 *   người tạo. Không bao giờ tự duyệt đơn của mình.
 */
@Service
@RequiredArgsConstructor
public class OTRequestService {

  public static final String ACTION = "OT_REQUEST";
  public static final String TARGET_TYPE = "OTRequest";

  private final OTRequestRepository otRequestRepository;
  private final OTRequestItemRepository otRequestItemRepository;
  private final OTConfigRepository otConfigRepository;
  private final OTRequestCalculator calculator;
  private final EmployeeRepository employeeRepository;
  private final DepartmentService departmentService;
  private final HolidayService holidayService;
  private final EmployeeAccessResolver employeeAccessResolver;
  private final AuditService auditService;
  private final LeaveSettingsService leaveSettingsService;

  // Trần OT (ngày/năm) KHÔNG chặn lúc tạo/sửa đơn — chỉ áp dụng khi tính/chốt giờ OT.

  /**
   * Tạo đơn OT master + items. Nhân viên tạo cho chính mình.
   * @param username employeeCode của người tạo (identity).
   */
  @Transactional
  public OTRequestDTO create(String username, OTRequestCreateRequest request) {
    Employee creator = employeeRepository.findByEmployeeCode(username)
        .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + username));

    if (request == null || request.isEmpty()) {
      throw new BusinessRuleException("Vui lòng cung cấp lý do và ít nhất 1 ngày OT");
    }

    List<OTRequestItemCreateRequest> itemRequests = expandDateRange(request);
    if (itemRequests.isEmpty()) {
      throw new BusinessRuleException("Vui lòng cung cấp lý do và ít nhất 1 ngày OT");
    }

    OTConfig config = otConfigRepository.findActive()
        .orElseThrow(() -> new BusinessRuleException("Chưa có cấu hình OT. Vui lòng liên hệ quản lý."));

    Set<LocalDate> seenDates = new HashSet<>();
    PeriodEngine.Period currentPeriod = currentWorkPeriod();
    BigDecimal totalHours = BigDecimal.ZERO;
    List<OTRequestItemDTO> itemDTOs = new ArrayList<>();

    for (OTRequestItemCreateRequest itemReq : itemRequests) {
      validateItemInterval(itemReq);

      LocalDate date = itemReq.getStartTime().toLocalDate();
      if (!seenDates.add(date)) {
        throw new BusinessRuleException("Trùng ngày OT trong đơn: " + date);
      }
      validateDateInCurrentPeriod(date, currentPeriod);

      DayType dayType = determineDayType(date);
      BigDecimal[] hours = calculator.computeItemHours(
          itemReq.getStartTime(), itemReq.getEndTime(), config);
      BigDecimal dayHours = hours[0];
      BigDecimal nightHours = hours[1];
      BigDecimal totalItemHours = dayHours.add(nightHours);

      OTRequestItem item = new OTRequestItem();
      item.setOtDate(date);
      item.setStartTime(itemReq.getStartTime());
      item.setEndTime(itemReq.getEndTime());
      item.setDayType(dayType);
      item.setDayHours(dayHours);
      item.setNightHours(nightHours);
      item.setApprovedHours(totalItemHours); // approved_hours = real hours ban đầu

      itemDTOs.add(OTRequestItemDTO.from(item));
      totalHours = totalHours.add(totalItemHours);
    }

    totalHours = totalHours.setScale(2, BigDecimal.ROUND_HALF_UP);

    OTRequest otRequest = new OTRequest();
    otRequest.setEmployeeId(creator.getEmployeeId());
    otRequest.setEmployeeCode(creator.getEmployeeCode());
    otRequest.setFullName(creator.getFullName());
    otRequest.setDepartmentId(stringToUuid(creator.getDepartmentId()));
    otRequest.setReason(request.getReason());
    otRequest.setTotalHours(totalHours);
    otRequest.setStatus(OTRequestStatus.PENDING);

    // approver = manager trực tiếp (lưu employeeCode để khớp preferred_username khi duyệt)
    String managerId = creator.getManagerId();
    if (managerId != null && !managerId.isBlank()) {
      employeeRepository.findById(managerId)
          .ifPresent(m -> otRequest.setApproverId(m.getEmployeeCode()));
    }

    otRequest.setRequestCode(generateRequestCode());

    // Save master request first so FK from items is valid
    OTRequest savedRequest = otRequestRepository.save(otRequest);

    itemDTOs.forEach(dto -> {
      OTRequestItem savedItem = new OTRequestItem();
      savedItem.setRequest(savedRequest);
      savedItem.setOtDate(dto.otDate());
      savedItem.setStartTime(dto.startTime());
      savedItem.setEndTime(dto.endTime());
      savedItem.setDayType(dto.dayType());
      savedItem.setDayHours(dto.dayHours());
      savedItem.setNightHours(dto.nightHours());
      savedItem.setApprovedHours(dto.approvedHours());
      otRequestItemRepository.save(savedItem);
    });

    auditService.success(ACTION + "_CREATE", TARGET_TYPE, otRequest.getId(),
        "employee=" + username + ", hours=" + totalHours + ", items=" + itemDTOs.size());

    return toDTO(otRequest);
  }

  /** Approve/Reject đơn OT (PENDING) hoặc yêu cầu hủy (PENDING_CANCEL). */
  @Transactional
  public void approve(UUID requestId, String username, boolean approved, String reason) {
    OTRequest request = findById(requestId);

    boolean isCancelRequest = request.getStatus() == OTRequestStatus.PENDING_CANCEL;
    if (request.getStatus() != OTRequestStatus.PENDING && !isCancelRequest) {
      throw new BusinessRuleException("Đơn không ở trạng thái chờ duyệt");
    }

    assertCanApprove(request, username);

    if (approved) {
      if (isCancelRequest) {
        request.setStatus(OTRequestStatus.CANCELLED_APPROVED);
        request.setCancelledAt(LocalDateTime.now());
      } else {
        request.setStatus(OTRequestStatus.APPROVED);
        request.setApprovedAt(LocalDateTime.now());
      }
      request.setApproverId(username);
    } else if (isCancelRequest) {
      // Từ chối yêu cầu hủy → đơn trở lại APPROVED, giữ nguyên người duyệt gốc
      request.setStatus(OTRequestStatus.APPROVED);
      request.setCancellationReason(null);
    } else {
      if (reason == null || reason.isBlank()) {
        throw new BusinessRuleException("Phải nhập lý do từ chối");
      }
      request.setStatus(OTRequestStatus.REJECTED);
      request.setRejectionReason(reason);
    }

    otRequestRepository.save(request);

    auditService.success(ACTION + (approved ? "_APPROVE" : "_REJECT"), TARGET_TYPE, requestId,
        "actor=" + username + ", status=" + request.getStatus()
            + ", reason=" + (reason != null ? reason : ""));
  }

  /**
   * Hủy đơn (chủ đơn): DRAFT/PENDING → CANCELLED trực tiếp;
   * APPROVED → PENDING_CANCEL (chờ leader duyệt yêu cầu hủy).
   */
  @Transactional
  public void cancel(UUID requestId, String username, String reason) {
    OTRequest request = findById(requestId);
    if (!request.getEmployeeCode().equals(username)) {
      throw new AccessDeniedException("Bạn không phải người tạo đơn này");
    }
    switch (request.getStatus()) {
      case DRAFT, PENDING -> {
        request.setStatus(OTRequestStatus.CANCELLED);
        request.setCancellationReason(reason);
        request.setCancelledAt(LocalDateTime.now());
      }
      case APPROVED -> {
        request.setStatus(OTRequestStatus.PENDING_CANCEL);
        // Giữ nguyên approverId — người duyệt gốc sẽ duyệt yêu cầu hủy
        request.setCancellationReason(reason);
      }
      default -> throw new BusinessRuleException(
          "Không thể hủy đơn ở trạng thái " + request.getStatus());
    }
    otRequestRepository.save(request);
    auditService.success(ACTION + "_CANCEL", TARGET_TYPE, requestId,
        "actor=" + username + ", status=" + request.getStatus());
  }

  /**
   * Xóa đơn (chỉ trạng thái DRAFT/REJECTED/CANCELLED/CANCELLED_APPROVED):
   * người tạo hoặc ADMIN/GLOBAL_LEAD. DB cascade xóa items.
   */
  @Transactional
  public void delete(UUID requestId, String username) {
    OTRequest request = findById(requestId);
    boolean isCreator = request.getEmployeeCode().equals(username);
    if (!isCreator && !isAdminOrGlobalLead()) {
      throw new AccessDeniedException("Bạn không có quyền xóa đơn OT này");
    }
    switch (request.getStatus()) {
      case DRAFT, REJECTED, CANCELLED, CANCELLED_APPROVED -> { /* hợp lệ */ }
      default -> throw new BusinessRuleException(
          "Chỉ xóa được đơn ở trạng thái Nháp, Bị từ chối hoặc Đã hủy");
    }
    otRequestRepository.delete(request);

    auditService.success(ACTION + "_DELETE", TARGET_TYPE, requestId,
        "actor=" + username + ", status=" + request.getStatus());
  }

  /** Cập nhật đơn OT (chỉ DRAFT/PENDING của người tạo). */
  @Transactional
  public OTRequestDTO update(UUID requestId, String username, OTRequestCreateRequest request) {
    OTRequest otRequest = findById(requestId);
    if (!otRequest.getEmployeeCode().equals(username)) {
      throw new AccessDeniedException("Bạn không phải người tạo đơn này");
    }
    if (otRequest.getStatus() != OTRequestStatus.DRAFT
        && otRequest.getStatus() != OTRequestStatus.PENDING) {
      throw new BusinessRuleException("Chỉ đơn ở trạng thái Nháp hoặc Chờ duyệt mới được sửa");
    }

    List<OTRequestItemCreateRequest> itemRequests = expandDateRange(request);
    if (itemRequests.isEmpty()) {
      throw new BusinessRuleException("Vui lòng cung cấp lý do và ít nhất 1 ngày OT");
    }

    OTConfig config = otConfigRepository.findActive()
        .orElseThrow(() -> new BusinessRuleException("Chưa có cấu hình OT. Vui lòng liên hệ quản lý."));

    Set<LocalDate> seenDates = new HashSet<>();
    PeriodEngine.Period currentPeriod = currentWorkPeriod();
    BigDecimal totalHours = BigDecimal.ZERO;
    List<OTRequestItemDTO> itemDTOs = new ArrayList<>();

    for (OTRequestItemCreateRequest itemReq : itemRequests) {
      validateItemInterval(itemReq);

      LocalDate date = itemReq.getStartTime().toLocalDate();
      if (!seenDates.add(date)) {
        throw new BusinessRuleException("Trùng ngày OT trong đơn: " + date);
      }
      validateDateInCurrentPeriod(date, currentPeriod);

      DayType dayType = determineDayType(date);
      BigDecimal[] hours = calculator.computeItemHours(
          itemReq.getStartTime(), itemReq.getEndTime(), config);
      BigDecimal dayHours = hours[0];
      BigDecimal nightHours = hours[1];
      BigDecimal totalItemHours = dayHours.add(nightHours);

      itemDTOs.add(new OTRequestItemDTO(
          null, requestId, date, itemReq.getStartTime(), itemReq.getEndTime(), dayType,
          dayHours, nightHours, null, null, totalItemHours, null));
      totalHours = totalHours.add(totalItemHours);
    }

    totalHours = totalHours.setScale(2, BigDecimal.ROUND_HALF_UP);

    // Xóa items cũ rồi FLUSH ngay — nếu không, Hibernate xếp INSERT trước DELETE khi flush
    // → vi phạm unique (request_id, ot_date) dù dữ liệu cuối cùng hợp lệ
    otRequestItemRepository.deleteByRequestId(requestId);
    otRequestItemRepository.flush();

    for (OTRequestItemDTO dto : itemDTOs) {
      OTRequestItem item = new OTRequestItem();
      item.setRequest(otRequest);
      item.setOtDate(dto.otDate());
      item.setStartTime(dto.startTime());
      item.setEndTime(dto.endTime());
      item.setDayType(dto.dayType());
      item.setDayHours(dto.dayHours());
      item.setNightHours(dto.nightHours());
      item.setApprovedHours(dto.approvedHours());
      otRequestItemRepository.save(item);
    }

    otRequest.setReason(request.getReason());
    otRequest.setTotalHours(totalHours);
    otRequestRepository.save(otRequest);

    auditService.success(ACTION + "_UPDATE", TARGET_TYPE, requestId,
        "employee=" + username + ", hours=" + totalHours + ", items=" + itemDTOs.size());

    return toDTO(otRequest);
  }

  /**
   * List đơn OT theo scope của người dùng.
   * - ADMIN/GLOBAL_LEAD: tất cả.
   * - DEPT_LEAD: self ∪ trực thuộc ∪ subtree phòng ban mình quản lý.
   * - MANAGER/EMPLOYEE: self ∪ trực thuộc.
   */
  @Transactional(readOnly = true)
  public PageResponse<OTRequestDTO> list(int page, int size, String status, String username) {
    EmployeeScope scope = EmployeeScope.fromAuthorities(
        SecurityContextHolder.getContext().getAuthentication().getAuthorities());

    Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
        Sort.by("createdAt").descending());

    OTRequestStatus statusFilter = parseStatus(status);
    Set<String> accessibleEmployeeIds = resolveAccessibleEmployeeIds(username, scope);

    Page<OTRequest> pageResult = otRequestRepository.findByEmployeeIds(
        accessibleEmployeeIds, statusFilter, pageable);

    List<OTRequest> requests = pageResult.getContent();
    Map<UUID, List<OTRequestItemDTO>> itemsByRequest = loadItemsByRequest(requests);
    Map<String, Employee> approverByRef = loadApproversByRef(requests);

    List<OTRequestDTO> dtos = requests.stream()
        .map(r -> toDTO(r, itemsByRequest.getOrDefault(r.getId(), List.of()), approverByRef))
        .toList();

    return PageResponse.of(dtos, pageResult.getNumber(), pageResult.getSize(),
        pageResult.getTotalElements());
  }

  /**
   * Thông tin người duyệt trực tiếp (manager) của người gọi — FE hiển thị thẻ approver.
   * Trả về null (200 rỗng) khi không có manager.
   */
  @Transactional(readOnly = true)
  public OTApproverInfo findApproverInfo(String username) {
    Employee self = employeeRepository.findByEmployeeCode(username).orElse(null);
    if (self == null || self.getManagerId() == null || self.getManagerId().isBlank()) {
      return null;
    }
    Employee manager = employeeRepository.findById(self.getManagerId()).orElse(null);
    if (manager == null) {
      return null;
    }
    return new OTApproverInfo(manager.getFullName(), manager.getEmployeeCode(),
        manager.getPosition());
  }

  // ---------- Private helper methods ----------

  private OTRequest findById(UUID id) {
    return otRequestRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("OTRequest", id));
  }

  private static OTRequestStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return OTRequestStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BusinessRuleException("Trạng thái không hợp lệ: " + status);
    }
  }

  private static boolean isAdminOrGlobalLead() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().stream().anyMatch(a ->
        a.getAuthority().equals("ROLE_" + Roles.ADMIN)
            || a.getAuthority().equals("ROLE_" + Roles.GLOBAL_LEAD));
  }

  private DayType determineDayType(LocalDate date) {
    // Ngày lễ (kiểm tra toàn bộ DB, bất kể calendarCode)
    if (holidayService.isHoliday(date)) {
      return DayType.HOLIDAY;
    }
    DayOfWeek dow = date.getDayOfWeek();
    if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
      return DayType.WEEKEND;
    }
    return DayType.WEEKDAY;
  }

  private void validateItemInterval(OTRequestItemCreateRequest item) {
    if (item.getStartTime() == null || item.getEndTime() == null) {
      throw new BusinessRuleException("Thời gian bắt đầu/kết thúc không được để trống");
    }
    if (!item.getStartTime().isBefore(item.getEndTime())) {
      throw new BusinessRuleException("Thời gian bắt đầu phải trước thời gian kết thúc");
    }
    if (ChronoUnit.MINUTES.between(item.getStartTime(), item.getEndTime()) > 24 * 60L) {
      throw new BusinessRuleException("Một đơn OT không được vượt quá 24 giờ");
    }
  }

  /** Ngày OT chỉ được nằm trong kỳ công hiện tại (quá khứ trong kỳ vẫn cho phép). */
  private void validateDateInCurrentPeriod(LocalDate date, PeriodEngine.Period period) {
    if (date.isBefore(period.start()) || date.isAfter(period.end())) {
      throw new BusinessRuleException(
          "Ngày OT " + date + " phải thuộc kỳ công hiện tại ("
              + period.start() + " – " + period.end() + ")");
    }
  }

  /** Kỳ công chứa hôm nay (theo leave settings). */
  private PeriodEngine.Period currentWorkPeriod() {
    LeaveSettings settings = leaveSettingsService.getOrCreateSettings();
    return PeriodEngine.periodOf(settings, LocalDate.now());
  }

  /**
   * Quyền approve/reject: ADMIN/GLOBAL_LEAD toàn quyền; còn lại phải thuộc 1 trong:
   * (a) approver được ghi trên đơn, (b) manager trực tiếp của người tạo,
   * (c) DEPT_LEAD có scope phủ người tạo. Không bao giờ tự duyệt đơn của mình.
   */
  private void assertCanApprove(OTRequest request, String username) {
    if (isAdminOrGlobalLead()) {
      return;
    }

    Employee approverEmp = employeeRepository.findByEmployeeCode(username).orElse(null);
    Employee creatorEmp = employeeRepository.findByEmployeeCode(request.getEmployeeCode())
        .orElse(null);
    if (approverEmp == null || creatorEmp == null) {
      throw new AccessDeniedException("Không tìm thấy hồ sơ nhân sự để xác thực quyền duyệt");
    }

    if (approverEmp.getEmployeeId().equals(creatorEmp.getEmployeeId())) {
      throw new AccessDeniedException("Bạn không thể duyệt đơn OT của chính mình");
    }

    // approverId lưu employeeCode (dữ liệu cũ có thể là employeeId)
    String approverId = request.getApproverId();
    if (approverId != null
        && (approverId.equals(username) || approverId.equals(approverEmp.getEmployeeId()))) {
      return;
    }

    // Manager trực tiếp của người tạo
    if (approverEmp.getEmployeeId().equals(creatorEmp.getManagerId())) {
      return;
    }

    // DEPT_LEAD: người tạo nằm trong subtree phòng mình quản lý
    EmployeeScope scope = EmployeeScope.fromAuthorities(
        SecurityContextHolder.getContext().getAuthentication().getAuthorities());
    if (scope == EmployeeScope.DEPARTMENT) {
      EmployeeAccess access = employeeAccessResolver.resolve(username, scope);
      if (access.unrestricted() || access.canAccessSafe(creatorEmp)) {
        return;
      }
    }

    throw new AccessDeniedException("Bạn không có quyền duyệt đơn OT của nhân viên này");
  }

  /**
   * Scope đọc đơn OT:
   * - ALL (ADMIN/GLOBAL_LEAD): null = không filter.
   * - SELF (MANAGER/EMPLOYEE): self ∪ trực thuộc (reports).
   * - DEPARTMENT (DEPT_LEAD): self ∪ trực thuộc ∪ subtree phòng ban quản lý.
   */
  private Set<String> resolveAccessibleEmployeeIds(String username, EmployeeScope scope) {
    if (scope == EmployeeScope.ALL) {
      return null;
    }

    Employee self = employeeRepository.findByEmployeeCode(username).orElse(null);
    if (self == null) {
      return Set.of(); // fail-closed
    }

    Set<String> ids = new HashSet<>();
    ids.add(self.getEmployeeId());
    ids.addAll(employeeRepository.findEmployeeIdsByManagerId(self.getEmployeeId()));

    if (scope == EmployeeScope.DEPARTMENT) {
      Set<String> subtreeDepts = departmentService.managedSubtreeIds(self.getEmployeeId());
      if (subtreeDepts != null && !subtreeDepts.isEmpty()) {
        ids.addAll(employeeRepository.findEmployeeIdsByDepartmentIdIn(subtreeDepts));
      }
    }

    return ids;
  }

  /** Batch nạp items cho danh sách đơn (tránh N+1). */
  private Map<UUID, List<OTRequestItemDTO>> loadItemsByRequest(List<OTRequest> requests) {
    Map<UUID, List<OTRequestItemDTO>> result = new HashMap<>();
    if (requests.isEmpty()) {
      return result;
    }
    List<UUID> ids = requests.stream().map(OTRequest::getId).toList();
    Map<UUID, List<OTRequestItem>> grouped = otRequestItemRepository.findByRequestIdIn(ids)
        .stream()
        .collect(Collectors.groupingBy(i -> i.getRequest().getId()));
    grouped.forEach((requestId, items) ->
        result.put(requestId, items.stream().map(OTRequestItemDTO::from).toList()));
    return result;
  }

  /** Batch nạp người duyệt theo employeeCode/employeeId (tránh N+1). */
  private Map<String, Employee> loadApproversByRef(List<OTRequest> requests) {
    Map<String, Employee> byRef = new HashMap<>();
    Set<String> refs = requests.stream()
        .map(OTRequest::getApproverId)
        .filter(r -> r != null && !r.isBlank())
        .collect(Collectors.toSet());
    if (refs.isEmpty()) {
      return byRef;
    }
    employeeRepository.findByEmployeeCodeIn(refs)
        .forEach(e -> byRef.put(e.getEmployeeCode(), e));
    employeeRepository.findAllById(refs).forEach(e -> {
      byRef.putIfAbsent(e.getEmployeeId(), e);
      byRef.putIfAbsent(e.getEmployeeCode(), e);
    });
    return byRef;
  }

  private OTRequestDTO toDTO(OTRequest r) {
    List<OTRequestItemDTO> items = otRequestItemRepository.findByRequestId(r.getId()).stream()
        .map(OTRequestItemDTO::from).toList();
    Map<String, Employee> approverByRef = loadApproversByRef(List.of(r));
    return toDTO(r, items, approverByRef);
  }

  private OTRequestDTO toDTO(OTRequest r, List<OTRequestItemDTO> items,
                             Map<String, Employee> approverByRef) {
    Employee approver = r.getApproverId() != null && !r.getApproverId().isBlank()
        ? approverByRef.get(r.getApproverId())
        : null;
    String approverName = approver != null ? approver.getFullName() : null;
    String approverEmployeeCode = approver != null ? approver.getEmployeeCode() : null;
    return OTRequestDTO.from(r, items, approverName, approverEmployeeCode);
  }

  private UUID stringToUuid(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Nếu request có dateRange nhưng items rỗng → sinh 1 item cho từng ngày từ startDate→endDate,
   * dùng defaultStartTime/defaultEndTime. Nếu items đã có → trả về nguyên.
   */
  private List<OTRequestItemCreateRequest> expandDateRange(OTRequestCreateRequest request) {
    if (request.getItems() != null && !request.getItems().isEmpty()) {
      return request.getItems();
    }
    if (request.getStartDate() == null || request.getEndDate() == null) {
      return java.util.Collections.emptyList();
    }
    if (request.getDefaultStartTime() == null || request.getDefaultEndTime() == null) {
      throw new BusinessRuleException("Phải cung cấp defaultStartTime/defaultEndTime khi dùng dateRange");
    }
    if (request.getStartDate().isAfter(request.getEndDate())) {
      throw new BusinessRuleException("startDate phải nhỏ hơn hoặc bằng endDate");
    }

    var timePart = request.getDefaultStartTime().toLocalTime();
    var endTimePart = request.getDefaultEndTime().toLocalTime();

    java.util.List<OTRequestItemCreateRequest> items = new java.util.ArrayList<>();
    for (LocalDate d = request.getStartDate(); !d.isAfter(request.getEndDate()); d = d.plusDays(1)) {
      LocalDateTime start = LocalDateTime.of(d, timePart);
      LocalDateTime end = LocalDateTime.of(d, endTimePart);
      // Nếu defaultEndTime < defaultStartTime (qua đêm) → end + 1 ngày
      if (end.isBefore(start)) {
        end = end.plusDays(1);
      }
      items.add(new OTRequestItemCreateRequest(start, end));
    }
    return items;
  }

  /**
   * Sinh mã đơn theo ngày: OT + YYYYMMDD + sequence (số đơn tạo trong ngày).
   * Retry khi va chạm unique request_code.
   */
  private String generateRequestCode() {
    LocalDate today = LocalDate.now();
    String datePart = today.format(DateTimeFormatter.BASIC_ISO_DATE);
    java.time.Instant dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
    long seq = otRequestRepository.countByCreatedAtAfter(dayStart);
    for (int i = 0; i < 20; i++) {
      String code = "OT" + datePart + String.format("%03d", (seq + i) % 1000);
      if (!otRequestRepository.existsByRequestCode(code)) {
        return code;
      }
    }
    // Fallback hiếm gặp: thêm hậu tố ngẫu nhiên (vẫn ≤ 30 ký tự)
    return "OT" + datePart + String.format("%03d", seq % 1000)
        + "R" + String.format("%03d", System.nanoTime() % 1000);
  }
}
