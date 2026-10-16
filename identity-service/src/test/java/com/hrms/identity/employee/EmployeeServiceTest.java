package com.hrms.identity.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.kafka.EmployeeEventTypes;
import com.hrms.common.kafka.KafkaTopics;
import com.hrms.common.outbox.OutboxEvent;
import com.hrms.common.outbox.OutboxPayloadConverter;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.department.Department;
import com.hrms.identity.department.DepartmentRepository;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import com.hrms.identity.employee.dto.EmployeeResponse;
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
import com.hrms.identity.event.EmployeeEventPayload;
import com.hrms.identity.history.JobHistoryService;
import com.hrms.identity.holiday.HolidayCalendar;
import com.hrms.identity.holiday.HolidayCalendarRepository;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
  private HolidayCalendarRepository holidayCalendarRepository;

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
    verify(keycloakAdminClient).createUser(eq("NV0001"), eq("Nguyễn Văn A"), eq("a@hrms.vn"));
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
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    service.create(request, actorId);

    assertEquals("nthieu", entity.getEmployeeCode());
    verify(keycloakAdminClient).createUser(eq("nthieu"), eq("Nguyễn Trung Hiếu"), isNull());
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
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    service.create(request, actorId);

    assertEquals("nthieu1", entity.getEmployeeCode());
    verify(keycloakAdminClient).createUser(eq("nthieu1"), any(), any());
  }

  @Test
  void create_keycloakUsernameTaken_throwsConflictAndSkipsOutbox() {
    EmployeeCreateRequest request = createReq("hr", "Hồ Ráng", null, null, null);
    Employee entity = new Employee();
    entity.setFullName("Hồ Ráng");

    when(employeeRepository.existsByEmployeeCode("hr")).thenReturn(false);
    when(mapper.toEntity(request)).thenReturn(entity);
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
      Employee saved = inv.getArgument(0);
      saved.setId(employeeId);
      return saved;
    });
    doThrow(new ConflictException("Tên đăng nhập đã tồn tại trong Keycloak: hr"))
        .when(keycloakAdminClient).createUser(eq("hr"), any(), any());

    assertThrows(ConflictException.class, () -> service.create(request, actorId));
    verify(outboxRepository, never()).save(any());
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
        any())).thenReturn(List.of(employee));
    when(employeeRepository.countSearch(eq(EmployeeStatus.ACTIVE), isNull(), eq(""), eq(""),
        eq(""))).thenReturn(1L);
    when(mapper.toResponse(employee)).thenReturn(response);

    PageResponse<EmployeeResponse> result =
        service.search(0, 20, EmployeeStatus.ACTIVE, null, null, null, null);

    assertEquals(1, result.items().size());
    assertEquals(1L, result.total());
    assertEquals("NV0001", result.items().get(0).employeeCode());
  }

  @Test
  void get_notFound_throwsResourceNotFound() {
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> service.get(employeeId));
  }

  @Test
  void update_noFields_throwsBusinessRule() {
    EmployeeUpdateRequest patch = updateReq();
    assertThrows(BusinessRuleException.class, () -> service.update(employeeId, patch, actorId));
    verify(employeeRepository, never()).findById(any());
  }

  @Test
  void update_statusTerminatedViaPatch_rejected() {
    EmployeeUpdateRequest patch = updateWithStatus("TERMINATED");
    assertThrows(BusinessRuleException.class, () -> service.update(employeeId, patch, actorId));
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
    service.update(employeeId, patch, actorId);

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
    service.update(employeeId, patch, actorId);

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
    service.update(employeeId, patch, actorId);

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
    service.update(employeeId, patch, actorId);

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
    assertThrows(BusinessRuleException.class, () -> service.update(employeeId, patch, actorId));
    verify(employeeRepository, never()).save(any());
  }

  @Test
  void update_nothingChanged_writesNoOutbox() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    employee.setFullName("Nguyễn Văn A");
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

    EmployeeUpdateRequest patch = updateWithFullName("Nguyễn Văn A");
    service.update(employeeId, patch, actorId);

    verify(outboxRepository, never()).save(any());
  }

  @Test
  void terminate_setsStatusAndWritesTerminatedEvent() {
    Employee employee = employeeWithStatus(EmployeeStatus.ACTIVE);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    service.terminate(employeeId, actorId, "Tự nguyện");

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
    verify(keycloakAdminClient).setUserEnabled("NV0001", false);
  }

  @Test
  void terminate_alreadyTerminated_throwsBusinessRule() {
    Employee employee = employeeWithStatus(EmployeeStatus.TERMINATED);
    when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

    assertThrows(BusinessRuleException.class,
        () -> service.terminate(employeeId, actorId, null));
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

    EmployeeResponse response = service.assignCalendar(employeeId, calendarId, actorId);

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

    EmployeeResponse response = service.assignCalendar(employeeId, calendarId, actorId);

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
