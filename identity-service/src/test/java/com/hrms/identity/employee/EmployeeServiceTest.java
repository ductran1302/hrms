package com.hrms.identity.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.hrms.common.security.Roles;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.department.Department;
import com.hrms.identity.department.DepartmentRepository;
import com.hrms.identity.department.DepartmentStatus;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
import com.hrms.identity.event.EmployeeEventPayload;
import com.hrms.identity.history.JobHistoryService;
import com.hrms.identity.holiday.HolidayCalendar;
import com.hrms.identity.holiday.HolidayCalendarRepository;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import com.hrms.identity.keycloak.KeycloakSyncService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private OutboxRepository outboxRepository;

  @Mock
  private EmployeeMapper mapper;

  @Mock
  private DepartmentRepository departmentRepository;

  @Mock
  private JobHistoryService jobHistoryService;

  @Mock
  private KeycloakAdminClient keycloakAdminClient;

  @Mock
  private KeycloakSyncService keycloakSyncService;

  @Mock
  private HolidayCalendarRepository holidayCalendarRepository;

  @Mock
  private RoleAssignmentService roleAssignmentService;

  @Mock
  private AuditService auditService;

  @InjectMocks
  private EmployeeService service;

  private final UUID employeeId = UUID.randomUUID();
  private final String actorId = "admin-user-1";

  @Test
  void create_savesEmployeeAndWritesOutbox() {
    EmployeeCreateRequest request = createReq("NV0001", "Nguyễn Văn A", "a@hrms.vn", null,
        "Backend");
    Employee entity = new Employee();
    entity.setEmployeeCode("NV0001");
    entity.setFullName("Nguyễn Văn A");
    entity.setEmail("a@hrms.vn");

    when(employeeRepository.existsByEmployeeCode("NV0001")).thenReturn(false);
    when(mapper.toEntity(request)).thenReturn(entity);
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
      Employee saved = inv.getArgument(0);
      saved.setId(employeeId);
      return saved;
    });
    when(roleAssignmentService.resolveDefaultRole(employeeId)).thenReturn(Roles.EMPLOYEE);
    when(outboxRepository.save(any(OutboxEvent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.create(request, actorId);

    assertEquals(EmployeeStatus.ACTIVE, entity.getStatus());

    ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(outbox.capture());
    OutboxEvent event = outbox.getValue();
    assertEquals(EmployeeEventTypes.CREATED, event.getEventType());
    assertEquals(KafkaTopics.EMPLOYEE_V1, event.getTopic());
    assertEquals(employeeId.toString(), event.getAggregateId());

    EmployeeEventPayload payload =
        OutboxPayloadConverter.fromJson(event.getPayload(), EmployeeEventPayload.class);
    assertEquals("NV0001", payload.employeeCode());
    assertEquals("ACTIVE", payload.status());
    assertEquals(actorId, payload.actorId());
    verify(keycloakSyncService).enqueueCreate(eq("NV0001"), eq("Nguyễn Văn A"),
        eq("a@hrms.vn"), eq(Roles.EMPLOYEE));
  }

  @Test
  void create_blankCode_generatesFromFullName() {
    EmployeeCreateRequest request = createReq(null, "Nguyễn Trung Hiếu", null, null, null);
    Employee entity = new Employee();
    entity.setFullName("Nguyễn Trung Hiếu");

    when(employeeRepository.existsByEmployeeCode("nthieu")).thenReturn(false);
    when(keycloakAdminClient.userExists("nthieu")).thenReturn(false);
    when(mapper.toEntity(request)).thenReturn(entity);
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
      Employee saved = inv.getArgument(0);
      saved.setId(employeeId);
      return saved;
    });
    when(roleAssignmentService.resolveDefaultRole(employeeId)).thenReturn(Roles.EMPLOYEE);
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    service.create(request, actorId);

    assertEquals("nthieu", entity.getEmployeeCode());
    verify(keycloakSyncService).enqueueCreate(eq("nthieu"), eq("Nguyễn Trung Hiếu"), isNull(),
        eq(Roles.EMPLOYEE));
  }

  @Test
  void create_blankCode_appendsSuffixWhenTaken() {
    EmployeeCreateRequest request = createReq("", "Nguyễn Trung Hiếu", null, null, null);
    Employee entity = new Employee();
    entity.setFullName("Nguyễn Trung Hiếu");

    when(employeeRepository.existsByEmployeeCode("nthieu")).thenReturn(true);
    when(employeeRepository.existsByEmployeeCode("nthieu1")).thenReturn(false);
    when(keycloakAdminClient.userExists("nthieu1")).thenReturn(false);
    when(mapper.toEntity(request)).thenReturn(entity);
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
      Employee saved = inv.getArgument(0);
      saved.setId(employeeId);
      return saved;
    });
    when(roleAssignmentService.resolveDefaultRole(employeeId)).thenReturn(Roles.EMPLOYEE);
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    service.create(request, actorId);

    assertEquals("nthieu1", entity.getEmployeeCode());
    verify(keycloakSyncService).enqueueCreate(eq("nthieu1"), any(), any(), eq(Roles.EMPLOYEE));
  }

  @Test
  void create_keycloakUsernameTaken_throwsConflictAndSkipsOutbox() {
    EmployeeCreateRequest request = createReq("hr", "Hồ Ráng", null, null, null);
    Employee entity = new Employee();
    entity.setFullName("Hồ Ráng");

    when(employeeRepository.existsByEmployeeCode("hr")).thenReturn(false);
    when(keycloakAdminClient.userExists("hr")).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.create(request, actorId));
    verify(outboxRepository, never()).save(any());
    verify(keycloakSyncService, never()).enqueueCreate(any(), any(), any(), any());
  }

  @Test
  void create_duplicateCode_throwsConflict() {
    EmployeeCreateRequest request = createReq("NV0001", "Nguyễn Văn A", null, null, null);
    when(employeeRepository.existsByEmployeeCode("NV0001")).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.create(request, actorId));
    verify(employeeRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void create_unknownDepartment_throwsResourceNotFound() {
    UUID departmentId = UUID.randomUUID();
    EmployeeCreateRequest request = createReq("NV0002", "Test", null, departmentId, null);
    Employee entity = new Employee();
    entity.setEmployeeCode("NV0002");

    when(employeeRepository.existsByEmployeeCode("NV0002")).thenReturn(false);
    when(mapper.toEntity(request)).thenReturn(entity);
    when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> service.create(request, actorId));
    verify(employeeRepository, never()).save(any());
  }

  @Test
  void search_returnsPageResponse() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    EmployeeResponse response = sampleResponse();
    when(employeeRepository.search(eq(EmployeeStatus.ACTIVE), isNull(), eq(""), eq(""), eq(""),
        isNull(), isNull(), isNull(), any())).thenReturn(List.of(employee));
    when(employeeRepository.countSearch(eq(EmployeeStatus.ACTIVE), isNull(), eq(""), eq(""),
        eq(""), isNull(), isNull(), isNull())).thenReturn(1L);
    when(mapper.toResponse(employee)).thenReturn(response);

    PageResponse<EmployeeResponse> result =
        service.search(0, 20, EmployeeStatus.ACTIVE, null, null, null, null, adminViewer());

    assertEquals(1, result.items().size());
    assertEquals(1L, result.total());
    assertEquals("NV0001", result.items().get(0).employeeCode());
  }

  @Test
  void get_notFound_throwsResourceNotFound() {
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> service.get(employeeId, adminViewer()));
  }

  // ---- P1: MANAGER scoping + mask PII ----

  @Test
  void search_manager_scopesToDirectReportsAndMasksPii() {
    Employee self = employeeWithStatus(EmployeeStatus.ACTIVE);
    Employee report = new Employee();
    report.setId(UUID.randomUUID());
    report.setEmployeeCode("NV0002");

    when(employeeRepository.findByEmployeeCode("manager1")).thenReturn(Optional.of(self));
    when(employeeRepository.search(any(), any(), any(), any(), any(), eq(employeeId),
        isNull(), eq(employeeId), any())).thenReturn(List.of(self, report));
    when(employeeRepository.countSearch(any(), any(), any(), any(), any(), eq(employeeId),
        isNull(), eq(employeeId))).thenReturn(2L);
    when(mapper.toResponse(self)).thenReturn(piiResponse("NV0001"));
    when(mapper.toResponse(report)).thenReturn(piiResponse("NV0002"));

    PageResponse<EmployeeResponse> result =
        service.search(0, 20, null, null, null, null, null, managerViewer());

    assertEquals(2, result.items().size());
    assertEquals(2L, result.total());
    // chính mình trong danh sách → đầy đủ
    assertEquals("012345678901", result.items().get(0).nationalId());
    // nhân viên trực thuộc → PII bị mask, trường thường giữ nguyên
    EmployeeResponse masked = result.items().get(1);
    assertEquals("NV0002", masked.employeeCode());
    assertEquals("Nguyễn Văn A", masked.fullName());
    assertEquals("****8901", masked.nationalId());
    assertEquals("***", masked.address());
    assertEquals("***", masked.emergencyContactPhone());
  }

  @Test
  void search_managerWithoutEmployeeRecord_returnsEmptyPageAndSkipsQuery() {
    when(employeeRepository.findByEmployeeCode("manager1")).thenReturn(Optional.empty());
    when(employeeRepository.findFirstByEmail("manager1@hrms.vn")).thenReturn(Optional.empty());

    PageResponse<EmployeeResponse> result =
        service.search(0, 20, null, null, null, null, null, managerViewer());

    assertTrue(result.items().isEmpty());
    assertEquals(0L, result.total());
    // fail-closed: không chạy query khi không xác định được người xem
    verify(employeeRepository, never()).search(any(), any(), any(), any(), any(), any(), any(),
        any(), any());
  }

  @Test
  void get_admin_returnsFullPii() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(mapper.toResponse(employee)).thenReturn(piiResponse("NV0001"));

    EmployeeResponse result = service.get(employeeId, adminViewer());

    assertEquals("012345678901", result.nationalId());
    assertEquals("0900000002", result.emergencyContactPhone());
  }

  @Test
  void get_selfByManager_returnsFullPii() {
    Employee self = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(self));
    when(employeeRepository.findByEmployeeCode("manager1")).thenReturn(Optional.of(self));
    when(mapper.toResponse(self)).thenReturn(piiResponse("NV0001"));

    EmployeeResponse result = service.get(employeeId, managerViewer());

    assertEquals("012345678901", result.nationalId());
    assertEquals("12 Phố Huế, Hà Nội", result.address());
  }

  @Test
  void get_managerViewingDirectReport_masksPii() {
    Employee report = new Employee();
    report.setId(UUID.randomUUID());
    report.setEmployeeCode("NV0002");
    report.setManagerId(employeeId);
    when(employeeRepository.findById(report.getId())).thenReturn(Optional.of(report));
    when(employeeRepository.findByEmployeeCode("manager1"))
        .thenReturn(Optional.of(employeeWithStatus(EmployeeStatus.ACTIVE)));
    when(mapper.toResponse(report)).thenReturn(piiResponse("NV0002"));

    EmployeeResponse result = service.get(report.getId(), managerViewer());

    assertEquals("****8901", result.nationalId());
    assertEquals("***", result.address());
    assertEquals("***", result.emergencyContactPhone());
    // phone + tên người liên hệ giữ cho quan hệ công việc
    assertEquals("0912345678", result.phone());
    assertEquals("Nguyễn Văn B", result.emergencyContactName());
  }

  @Test
  void get_managerOutsideScope_throwsAccessDenied() {
    Employee other = new Employee();
    other.setId(UUID.randomUUID());
    other.setEmployeeCode("NV0003");
    when(employeeRepository.findById(other.getId())).thenReturn(Optional.of(other));
    when(employeeRepository.findByEmployeeCode("manager1"))
        .thenReturn(Optional.of(employeeWithStatus(EmployeeStatus.ACTIVE)));

    assertThrows(AccessDeniedException.class,
        () -> service.get(other.getId(), managerViewer()));
    verify(mapper, never()).toResponse(other);
  }

  // ---- 5-role: DEPT_LEAD scope subtree + MANAGER không có quyền ghi ----

  @Test
  void get_deptLeadViewingEmployeeInManagedDept_returnsFullPii() {
    UUID deptId = UUID.randomUUID();
    Employee lead = employeeWithStatus(EmployeeStatus.ACTIVE);
    Employee member = new Employee();
    member.setId(UUID.randomUUID());
    member.setEmployeeCode("NV0010");
    member.setDepartmentId(deptId);
    when(employeeRepository.findById(member.getId())).thenReturn(Optional.of(member));
    when(employeeRepository.findByEmployeeCode("lead1")).thenReturn(Optional.of(lead));
    when(departmentRepository.findIdsByManager_IdAndStatus(lead.getId(),
        DepartmentStatus.ACTIVE)).thenReturn(List.of(deptId));
    when(mapper.toResponse(member)).thenReturn(piiResponse("NV0010"));

    EmployeeResponse result = service.get(member.getId(), deptLeadViewer());

    assertEquals("012345678901", result.nationalId());
  }

  @Test
  void get_deptLeadViewingSelf_returnsFullPii() {
    Employee lead = employeeWithStatus(EmployeeStatus.ACTIVE);
    lead.setEmployeeCode("lead1");
    when(employeeRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
    when(employeeRepository.findByEmployeeCode("lead1")).thenReturn(Optional.of(lead));
    when(mapper.toResponse(lead)).thenReturn(piiResponse("lead1"));

    EmployeeResponse result = service.get(lead.getId(), deptLeadViewer());

    assertEquals("012345678901", result.nationalId());
  }

  @Test
  void update_managerRole_throwsAccessDenied() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

    EmployeeUpdateRequest patch = updateWithEmail("x@hrms.vn");
    assertThrows(AccessDeniedException.class,
        () -> service.update(employeeId, patch, actorId, managerViewer()));
    verify(employeeRepository, never()).save(any());
  }

  @Test
  void update_deptLeadOutsideScope_throwsAccessDenied() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    employee.setDepartmentId(UUID.randomUUID());
    Employee lead = new Employee();
    lead.setId(UUID.randomUUID());
    lead.setEmployeeCode("lead1");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(employeeRepository.findByEmployeeCode("lead1")).thenReturn(Optional.of(lead));
    when(departmentRepository.findIdsByManager_IdAndStatus(lead.getId(),
        DepartmentStatus.ACTIVE)).thenReturn(List.of(UUID.randomUUID()));

    EmployeeUpdateRequest patch = updateWithEmail("x@hrms.vn");
    assertThrows(AccessDeniedException.class,
        () -> service.update(employeeId, patch, actorId, deptLeadViewer()));
    verify(employeeRepository, never()).save(any());
  }

  @Test
  void update_noFields_throwsBusinessRule() {
    EmployeeUpdateRequest patch = updateReq();
    assertThrows(BusinessRuleException.class, () -> service.update(employeeId, patch, actorId, adminViewer()));
    verify(employeeRepository, never()).findById(any());
  }

  @Test
  void update_statusTerminatedViaPatch_rejected() {
    EmployeeUpdateRequest patch = updateWithStatus("TERMINATED");
    assertThrows(BusinessRuleException.class, () -> service.update(employeeId, patch, actorId, adminViewer()));
    verify(employeeRepository, never()).findById(any());
  }

  @Test
  void update_changedField_savesAndWritesOutbox() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    employee.setEmail("old@hrms.vn");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    EmployeeUpdateRequest patch = updateWithEmail("new@hrms.vn");
    service.update(employeeId, patch, actorId, adminViewer());

    assertEquals("new@hrms.vn", employee.getEmail());

    ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(outbox.capture());
    assertEquals(EmployeeEventTypes.UPDATED, outbox.getValue().getEventType());

    EmployeeEventPayload payload =
        OutboxPayloadConverter.fromJson(outbox.getValue().getPayload(),
            EmployeeEventPayload.class);
    assertTrue(payload.changedFields().contains("email"));
  }

  @Test
  void update_officeChange_savesWithoutJobHistory() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    employee.setOffice("Trụ sở Hà Nội");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    EmployeeUpdateRequest patch = updateWithOffice("VP Đà Nẵng");
    service.update(employeeId, patch, actorId, adminViewer());

    assertEquals("VP Đà Nẵng", employee.getOffice());
    // office là info thường xuyên đổi (không phải job info) → không ghi history
    verify(jobHistoryService, never()).record(any(), any(), any(), any(), any());

    ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(outbox.capture());
    EmployeeEventPayload payload =
        OutboxPayloadConverter.fromJson(outbox.getValue().getPayload(),
            EmployeeEventPayload.class);
    assertTrue(payload.changedFields().contains("office"));
  }

  @Test
  void update_positionChange_recordsJobHistory() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    employee.setPosition("Backend");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    EmployeeUpdateRequest patch = updateWithPosition("Senior Backend");
    service.update(employeeId, patch, actorId, adminViewer());

    assertEquals("Senior Backend", employee.getPosition());
    verify(jobHistoryService).record(eq(employeeId), eq("position"), eq("Backend"),
        eq("Senior Backend"), eq(actorId));
  }

  @Test
  void update_departmentChange_resolvesNameAndRecordsHistory() {
    UUID departmentId = UUID.randomUUID();
    Department department = new Department();
    department.setId(departmentId);
    department.setName("HR");

    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    employee.setDepartmentId(UUID.randomUUID());
    employee.setDepartmentName("Engineering");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    EmployeeUpdateRequest patch = updateWithDepartment(departmentId);
    service.update(employeeId, patch, actorId, adminViewer());

    assertEquals(departmentId, employee.getDepartmentId());
    assertEquals("HR", employee.getDepartmentName());
    verify(jobHistoryService).record(eq(employeeId), eq("department"), eq("Engineering"),
        eq("HR"), eq(actorId));
  }

  @Test
  void update_managerCannotBeSelf_throwsBusinessRule() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

    EmployeeUpdateRequest patch = updateWithManager(employeeId);
    assertThrows(BusinessRuleException.class, () -> service.update(employeeId, patch, actorId, adminViewer()));
    verify(employeeRepository, never()).save(any());
  }

  @Test
  void update_nothingChanged_writesNoOutbox() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    employee.setFullName("Nguyễn Văn A");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

    EmployeeUpdateRequest patch = updateWithFullName("Nguyễn Văn A");
    service.update(employeeId, patch, actorId, adminViewer());

    verify(outboxRepository, never()).save(any());
  }

  @Test
  void terminate_setsStatusAndWritesTerminatedEvent() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    service.terminate(employeeId, actorId, "Tự nguyện", adminViewer());

    assertEquals(EmployeeStatus.TERMINATED, employee.getStatus());

    ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(outbox.capture());
    assertEquals(EmployeeEventTypes.TERMINATED, outbox.getValue().getEventType());

    EmployeeEventPayload payload =
        OutboxPayloadConverter.fromJson(outbox.getValue().getPayload(),
            EmployeeEventPayload.class);
    assertNotNull(payload.terminatedAt());
    assertEquals("Tự nguyện", payload.reason());
    assertFalse(payload.changedFields().contains("status"));
    verify(jobHistoryService).record(eq(employeeId), eq("status"), eq("ACTIVE"),
        eq("TERMINATED"), eq(actorId), eq("Tự nguyện"));
    verify(keycloakSyncService).enqueueEnabled("NV0001", false);
  }

  @Test
  void terminate_alreadyTerminated_throwsBusinessRule() {
    Employee employee = employeeWithStatus(EmployeeStatus.TERMINATED);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

    assertThrows(BusinessRuleException.class,
        () -> service.terminate(employeeId, actorId, null, adminViewer()));
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void assignCalendar_savesAndWritesHistory() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    UUID calendarId = UUID.randomUUID();
    HolidayCalendar calendar = new HolidayCalendar();
    calendar.setId(calendarId);
    calendar.setCode("ONSITE");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    when(holidayCalendarRepository.findById(calendarId)).thenReturn(Optional.of(calendar));
    when(mapper.toResponse(employee)).thenReturn(sampleResponse("NV0001", calendarId));

    EmployeeResponse response = service.assignCalendar(employeeId, calendarId, actorId, adminViewer());

    assertEquals(calendarId, response.calendarId());
    verify(jobHistoryService).record(eq(employeeId), eq("calendar"), isNull(),
        eq("ONSITE"), eq(actorId));
    verify(outboxRepository).save(any(OutboxEvent.class));
  }

  @Test
  void assignCalendar_sameCalendar_noop() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    UUID calendarId = UUID.randomUUID();
    employee.setCalendarId(calendarId);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(mapper.toResponse(employee)).thenReturn(sampleResponse("NV0001", calendarId));

    EmployeeResponse response = service.assignCalendar(employeeId, calendarId, actorId, adminViewer());

    assertEquals(calendarId, response.calendarId());
    verify(employeeRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void me_matchedByEmployeeCode_returnsProfile() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findByEmployeeCode("NV0001")).thenReturn(Optional.of(employee));
    when(mapper.toResponse(employee)).thenReturn(sampleResponse());

    assertEquals("NV0001", service.me("NV0001", "a@x.vn").employeeCode());
    verify(employeeRepository, never()).findFirstByEmail(any());
  }

  @Test
  void me_fallbackEmail_returnsProfile() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findByEmployeeCode("manager1")).thenReturn(Optional.empty());
    when(employeeRepository.findFirstByEmail("employee1@hrms.local"))
        .thenReturn(Optional.of(employee));
    when(mapper.toResponse(employee)).thenReturn(sampleResponse("NV0002", null));

    assertEquals("NV0002", service.me("manager1", "employee1@hrms.local").employeeCode());
  }

  private Employee employeeWithStatus(EmployeeStatus status) {
    Employee employee = new Employee();
    employee.setId(employeeId);
    employee.setEmployeeCode("NV0001");
    employee.setFullName("Nguyễn Văn A");
    employee.setStatus(status);
    return employee;
  }

  // ---- Factory helpers: tránh lỗi sai số lượng tham số khi record thêm field ----

  private static EmployeeCreateRequest createReq(String employeeCode, String fullName,
      String email, UUID departmentId, String position) {
    return new EmployeeCreateRequest(employeeCode, fullName, email, departmentId, position, null,
        null, null, null, null, null, null, null);
  }

  private EmployeeResponse sampleResponse() {
    return sampleResponse("NV0001", null);
  }

  private EmployeeResponse sampleResponse(String code, UUID calendarId) {
    return new EmployeeResponse(
        employeeId, code, "Nguyễn Văn A", null, null, null, null, null, EmployeeStatus.ACTIVE,
        null, null, null, null, null, null, null, null, null, null, null, null, null, calendarId,
        null, null, 0L);
  }

  /** Response có đủ PII để assert mask (nationalId/address/emergencyContactPhone). */
  private EmployeeResponse piiResponse(String code) {
    return new EmployeeResponse(
        employeeId, code, "Nguyễn Văn A", "a@hrms.vn", null, null, "Backend", null,
        EmployeeStatus.ACTIVE, null, null, null, "0912345678", "012345678901",
        "12 Phố Huế, Hà Nội", null, null, null, "Nguyễn Văn B", "0900000002", "Vợ",
        null, null, null, null, 0L);
  }

  private static EmployeeViewer adminViewer() {
    return new EmployeeViewer("hr", "hr@hrms.vn", EmployeeScope.ALL);
  }

  private static EmployeeViewer managerViewer() {
    return new EmployeeViewer("manager1", "manager1@hrms.vn", EmployeeScope.SELF);
  }

  private static EmployeeViewer deptLeadViewer() {
    return new EmployeeViewer("lead1", "lead1@hrms.vn", EmployeeScope.DEPARTMENT);
  }

  /** PATCH toàn null (19 trường) — dùng cho case "không có trường nào để cập nhật". */
  private static EmployeeUpdateRequest updateReq() {
    return new EmployeeUpdateRequest(null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null);
  }

  private static EmployeeUpdateRequest updateWithFullName(String fullName) {
    return new EmployeeUpdateRequest(fullName, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null);
  }

  private static EmployeeUpdateRequest updateWithEmail(String email) {
    return new EmployeeUpdateRequest(null, email, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null);
  }

  private static EmployeeUpdateRequest updateWithDepartment(UUID departmentId) {
    return new EmployeeUpdateRequest(null, null, departmentId, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null);
  }

  private static EmployeeUpdateRequest updateWithPosition(String position) {
    return new EmployeeUpdateRequest(null, null, null, null, position, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null);
  }

  private static EmployeeUpdateRequest updateWithManager(UUID managerId) {
    return new EmployeeUpdateRequest(null, null, null, null, null, managerId, null, null, null,
        null, null, null, null, null, null, null, null, null, null);
  }

  private static EmployeeUpdateRequest updateWithStatus(String status) {
    return new EmployeeUpdateRequest(null, null, null, null, null, null, null, status, null, null,
        null, null, null, null, null, null, null, null, null);
  }

  private static EmployeeUpdateRequest updateWithOffice(String office) {
    return new EmployeeUpdateRequest(null, null, null, null, null, null, null, null, null, null,
        null, null, null, office, null, null, null, null, null);
  }
}
