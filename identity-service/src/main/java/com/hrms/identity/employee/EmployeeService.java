package com.hrms.identity.employee;

import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.kafka.EmployeeEventTypes;
import com.hrms.common.kafka.KafkaTopics;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxPayloadConverter;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.common.security.EmployeeScope;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.department.Department;
import com.hrms.identity.department.DepartmentRepository;
import com.hrms.identity.department.DepartmentStatus;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
import com.hrms.identity.event.EmployeeEventPayload;
import com.hrms.identity.history.JobHistoryService;
import com.hrms.identity.holiday.HolidayCalendarRepository;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import com.hrms.identity.keycloak.KeycloakSyncService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD nhân viên. Mỗi thao tác ghi business data + outbox trong CÙNG transaction
 * ({@code @Transactional}); {@code OutboxPublisher} poll và publish sang Kafka.
 * Thay đổi job info (phòng ban/vị trí/quản lý/trạng thái) được ghi vào
 * {@code employee_job_history} (model theo SAP SuccessFactors EC).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

  private static final String AGGREGATE_TYPE = "Employee";

  private final EmployeeRepository employeeRepository;
  private final OutboxRepository outboxRepository;
  private final EmployeeMapper mapper;
  private final DepartmentRepository departmentRepository;
  private final JobHistoryService jobHistoryService;
  private final KeycloakAdminClient keycloakAdminClient;
  private final KeycloakSyncService keycloakSyncService;
  private final HolidayCalendarRepository holidayCalendarRepository;
  private final RoleAssignmentService roleAssignmentService;
  private final AuditService auditService;

  @Transactional
  public EmployeeResponse create(EmployeeCreateRequest request, String actorId) {
    String employeeCode = resolveEmployeeCode(request.employeeCode(), request.fullName());
    Employee employee = mapper.toEntity(request);
    employee.setEmployeeCode(employeeCode);
    employee.setStatus(EmployeeStatus.ACTIVE);
    applyDepartment(employee, request.departmentId());
    validateGender(employee.getGender());
    validateManagerRef(employee.getManagerId(), null);
    employeeRepository.save(employee);
    // Mã NV = username đăng nhập. Task đồng bộ Keycloak được ghi CÙNG transaction; chỉ gọi
    // Keycloak sau khi commit (P0-6) — rollback DB sẽ rollback luôn task, không tạo user mồ côi.
    String defaultRole = roleAssignmentService.resolveDefaultRole(employee.getId());
    keycloakSyncService.enqueueCreate(employeeCode, employee.getFullName(), employee.getEmail(),
        defaultRole);
    writeOutbox(employee, EmployeeEventTypes.CREATED, actorId, null, null, null,
        List.of(defaultRole));
    auditService.success("EMPLOYEE_CREATE", "Employee", employee.getId(),
        "employeeCode=" + employeeCode);
    return mapper.toResponse(employee);
  }

  /**
   * Mã nhân viên = username đăng nhập. Nếu client không truyền (hoặc trắng) thì sinh từ
   * họ tên ({@link EmployeeCodeGenerator}) và cộng hậu tố 1,2,... tới khi trùng DB lẫn
   * Keycloak. Có sẵn mã thì giữ nguyên (FE gợi ý nhưng HR sửa được) + kiểm tra trùng DB.
   */
  private String resolveEmployeeCode(String requestedCode, String fullName) {
    if (!isBlank(requestedCode)) {
      String code = requestedCode.trim();
      if (employeeRepository.existsByEmployeeCode(code)) {
        throw new ConflictException("Mã nhân viên đã tồn tại: " + code);
      }
      // Giữ hành vi cũ: mã do HR nhập trùng username Keycloak → 409 ngay, không để task
      // đồng bộ (async) vô tình gán role cho tài khoản có sẵn. Đây là read-only, không ghi KC.
      if (keycloakAdminClient.userExists(code)) {
        throw new ConflictException("Tên đăng nhập đã tồn tại trong Keycloak: " + code);
      }
      return code;
    }
    String base = EmployeeCodeGenerator.baseFrom(fullName);
    String candidate = base;
    if (isAvailable(candidate)) {
      return candidate;
    }
    for (int attempt = 1; attempt < 1000; attempt++) {
      candidate = EmployeeCodeGenerator.withSuffix(base, attempt);
      if (isAvailable(candidate)) {
        return candidate;
      }
    }
    throw new BusinessRuleException("Không sinh được mã nhân viên duy nhất cho: " + fullName);
  }

  private boolean isAvailable(String code) {
    return !employeeRepository.existsByEmployeeCode(code) && !keycloakAdminClient.userExists(code);
  }

  /**
   * Danh sách phân trang + tìm kiếm/tìm lọc theo {@link EmployeeScope}:
   * ALL (ADMIN/GLOBAL_LEAD) toàn bộ, không mask; DEPARTMENT (DEPT_LEAD) nhân viên các
   * phòng mình quản lý (gồm nhánh con) + trực thuộc + self, PII đầy đủ; SELF (MANAGER/
   * EMPLOYEE) trực thuộc + self, PII nhạy cảm bị mask. Không khớp hồ sơ → danh sách rỗng
   * (fail-closed).
   */
  @Transactional(readOnly = true)
  public PageResponse<EmployeeResponse> search(int page, int size, EmployeeStatus status,
      UUID departmentId, String position, String office, String q, EmployeeViewer viewer) {
    String query = (q == null || q.isBlank()) ? "" : q.trim().toLowerCase(Locale.ROOT);
    String pos = (position == null || position.isBlank()) ? "" : position.trim();
    String off = (office == null || office.isBlank()) ? "" : office.trim();
    PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

    if (!viewer.unrestricted()) {
      Optional<Employee> self = resolveViewer(viewer);
      if (self.isEmpty()) {
        return PageResponse.of(List.of(), page, size, 0);
      }
      UUID selfId = self.get().getId();
      List<UUID> deptScope = departmentScope(viewer, selfId);
      List<EmployeeResponse> items = employeeRepository
          .search(status, departmentId, pos, off, query, selfId, deptScope, selfId, pageable)
          .stream()
          .map(employee -> toViewerResponse(employee, selfId, viewer))
          .toList();
      long total = employeeRepository.countSearch(
          status, departmentId, pos, off, query, selfId, deptScope, selfId);
      return PageResponse.of(items, page, size, total);
    }

    List<EmployeeResponse> items = employeeRepository
        .search(status, departmentId, pos, off, query, null, null, null, pageable).stream()
        .map(mapper::toResponse)
        .toList();
    long total = employeeRepository.countSearch(status, departmentId, pos, off, query, null, null,
        null);
    return PageResponse.of(items, page, size, total);
  }

  /**
   * Chi tiết nhân viên theo scope: ALL đầy đủ; DEPARTMENT đầy đủ nếu trong phạm vi phòng
   * ban/trực thuộc (self → 403 — lead không tự sửa/xem qua self để mở rộng quyền);
   * SELF chỉ chính mình (đầy đủ) hoặc trực thuộc (mask); ngoài phạm vi → 403.
   */
  @Transactional(readOnly = true)
  public EmployeeResponse get(UUID id, EmployeeViewer viewer) {
    Employee employee = find(id);
    if (viewer.unrestricted()) {
      return mapper.toResponse(employee);
    }
    Optional<Employee> self = resolveViewer(viewer);
    if (self.isPresent() && self.get().getId().equals(id)) {
      return mapper.toResponse(employee);
    }
    if (self.isEmpty()) {
      throw new AccessDeniedException("Bạn không có quyền xem nhân viên này");
    }
    UUID selfId = self.get().getId();
    boolean inReports = Objects.equals(employee.getManagerId(), selfId);
    if (viewer.scope() == EmployeeScope.DEPARTMENT && inDepartmentScope(self.get(), employee)) {
      return mapper.toResponse(employee);
    }
    if (!inReports) {
      throw new AccessDeniedException("Bạn không có quyền xem nhân viên này");
    }
    return maskSensitive(mapper.toResponse(employee));
  }

  /**
   * Nhân viên hiện tại. Auto-provision users: KC username = employeeCode → match trước;
   * fallback theo email claim (user import realm không có mã NV trùng username).
   */
  @Transactional(readOnly = true)
  public EmployeeResponse me(String preferredUsername, String email) {
    Employee found = resolveViewer(
            new EmployeeViewer(preferredUsername, email, EmployeeScope.SELF))
        .orElseThrow(() -> new ResourceNotFoundException(
            "Nhân viên (username/email " + preferredUsername + ")"));
    return mapper.toResponse(found);
  }

  /** Khớp user đăng nhập với hồ sơ NV: username = employeeCode trước, fallback email. */
  private Optional<Employee> resolveViewer(EmployeeViewer viewer) {
    if (!isBlank(viewer.username())) {
      Optional<Employee> byCode = employeeRepository.findByEmployeeCode(viewer.username());
      if (byCode.isPresent()) {
        return byCode;
      }
    }
    return isBlank(viewer.email())
        ? Optional.empty()
        : employeeRepository.findFirstByEmail(viewer.email());
  }

  /** Id các phòng ACTIVE mà self quản lý — null nếu không phải DEPT_LEAD (fail-closed). */
  private List<UUID> departmentScope(EmployeeViewer viewer, UUID selfId) {
    if (viewer.scope() != EmployeeScope.DEPARTMENT) {
      return null;
    }
    List<UUID> deptIds = departmentRepository.findIdsByManager_IdAndStatus(selfId,
        DepartmentStatus.ACTIVE);
    return deptIds.isEmpty() ? null : deptIds;
  }

  /** true nếu target nằm trong scope DEPARTMENT của self (phòng self quản lý hoặc reports). */
  private boolean inDepartmentScope(Employee self, Employee target) {
    if (Objects.equals(target.getManagerId(), self.getId())) {
      return true;
    }
    if (target.getDepartmentId() == null) {
      return false;
    }
    return departmentRepository.findIdsByManager_IdAndStatus(self.getId(),
        DepartmentStatus.ACTIVE).contains(target.getDepartmentId());
  }

  /**
   * Chặn ghi ngoài scope: ALL ghi được mọi employee; DEPARTMENT ghi được nhân viên trong
   * phòng mình quản lý (gồm nhánh con) hoặc trực thuộc — không ghi self (lead không tự
   * sửa để tránh mở rộng quyền); SELF (MANAGER/EMPLOYEE) không có quyền ghi.
   */
  private void assertWriteScope(EmployeeViewer viewer, Employee employee) {
    if (viewer.unrestricted()) {
      return;
    }
    if (viewer.scope() == EmployeeScope.DEPARTMENT) {
      Optional<Employee> self = resolveViewer(viewer);
      if (self.isPresent()
          && !self.get().getId().equals(employee.getId())
          && inDepartmentScope(self.get(), employee)) {
        return;
      }
    }
    throw new AccessDeniedException("Bạn không có quyền sửa nhân viên này");
  }

  /** Response trong danh sách: self đầy đủ; mask PII người khác khi scope SELF. */
  private EmployeeResponse toViewerResponse(Employee employee, UUID viewerEmployeeId,
      EmployeeViewer viewer) {
    EmployeeResponse response = mapper.toResponse(employee);
    if (!viewer.maskOthers() || Objects.equals(employee.getId(), viewerEmployeeId)) {
      return response;
    }
    return maskSensitive(response);
  }

  /**
   * Mask PII nhạy cảm cho người xem không phải ADMIN/GLOBAL_LEAD/DEPT_LEAD/chính chủ:
   * {@code nationalId} → {@code ****} + 4 số cuối; {@code address},
   * {@code emergencyContactPhone} → {@code ***}.
   * {@code phone} giữ nguyên (quan hệ công việc với nhân viên trực thuộc).
   */
  private EmployeeResponse maskSensitive(EmployeeResponse r) {
    return new EmployeeResponse(
        r.id(), r.employeeCode(), r.fullName(), r.email(), r.departmentId(), r.departmentName(),
        r.position(), r.managerId(), r.status(), r.hiredAt(), r.birthDate(), r.gender(), r.phone(),
        maskLast4(r.nationalId()), maskText(r.address()), r.office(), r.educationLevel(),
        r.skills(), r.emergencyContactName(), maskText(r.emergencyContactPhone()),
        r.emergencyContactRelation(), r.photoUrl(), r.calendarId(), r.createdAt(), r.updatedAt(),
        r.version());
  }

  private static String maskLast4(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
  }

  private static String maskText(String value) {
    return (value == null || value.isBlank()) ? value : "***";
  }

  @Transactional
  public EmployeeResponse update(UUID id, EmployeeUpdateRequest patch, String actorId,
      EmployeeViewer viewer) {
    if (isNothingToPatch(patch)) {
      throw new BusinessRuleException("Không có trường nào để cập nhật");
    }
    if (EmployeeStatus.TERMINATED.name().equals(patch.status())) {
      throw new BusinessRuleException(
          "Không thể nghỉ việc qua PATCH — dùng DELETE /api/v1/employees/{id}");
    }

    Employee employee = find(id);
    assertWriteScope(viewer, employee);
    List<String> changed = new ArrayList<>();

    if (!isBlank(patch.fullName()) && !Objects.equals(patch.fullName(), employee.getFullName())) {
      employee.setFullName(patch.fullName());
      changed.add("fullName");
    }
    if (!isBlank(patch.email()) && !Objects.equals(patch.email(), employee.getEmail())) {
      employee.setEmail(patch.email());
      changed.add("email");
    }

    // ---- Phòng ban (job info → history) ----
    if (Boolean.TRUE.equals(patch.clearDepartment())) {
      if (employee.getDepartmentId() != null) {
        String oldName = employee.getDepartmentName();
        employee.setDepartmentId(null);
        employee.setDepartmentName(null);
        changed.add("departmentId");
        jobHistoryService.record(id, "department", oldName, null, actorId);
      }
    } else if (patch.departmentId() != null
        && !Objects.equals(patch.departmentId(), employee.getDepartmentId())) {
      String oldName = employee.getDepartmentName();
      applyDepartment(employee, patch.departmentId());
      changed.add("departmentId");
      jobHistoryService.record(id, "department", oldName, employee.getDepartmentName(), actorId);
    }

    if (!isBlank(patch.position())
        && !Objects.equals(patch.position(), employee.getPosition())) {
      jobHistoryService.record(id, "position", employee.getPosition(), patch.position(), actorId);
      employee.setPosition(patch.position());
      changed.add("position");
    }

    // ---- Quản lý trực tiếp (job info → history) ----
    if (Boolean.TRUE.equals(patch.clearManager())) {
      if (employee.getManagerId() != null) {
        employee.setManagerId(null);
        changed.add("managerId");
        jobHistoryService.record(id, "manager", null, null, actorId);
      }
    } else if (patch.managerId() != null
        && !Objects.equals(patch.managerId(), employee.getManagerId())) {
      validateManagerRef(patch.managerId(), id);
      jobHistoryService.record(id, "manager",
          employee.getManagerId() == null ? null : employee.getManagerId().toString(),
          patch.managerId().toString(), actorId);
      employee.setManagerId(patch.managerId());
      changed.add("managerId");
    }

    if (!isBlank(patch.status())) {
      EmployeeStatus newStatus;
      try {
        newStatus = EmployeeStatus.valueOf(patch.status());
      } catch (IllegalArgumentException ex) {
        throw new BusinessRuleException("Trạng thái không hợp lệ: " + patch.status());
      }
      if (newStatus != employee.getStatus()) {
        jobHistoryService.record(id, "status", employee.getStatus().name(), newStatus.name(),
            actorId);
        employee.setStatus(newStatus);
        changed.add("status");
      }
    }

    // ---- Personal info (không ghi job history) ----
    if (patch.birthDate() != null && !Objects.equals(patch.birthDate(), employee.getBirthDate())) {
      employee.setBirthDate(patch.birthDate());
      changed.add("birthDate");
    }
    if (!isBlank(patch.gender())) {
      validateGender(patch.gender());
      String normalized = patch.gender().trim().toUpperCase(Locale.ROOT);
      if (!Objects.equals(normalized, employee.getGender())) {
        employee.setGender(normalized);
        changed.add("gender");
      }
    }
    if (patch.phone() != null && !Objects.equals(patch.phone(), employee.getPhone())) {
      employee.setPhone(patch.phone());
      changed.add("phone");
    }
    if (patch.nationalId() != null
        && !Objects.equals(patch.nationalId(), employee.getNationalId())) {
      employee.setNationalId(patch.nationalId());
      changed.add("nationalId");
    }
    if (patch.address() != null && !Objects.equals(patch.address(), employee.getAddress())) {
      employee.setAddress(patch.address());
      changed.add("address");
    }
    if (patch.office() != null && !Objects.equals(patch.office(), employee.getOffice())) {
      employee.setOffice(patch.office());
      changed.add("office");
    }

    // ---- Thông tin bổ sung (không ghi job history) ----
    if (patch.educationLevel() != null
        && !Objects.equals(patch.educationLevel(), employee.getEducationLevel())) {
      employee.setEducationLevel(patch.educationLevel());
      changed.add("educationLevel");
    }
    if (patch.skills() != null && !Objects.equals(patch.skills(), employee.getSkills())) {
      employee.setSkills(patch.skills());
      changed.add("skills");
    }
    if (patch.emergencyContactName() != null
        && !Objects.equals(patch.emergencyContactName(), employee.getEmergencyContactName())) {
      employee.setEmergencyContactName(patch.emergencyContactName());
      changed.add("emergencyContactName");
    }
    if (patch.emergencyContactPhone() != null
        && !Objects.equals(patch.emergencyContactPhone(), employee.getEmergencyContactPhone())) {
      employee.setEmergencyContactPhone(patch.emergencyContactPhone());
      changed.add("emergencyContactPhone");
    }
    if (patch.emergencyContactRelation() != null
        && !Objects.equals(patch.emergencyContactRelation(),
            employee.getEmergencyContactRelation())) {
      employee.setEmergencyContactRelation(patch.emergencyContactRelation());
      changed.add("emergencyContactRelation");
    }

    if (!changed.isEmpty()) {
      employeeRepository.save(employee);
      writeOutbox(employee, EmployeeEventTypes.UPDATED, actorId, changed, null, null,
          readRolesSafe(employee.getEmployeeCode()));
      auditService.success("EMPLOYEE_UPDATE", "Employee", id, "changed=" + changed);
    }
    return mapper.toResponse(employee);
  }

  /** Nghỉ việc (soft delete): chỉ đổi status, không xóa record — giữ cho payroll/tracing. */
  @Transactional
  public void terminate(UUID id, String actorId, String reason, EmployeeViewer viewer) {
    Employee employee = find(id);
    assertWriteScope(viewer, employee);
    if (employee.getStatus() == EmployeeStatus.TERMINATED) {
      throw new BusinessRuleException("Nhân viên đã nghỉ việc: " + employee.getEmployeeCode());
    }
    jobHistoryService.record(id, "status", employee.getStatus().name(),
        EmployeeStatus.TERMINATED.name(), actorId, reason);
    employee.setStatus(EmployeeStatus.TERMINATED);
    employeeRepository.save(employee);
    writeOutbox(employee, EmployeeEventTypes.TERMINATED, actorId, null, reason,
        Instant.now().toEpochMilli(), null);
    // Tắt tài khoản đăng nhập — task trong cùng transaction, xử lý sau commit (P0-6).
    keycloakSyncService.enqueueEnabled(employee.getEmployeeCode(), false);
    auditService.success("EMPLOYEE_TERMINATE", "Employee", id,
        "employeeCode=" + employee.getEmployeeCode());
  }

  /**
   * Gán/bỏ gán lịch nghỉ lễ (V8). calendarId = null → fallback lịch mặc định theo country
   * khi tự query /holidays/my. Ghi job history để truy vết lịch sử thay đổi.
   */
  @Transactional
  public EmployeeResponse assignCalendar(UUID id, UUID calendarId, String actorId,
      EmployeeViewer viewer) {
    Employee employee = find(id);
    assertWriteScope(viewer, employee);
    UUID oldCalendarId = employee.getCalendarId();
    if (Objects.equals(oldCalendarId, calendarId)) {
      return mapper.toResponse(employee);
    }
    String oldCode = calendarCode(oldCalendarId);
    String newCode = calendarCode(calendarId);
    employee.setCalendarId(calendarId);
    employeeRepository.save(employee);
    jobHistoryService.record(id, "calendar", oldCode, newCode, actorId);
    writeOutbox(employee, EmployeeEventTypes.UPDATED, actorId,
        List.of("calendarId"), null, null, readRolesSafe(employee.getEmployeeCode()));
    auditService.success("EMPLOYEE_ASSIGN_CALENDAR", "Employee", id,
        "calendar=" + newCode);
    return mapper.toResponse(employee);
  }

  private String calendarCode(UUID calendarId) {
    if (calendarId == null) {
      return null;
    }
    return holidayCalendarRepository.findById(calendarId)
        .orElseThrow(() -> new ResourceNotFoundException("HolidayCalendar", calendarId))
        .getCode();
  }

  private void applyDepartment(Employee employee, UUID departmentId) {
    if (departmentId == null) {
      employee.setDepartmentId(null);
      employee.setDepartmentName(null);
      return;
    }
    Department department = departmentRepository.findById(departmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
    employee.setDepartmentId(department.getId());
    employee.setDepartmentName(department.getName());
  }

  private void validateManagerRef(UUID managerId, UUID selfId) {
    if (managerId == null) {
      return;
    }
    if (selfId != null && managerId.equals(selfId)) {
      throw new BusinessRuleException("Nhân viên không thể là quản lý của chính mình");
    }
    employeeRepository.findById(managerId)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", managerId));
  }

  private void validateGender(String gender) {
    if (isBlank(gender)) {
      return;
    }
    if (!List.of("MALE", "FEMALE", "OTHER").contains(gender.trim().toUpperCase(Locale.ROOT))) {
      throw new BusinessRuleException("Giới tính chỉ nhận MALE/FEMALE/OTHER: " + gender);
    }
  }

  private boolean isNothingToPatch(EmployeeUpdateRequest patch) {
    return isBlank(patch.fullName())
        && isBlank(patch.email())
        && patch.departmentId() == null
        && !Boolean.TRUE.equals(patch.clearDepartment())
        && isBlank(patch.position())
        && patch.managerId() == null
        && !Boolean.TRUE.equals(patch.clearManager())
        && isBlank(patch.status())
        && patch.birthDate() == null
        && isBlank(patch.gender())
        && patch.phone() == null
        && patch.nationalId() == null
        && patch.address() == null
        && patch.office() == null
        && patch.educationLevel() == null
        && patch.skills() == null
        && patch.emergencyContactName() == null
        && patch.emergencyContactPhone() == null
        && patch.emergencyContactRelation() == null;
  }

  private Employee find(UUID id) {
    return employeeRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
  }

  private void writeOutbox(Employee employee, String eventType, String actorId,
      List<String> changedFields, String reason, Long terminatedAt, List<String> roles) {
    EmployeeEventPayload payload =
        EmployeeEventPayload.from(employee, actorId, changedFields, reason, terminatedAt, roles);
    outboxRepository.save(OutboxEvent.of(
        AGGREGATE_TYPE,
        employee.getId().toString(),
        eventType,
        KafkaTopics.EMPLOYEE_V1,
        OutboxPayloadConverter.toJson(payload)));
  }

  /**
   * Đọc roles từ KC để emit cùng event (attendance mirror employees.roles). Lỗi mạng →
   * null = không đụng roles ở consumer (tránh ghi đè rỗng khi KC flaky).
   */
  private List<String> readRolesSafe(String employeeCode) {
    try {
      return keycloakAdminClient.realmRolesByUsername(employeeCode);
    } catch (Exception ex) {
      log.warn("Không đọc được roles của '{}' để emit event: {}", employeeCode, ex.getMessage());
      return null;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
