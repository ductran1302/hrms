# AGENTS.md — Quick Reference cho AI Agents

> **Project:** HRMS — Human Resource Management System  
> **Stack:** Spring Boot 3.3 (Java 21) + React 18 + Kafka + PostgreSQL + Keycloak  
> **Language:** Code comments/prose = Vietnamese; identifiers = English

---

## 1. Quick Commands

```bash
# Lần đầu: tạo .env (compose + application.yml đã fail-fast — không còn secret default trong git)
cp .env.example .env

# Build toàn bộ (chạy trong dev container)
docker exec hrms-dev mvn -f /workspaces/HRMS/pom.xml clean verify

# Build 1 module (skip test)
docker exec hrms-dev mvn -f /workspaces/HRMS/pom.xml clean install -DskipTests -pl identity-service

# Chạy service dev (background) — PHẢI cd /workspaces/HRMS để DOCS_DIR data/documents đúng (xem §7.7)
# + nạp .env (container hrms-dev cũ không env_file; thiếu DB_PASSWORD/HRMS_IDENTITY_CLIENT_SECRET → start fail, xem §7.8)
docker exec -d hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && java -jar identity-service/target/identity-service-0.1.0-SNAPSHOT.jar > /tmp/identity.log 2>&1"

# Xem log
docker exec hrms-dev tail -f /tmp/identity.log

# Smoke test (verify-p1-scoping TRỨC TIẾP đọc env HRMS_IDENTITY_CLIENT_SECRET → nạp .env trước)
# + các script API mặc định localhost:8080 — khi app stack chạy và dev identity KHÔNG chạy,
#   override API=http://gateway:8080 (xem §7.2)
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-identity.sh"
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-p1-scoping.sh"
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-attendance.sh"
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-gateway-routes.sh"
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-roles.sh"

# 5-role setup cho KC đã chạy (rename HR_ADMIN→ADMIN + tạo GLOBAL_LEAD/DEPT_LEAD + group HR; idempotent)
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/kc-init-roles.sh"

# Frontend (dev)
docker exec hrms-dev bash -lc "cd /workspaces/HRMS/frontend && npm run build"
docker exec hrms-dev bash -lc "cd /workspaces/HRMS/frontend && npm run dev"

# Frontend (app stack production-style) — PHẢI build image + up -d để apply code mới
# (Lưu ý: `npm run build` trong dev container chỉ ghi dist vào host, KHÔNG update image app stack)
docker compose -f docker-compose.app.yml build frontend
docker compose -f docker-compose.app.yml up -d frontend
```

---

## 2. Architecture Rules (KHÔNG VI PHẠM)

| Quy tắc | Lý do |
|---|---|
| 1 service = 1 PostgreSQL DB, **không share/join chéo** | Boundary rõ ràng, deploy độc lập |
| Đồng bộ giữa service chỉ qua **Kafka event** | Không gọi REST chéo services |
| **Outbox pattern**: business data + outbox row在同一 transaction | Không mất event khi crash |
| JWT từ Keycloak, validate ở gateway, role đọc từ token | Centralized auth |
| Frontend gọi **gateway**, không gọi service trực tiếp | Single entry point |

---

## 3. Port Assignments

| Service | Dev Port | App Stack Port | Notes |
|---|---|---|---|
| identity | 8080 | 8080 | Dev conflict khi app stack chạy |
| gateway | 8085 | 8080 | App stack map container 8080 → host |
| attendance | 8086 | 8080 | |
| frontend | 5173 | 3000 | Vite dev vs nginx |
| keycloak | 8081 | — | Host |
| kafka | 9092 (host) / 29092 (in-network) | — | |

**Lưu ý:** Khi app stack (`docker-compose.app.yml`) đang chạy → `localhost:8080` là app gateway, không phải identity dev.

---

## 4. Backend Conventions (Java/Spring)

### 4.1 Package Structure

```
com.hrms.<service>.<feature>         # Feature vertical slicing
com.hrms.<service>.<feature>.dto     # DTOs (identity only; attendance inline records)
com.hrms.<service>.config            # SecurityConfig, PersistenceConfig, KafkaConfig
```

**Modules:** `hrms-common`, `identity-service`, `gateway-service`, `attendance-service`  
**Root package:** `com.hrms` (scanBasePackages)

### 4.2 Entity Pattern

```java
@Entity
@Table(name = "employees")
@Getter @Setter @NoArgsConstructor
public class Employee extends BaseEntity {  // BaseEntity: UUID id, createdAt, updatedAt, @Version
  @Column(name = "employee_code", nullable = false, length = 20)
  private String employeeCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private EmployeeStatus status;
  
  // FK = plain UUID column, KHÔNG @ManyToOne
}
```

- Enums: top-level types, `@Enumerated(EnumType.STRING)`
- Soft delete: `EmployeeStatus.TERMINATED`, không physical delete domain records
- Snake_case column names luôn explicit

### 4.3 Controller Pattern

```java
@Tag(name = "Employees")
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {
  private final EmployeeService employeeService;  // constructor injection, final fields

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)  // 201 for create
  @PreAuthorize("hasRole('ADMIN')")  // tạo NV = ADMIN duy nhất (xem §4.14)
  @Operation(summary = "Tạo nhân viên mới")
  public EmployeeResponse create(@Valid @RequestBody EmployeeCreateRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
    return employeeService.create(request, jwt.getSubject());
  }

  @PatchMapping("/{id}")  // Partial update → PATCH, not PUT
  @PreAuthorize("hasAnyRole('ADMIN','GLOBAL_LEAD','DEPT_LEAD')")  // ghi = 3 role cao (không MANAGER)
  public EmployeeResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody EmployeeUpdateRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
    return employeeService.update(id, request, jwt.getSubject());
  }
}
```

**Conventions:**
- Prefix: `/api/v1/...` always
- Create → `@ResponseStatus(CREATED)`, Delete → `@ResponseStatus(NO_CONTENT)`
- Partial update → `PATCH`, full replace → `PUT` (ít dùng)
- `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()` = actorId
- Pagination: `page` (default 0), `size` (default 20), filters: `status`, `departmentId`, `q`

### 4.4 Service Pattern

```java
@Slf4j @Service @RequiredArgsConstructor
public class EmployeeService {
  private static final String AGGREGATE_TYPE = "Employee";
  private final EmployeeRepository employeeRepository;
  private final OutboxRepository outboxRepository;
  private final EmployeeMapper mapper;  // MapStruct

  @Transactional  // Write methods
  public EmployeeResponse create(EmployeeCreateRequest request, String actorId) {
    // Validate → save entity → save outbox (SAME TRANSACTION) → return
  }

  @Transactional(readOnly = true)  // Read methods
  public PageResponse<EmployeeResponse> search(...) {
    // ...
    return PageResponse.of(items, page, size, total);
  }

  private Employee find(UUID id) {
    return employeeRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
  }
}
```

### 4.5 DTO Pattern (Records)

```java
// Request với validation
public record EmployeeCreateRequest(
    @NotBlank @Size(max = 20) String employeeCode,
    @NotBlank @Size(max = 200) String fullName,
    @Email @Size(max = 200) String email,
    UUID departmentId
) {}

// Update: null = don't change; Boolean clearXxx = unlink
public record EmployeeUpdateRequest(
    String fullName,
    UUID departmentId,
    Boolean clearDepartment
) {}

// Response: flat, includes audit fields
public record EmployeeResponse(
    UUID id, String employeeCode, String fullName, String email,
    Instant createdAt, Instant updatedAt, Long version
) {}
```

### 4.6 Exception Handling

```java
// Throw từ service layer:
throw new ResourceNotFoundException("Employee", id);     // 404
throw new ConflictException("Employee code đã tồn tại"); // 409
throw new BusinessRuleException("Không thể xóa phòng ban còn nhân viên"); // 422

// KHÔNG viết @RestControllerAdvice riêng — dùng chung:
// com.hrms.common.web.GlobalExceptionHandler (đã xử lý hết)
```

**Error codes:** `ErrorCode` enum trong `hrms-common`:
- `VALIDATION_FAILED` (400), `RESOURCE_NOT_FOUND` (404), `CONFLICT` (409), `BUSINESS_RULE_VIOLATION` (422), `INTERNAL_ERROR` (500)

### 4.7 Repository Pattern

```java
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
  boolean existsByEmployeeCode(String employeeCode);
  
  @Query("""
    SELECT e FROM Employee e
    WHERE (:status IS NULL OR e.status = :status)
      AND (:departmentId IS NULL OR e.departmentId = :departmentId)
      AND (:q IS NULL OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
  """)
  List<Employee> search(@Param("status") EmployeeStatus status, Pageable pageable);

  @Query("... same WHERE clause ...")
  long countSearch(...);  // Pair count query (API dùng total, không Page<T>)
}
```

### 4.8 MapStruct

```java
@Mapper(componentModel = "spring")
public interface EmployeeMapper {
  EmployeeResponse toResponse(Employee employee);

  @Mapping(target = "id", ignore = true)  // Ignore audit/derived fields
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  Employee toEntity(EmployeeCreateRequest request);
}
```

**Lưu ý:** Attendance service dùng static factory `EmployeeResponse.from(entity)` thay vì MapStruct.

### 4.9 Flyway Migrations

```sql
-- File: identity-service/src/main/resources/db/migration/V3__employee_photo.sql
-- Naming: V<n>__<snake_case_description>.sql (double underscore)

alter table employees add column photo_url varchar(500);

-- Constraints:
-- uq_<table>_<col> unique
-- fk_<table>_<ref> foreign key  
-- idx_<table>_<cols> index
```

**Rules:**
- Append-only (không sửa migration đã chạy)
- Lowercase SQL keywords và table names
- `timestamptz`, `uuid` PKs
- `ddl-auto: validate` → schema phải khớp chính xác

### 4.10 Avro Events

```json
// hrms-common/src/main/avro/EmployeeCreatedEvent.avsc
{
  "type": "record",
  "name": "EmployeeCreatedEvent",
  "namespace": "com.hrms.common.avro.employee",
  "fields": [
    {"name": "eventId", "type": "string", "doc": "UUID - idempotency key"},
    {"name": "eventType", "type": "string", "default": "employee.created"},
    {"name": "occurredAt", "type": {"type": "long", "logicalType": "timestamp-millis"}},
    {"name": "employeeId", "type": "string"},
    ...
  ]
}
```

**Rules:**
- Naming: `<Aggregate><Verb>Event.avsc`
- Luôn có: `eventId` (idempotency), `eventType`, `occurredAt`
- Event type constants: `EmployeeEventTypes.java` trong `hrms-common`
- Thêm package mới → cập nhật `org.apache.avro.SERIALIZABLE_PACKAGES=com.hrms` trong root `pom.xml`

### 4.11 Outbox Pattern

```java
// Trong service method (CÙNG TRANSACTION với business data):
@Transactional
public EmployeeResponse create(EmployeeCreateRequest request, String actorId) {
  Employee employee = employeeRepository.save(mapper.toEntity(request));
  
  // Outbox在同一 transaction
  outboxRepository.save(OutboxEvent.of(
      AGGREGATE_TYPE,
      employee.getId().toString(),
      EmployeeEventTypes.CREATED,
      KafkaTopics.EMPLOYEE_V1,
      OutboxPayloadConverter.toJson(payload)  // JSON payload
  ));
  
  return mapper.toResponse(employee);
}
```

**Service phải implement:** `OutboxMessageConverter` (1 bean duy nhất):
```java
@Component
public class IdentityOutboxConverter implements OutboxMessageConverter {
  @Override
  public Object convert(OutboxEvent event) {
    switch (event.getEventType()) {
      case EmployeeEventTypes.CREATED -> {
        EmployeeEventPayload payload = ...;
        return EmployeeCreatedEvent.newBuilder()
            .setEventId(event.getId().toString())  // eventId = outbox row id
            .setEmployeeId(payload.employeeId())
            .build();
      }
      // ...
    }
  }
}
```

### 4.12 Kafka Consumer Pattern

```java
@Component
@RequiredArgsConstructor
public class EmployeeEventConsumer {
  private final EmployeeService employeeService;

  @KafkaListener(
      topics = KafkaTopics.EMPLOYEE_V1,
      containerFactory = "kafkaListenerContainerFactory",
      groupId = "attendance-service"  // = service name
  )
  public void onMessage(ConsumerRecord<String, SpecificRecord> record) {
    SpecificRecord value = record.value();
    if (value instanceof EmployeeCreatedEvent event) {
      employeeService.applyCreated(event);
    } else if (value instanceof EmployeeUpdatedEvent event) {
      employeeService.applyUpdated(event);
    }
    // Unknown type → throw → DLQ
  }
}
```

**Idempotency:**
```java
@Transactional
public void applyCreated(EmployeeCreatedEvent event) {
  if (isDuplicate(event.getEventId())) return;  // processed_events.existsById
  
  Employee employee = findOrCreate(event.getEmployeeId(), event.getEmployeeCode());
  // ... apply changes ...
  employeeRepository.save(employee);
  
  markProcessed(event.getEventId(), event.getEventType());  // Save ProcessedEvent
}
```

**DLQ:** `DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L))` → 1 attempt + 2 retries → DLQ

### 4.13 Topics Naming

```java
public class KafkaTopics {
  public static final String EMPLOYEE_V1 = "hrms.employee.v1";
  public static final String HOLIDAY_V1 = "hrms.holiday.v1";
  public static final String DEPARTMENT_V1 = "hrms.department.v1";
  
  public static String dlq(String topic) {
    return topic + ".DLQ";
  }
}
```

**Pattern:** `hrms.<aggregate>.v1` (version suffix), RecordNameStrategy → nhiều event types chung 1 topic.

### 4.14 Roles & Scoping (5 role — Q&A đã chốt)

| Role | Level | Scope đọc NV | Ghi NV | Salary hợp đồng | Tạo NV |
|---|---|---|---|---|---|
| `ADMIN` | 4 (rename HR_ADMIN) | ALL | ALL | ✓ | ✓ duy nhất |
| `GLOBAL_LEAD` | 3 | ALL | ALL | ✓ | ✗ |
| `DEPT_LEAD` | 2 | subtree dept mình ql ∪ reports ∪ self | như scope (KHÔNG self — chống tự mở quyền) | ✗ (mask) | ✗ |
| `MANAGER` | 1 | self ∪ reports (mask non-self) | ✗ (chỉ HR admin ghi trước đây) | ✗ | ✗ |
| `EMPLOYEE` | 0 | self | ✗ | ✗ | ✗ |

- **Constants:** `Roles.java` (hrms-common) — `all()`, `level()`, `maxLevel()`; KHÔNG thêm role khác.
- **`EmployeeScope`** (common/security, dùng chung identity + attendance): `ALL`/`DEPARTMENT`/`SELF`,
  `fromAuthorities(jwt)` đọc từ authorities (fail-closed = `SELF` khi không nhận ra).
- **`EmployeeViewer`** (identity): wrap username/email/scope — `unrestricted()`/`maskOthers()`;
  EmployeeService: `departmentScope(...)` BFS descendants + `assertWriteScope` (DEPT_LEAD không ghi self).
- **@PreAuthorize matrix:** reads = `ADMIN/GLOBAL_LEAD/DEPT_LEAD/MANAGER` (+EMPLOYEE cho my-*,
  holiday đọc), writes = `ADMIN` (+`GLOBAL_LEAD/DEPT_LEAD` cho employee update), tạo NV = `ADMIN`.
- **Auto-assign role (creation-only):** `RoleAssignmentService.resolveDefaultRole` —
  manager dept parent=null ACTIVE → `GLOBAL_LEAD` > manager dept ACTIVE bất kỳ → `DEPT_LEAD` >
  có reports ACTIVE → `MANAGER` > `EMPLOYEE`. Gọi lúc tạo NV + idempotent khi tạo hợp đồng.
- **PATCH `/api/v1/accounts/{username}/roles`** (ADMIN, 204): validate subset `Roles.all()` (422),
  chặn tự hạ mình khi target==actor (422). KC running đã có HR_ADMIN → chạy `scripts/kc-init-roles.sh`
  (rename + tạo role + group HR; idempotent).
- **Scoping đọc (IDOR) — 2026-10-23:** mọi GET tham chiếu nhân sự áp `EmployeeScope`; fail-closed
  khi `username` không khớp hồ sơ (`employeeCode`).
  - **Attendance:** `EmployeeAccess` + `EmployeeAccessResolver` (employee/) — ALL → toàn bộ;
    DEPARTMENT → `DepartmentService.managedSubtreeIds` ∪ trực thuộc ∪ self; SELF → trực thuộc ∪ self.
    Áp cho `EmployeeService.list/getByCode` (MANAGER nay chỉ self ∪ trực thuộc, KHÔNG còn toàn bộ),
    `LeaveQuotaService.getQuota/listQuotas`, `LeaveSettingsService.getNote/listWorkDays`,
    `ShiftAssignmentService.list` (ngoài scope → 403; list lọc theo scope).
  - **Identity contracts:** `EmployeeViewer.of(jwt,auth)` + `EmployeeRepository.findIdsInScope`;
    `ContractService.search/expiringSoon` lọc theo id trong scope, `listByEmployee` ngoài scope → 403.
  - MANAGER/DEPT_LEAD seed không gắn hồ sơ NV → thấy rỗng (đúng, giống `/employees`).
- **KHÔNG đổi:** org-chart vẫn toàn công ty.
- **KC realm:** `keycloak/realm-hrms.json` 5 roles + groups HR/Board/DeptLeads/Managers;
  user `hr` realmRoles `["ADMIN"]`.

### 4.15 Leave Quota Settings & Policy (2026-10-21)

Feature "thiết kế lại tính quỹ phép" — quỹ tính theo **kỳ công** + **năm tài chính**:

- **`leave_settings`** (1 row global): `fyStartMonth/fyStartDay` (năm TC),
  `periodStartDay/periodEndDay` — chỉ 2 combo hợp lệ: start=1,end≥29 hoặc
  start=end+1 (hở → 422; kỳ WHOLE theo lưới, gán năm TC theo ngày end → FY 1/4 +
  kỳ 26/25 = **12 kỳ** đúng: kỳ đầu 26/3–25/4 bắt đầu trước fyStart, đuôi 26/3–31/3
  thuộc "kỳ tháng 4" của FY sau — không clip, không tạo kỳ thứ 13), `saturdayPolicy`
  (OFF/HALF/ALTERNATING/ALL), `minWorkPercent` (x%, default 50),
  `require_policy_approval` (default **true** — false: activate thẳng từ DRAFT).
  API `GET/PUT /api/v1/attendance/leave/settings` (đọc 4 role, ghi ADMIN).
- **`leave_policies` versioning** (nhiều version/năm TC; effective-dated):
  `baseDays`, `seniorityRule` (`VN_TIERED` default | `LINEAR`), `seniorityTiers[]`
  (bảng `leave_seniority_tiers`: `minYears/bonusDays`, unique (policy_id, min_years)),
  `seniorityStepYears/seniorityBonusDays/seniorityMaxBonus` (chỉ LINEAR —
  scalar giữ cho compat), `carryoverMax`, `roleQuotas[]`
  (`leave_role_quotas`, Source=POSITION), `versionNo`, `status`
  (**DRAFT → PENDING_APPROVAL → APPROVED → ACTIVE → EXPIRED**; CANCELLED;
  reject = PENDING→DRAFT), `effectiveFrom/effectiveTo`, `submittedAt` etc.
  - **Thâm niên — strategy pattern (mở rộng Hàn/Nhật không đập đi xây lại)**:
    interface `SeniorityRule` (`bonus(SeniorityContext)`) + `SeniorityRuleRegistry`
    (Spring nạp mọi bean; package `attendance.leave.seniority`).
    `VN_TIERED` (mặc định, NĐ 145/2020): bậc `0/5/10/15/20 → +0/2/4/6/8` seed
    (createDefaultDraft/copy source/request null đều seed 5 bậc luật VN),
    áp dụng khi `Period.between(hiredAt, asOf)` **đủ** `minYears` (asOf = ngày
    recalculate; lệch ~1 ngày so với "trên N năm" của luật — nhất quán mọi bậc),
    bỏ qua `seniorityMaxBonus` (bậc = giá trị tuyệt đối), tiers rỗng → self-heal
    tạm tính LINEAR; `LINEAR` (legacy): `bonus = stepYears×n × bonusDays` theo
    năm dương lịch, cap `maxBonus`. Validation: VN_TIERED + `seniorityTiers=[]`
    → 422, `minYears<0`/trùng → 422. Mở rộng: thêm enum + `@Component` rule.
    Migration `V16__seniority_rule_tiers.sql` (cột + bảng + seed).
  - Workflow (ADMIN tạo/sửa/activate/cancel; **approve/reject thêm GLOBAL_LEAD**
    — duyệt cuối chính sách toàn công ty): `POST /leave/policies` (create DRAFT,
    validate effectiveFrom > mọi version hiện có trong năm → 422),
    `PUT /versions/{id}` (chỉ DRAFT), `POST /{id}/submit|approve|reject|activate|cancel`,
    `DELETE /{id}` (chỉ DRAFT). `activate`: version mới effectiveFrom = D
    (D trong FY range, > version ACTIVE cũ; **quá khứ được phép — FE cảnh báo
    trước khi kích hoạt**) → ACTIVE cũ tự EXPIRED với `effectiveTo = D−1`;
    activate yêu cầu D > effectiveFrom của version cũ.
    Flag `requirePolicyApproval=false` → activate thẳng từ DRAFT.
  - `GET /leave/policies/in-force?year=` (write tx — self-heal + lazy expire,
    xem §7.9); `GET /leave/policies?year=` (history); `GET /leave/policies/{id}`.
  - **BASE mid-year tính per-period**: mỗi kỳ p dùng policy hiệu lực tại
    `p.start` → `Σ baseDays/tổng_số_kỳ` (chia 1 lần cuối); SENIORITY/role
    bonus/carryover dùng policy in-force tại asOf. `DELETE /{year}` (theo năm)
    **đã bỏ** — xóa theo id (chỉ DRAFT).
- **`leave_work_days`** (ngày công từng kỳ/NV): DEFAULT (auto-fill từ settings
  khi GET quota) / MANUAL (HR sửa tay — recalculate KHÔNG đụng dòng MANUAL).
  API `/api/v1/attendance/leave/quotas/{employeeId}/work-days`.
- **`leave_employee_notes`**: note ≤500 ký tự/NV/năm — `/quotas/{employeeId}/note`.
- **Recalculate** `POST /api/v1/attendance/leave/quotas/recalculate` (ADMIN,
  `{year, employeeIds?}`): BASE = `baseDays × số_kỳ_đạt / tổng_số_kỳ`
  (tổng = **12 kỳ** với lưới 26→25 → baseDays chia hết 12 ⇒ số nguyên;
  một kỳ đạt: actual×100 ≥ full×minWorkPercent; mỗi kỳ WHOLE worth baseDays/12),
  SENIORITY (thâm niên), role bonus = **max** các role khớp (`employees.roles`
  sync qua event `hrms.employee.v1` + `POST /api/v1/accounts/resync-role-events`),
  carryover auto = min(còn lại năm trước, carryoverMax) upsert CARRYOVER
  `is_auto=false`. Snapshot: mọi dòng auto ghi `policy_id` + `calculated_at`
  (BONUS/ADJUSTMENT policyId=null); recalculate **explicit** — không tự tính
  lại khi đổi policy. Entitlement nguồn AUTO (BASE/SENIORITY/CARRYOVER)
  **DELETE → 422**.
- **Ngày lễ KHÔNG trừ**: workingDays = T2–T6 + T7 theo policy + CN nghỉ;
  ALTERNATING: Sat #0 = đi làm, reset mỗi năm TC (`PeriodEngine`).
- **Đã fix 2 bug**: self-invocation readOnly không commit (xem §7.9);
  `ensureAuto` dùng chung `computeBase` — không ghi đè BASE đã recalculate.

### 4.16 Keycloak Sync Tasks (P0-6)

**Không gọi Keycloak write trong transaction nghiệp vụ** (tránh user mồ côi khi DB
rollback / giữ connection khi KC chậm):

- Ghi `keycloak_sync_tasks` (`identity-service`, migration `V11`) **cùng transaction**
  với employee/contract qua `KeycloakSyncService.enqueueCreate|enqueueEnabled`
  (`@Transactional(MANDATORY)`).
- `afterCommit` gọi `KeycloakSyncProcessor.process(id)` (`REQUIRES_NEW`) xử lý ngay;
  `KeycloakSyncScheduler` poll PENDING mỗi 5s làm fallback sau crash/KC lỗi.
- Khóa bi quan `findByIdForUpdate` + kiểm tra status → không xử lý trùng.
  Retry tối đa 5 lần (backoff 5s×attempt) → `FAILED`; `last_error` để điều tra.
- `KeycloakAdminClient.provisionUser` idempotent: chưa có user → tạo; có user nhưng
  **chưa có realm role** → gán role mặc định; đã có role → không đổi quyền.
- Mã NV HR nhập trùng username Keycloak vẫn 409 ngay (read-only `userExists` trước khi
  ghi DB) — không để task async gán role cho tài khoản có sẵn.
- **Vận hành (khép kín vòng P0-6):** `GET /api/v1/accounts/sync-tasks?status=&page=&size=`
  (ADMIN, `size` cap 100) xem task; `POST /api/v1/accounts/sync-tasks/{id}/retry` (ADMIN,
  202) chỉ cho task `FAILED` — reset PENDING/attempts=0/xóa `last_error` rồi xử lý lại
  afterCommit. Scheduler dọn task `DONE` cũ hơn `hrms.keycloak.sync.retention-days`
  (default 30, 0=tắt) theo `cleanup-cron` (default 3:30 hằng ngày) — `FAILED` giữ lại
  cho tới khi ADMIN retry.
- **FE:** `KeycloakSyncPanel` (Cài đặt→Tài khoản) — badge số task FAILED + bảng task
  (status/operation/attempts/lastError) + nút retry; dòng tài khoản có tag đỏ "Lỗi đồng bộ"
  khi username có task FAILED. API `searchSyncTasks`/`retrySyncTask` trong `api/accounts.ts`.

### 4.17 JWT Audience (P0-5)

- **Mọi resource server** (identity, attendance, gateway) build decoder với
  `DelegatingOAuth2TokenValidator(JwtValidators.createDefaultWithIssuer(issuer),
  JwtClaimValidator<List<String>>("aud", aud -> aud.contains(audience)))`.
  Thiếu/sai `aud` → **401** (token hợp lệ chữ ký + issuer vẫn bị loại).
- Config `hrms.keycloak.audience` — env `JWT_AUDIENCE`, default **`hrms-api`** (dùng chung
  cho mọi service; single entry point là gateway).
- Keycloak: protocol mapper `hrms-api-audience` (`oidc-audience-mapper`,
  `included.custom.audience=hrms-api`, bật cho access token, tắt id token) gắn trên client
  `hrms-spa` — có trong `keycloak/realm-hrms.json` (fresh import).
- **Realm đang chạy** (fresh import không áp dụng): chạy `scripts/kc-add-audience.sh`
  (idempotent — kiểm tra mapper, POST nếu thiếu, rồi verify token có `aud=hrms-api`).
- `hrms-identity` (client_credentials, chỉ gọi Keycloak Admin API) **cố ý không có**
  audience mapper → token của nó bị resource server từ chối (đúng: không phải token người dùng).
- **Sau khi bật:** token cũ (phát hành trước khi có mapper) bị 401 — user phải đăng nhập lại;
  smoke scripts tự lấy token mới mỗi lần chạy.
- Smoke: `verify-identity.sh` §0 — kiểm tra token hr có `aud=hrms-api` và token
  service-account (không aud) bị 401.

---

## 5. Frontend Conventions (React/TypeScript)

### 5.1 Structure

```
frontend/src/
  api/           # client.ts + <resource>.ts + types.ts
  auth/          # keycloak.ts, AuthContext.tsx, ProtectedRoute.tsx
  components/    # Shared presentational components
  layouts/       # AppLayout.tsx (nav shell)
  pages/         # One page component per file (PascalCase)
  i18n/          # locales/{vi,en}/<domain>.json
```

### 5.2 API Client

```typescript
// api/client.ts — đã có sẵn axios instance với interceptor
import client, { errorMessage } from './client';

// api/employees.ts — one file per resource
export function searchEmployees(params: EmployeeQuery = {}): Promise<PageResponse<Employee>> {
  return client.get('/api/v1/employees', { params }).then((r) => r.data);
}

export function createEmployee(data: EmployeeCreate): Promise<Employee> {
  return client.post('/api/v1/employees', data).then((r) => r.data);
}
```

**Rules:**
- Mọi API call qua shared axios client (tự attach token, handle 401)
- Function naming: `verbNoun` (searchEmployees, createEmployee, updateEmployee)
- Luôn return `Promise<T>` qua `.then((r) => r.data)`

### 5.3 Types

```typescript
// api/types.ts — ALL shared types in one file
export interface Employee {
  id: string;
  employeeCode: string;
  fullName: string;
  status: EmployeeStatusValue;
  createdAt: string;
  version: number;
}

export type EmployeeStatusValue = 'ACTIVE' | 'PROBATION' | 'ON_LEAVE' | 'TERMINATED';

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface ApiError {
  message: string;
  fieldErrors?: Array<{ field: string; message: string }>;
  correlationId?: string;
}
```

### 5.4 Page Component Pattern

```tsx
import { useState } from 'react';
import { ProTable } from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { searchEmployees } from '../api/employees';

export default function EmployeePage() {
  const { t } = useTranslation('employee');
  const { hasRole } = useAuth();
  const canEdit = hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD');
  const canCreate = hasRole('ADMIN');  // tạo NV = ADMIN duy nhất (§4.14)
  const actionRef = useRef<ActionType>();
  const [searchValue, setSearchValue] = useState('');

  const columns = [
    { title: t('columns.code'), dataIndex: 'employeeCode' },
    { title: t('columns.name'), dataIndex: 'fullName' },
    // ...
  ];

  return (
    <ProTable
      actionRef={actionRef}
      columns={columns}
      request={async (params) => {
        const data = await searchEmployees({ page: params.current - 1, size: params.pageSize, q: searchValue });
        return { data: data.items, total: data.total, success: true };
      }}
      search={false}
    />
  );
}
```

**Rules:**
- Page components: `*Page.tsx`, default export
- All strings via `useTranslation()`, không hardcode
- Role gating: `hasRole('ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', ...)` — xem matrix §4.14
- Message: `AntApp.useApp()` → `message.success()`, không import trực tiếp

### 5.5 Routing & Auth

```tsx
// App.tsx
<Route path="/" element={<ProtectedRoute><AppLayout/></ProtectedRoute>}>
  <Route index element={<Navigate to="/employees" replace/>}/>
  <Route path="employees" element={
    <ProtectedRoute roles={['ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER']}>
      <EmployeePage/>
    </ProtectedRoute>
  }/>
</Route>

// ProtectedRoute: tự handle loading/error/unauthorized states
```

**Auth flow:**
- Keycloak PKCE singleton trong `auth/keycloak.ts`
- `AuthProvider` + `useAuth()` hook
- Token refresh tự động (setInterval 30s)
- 401 → `keycloak.login()` (redirect)

---

## 6. Testing Patterns

### 6.1 Backend Tests

**Unit tests (Mockito):**
```java
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
  @Mock EmployeeRepository employeeRepository;
  @Mock OutboxRepository outboxRepository;
  @InjectMocks EmployeeService employeeService;

  @Test
  void create_withValidData_savesEmployeeAndOutbox() {
    // Arrange → Act → Assert
    // ArgumentCaptor để verify outbox save
  }
}
```

**MVC slice tests (RBAC):**
```java
@WebMvcTest(controllers = EmployeeController.class)
@Import(SecurityConfig.class)
class EmployeeControllerTest {
  @Autowired MockMvc mockMvc;
  @MockBean EmployeeService employeeService;

  @Test
  void list_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/employees"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_withManagerRole_returns200() throws Exception {
    mockMvc.perform(get("/api/v1/employees")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
        .andExpect(status().isOk());
  }
}
```

**Test naming:** `subject_condition_expectation`
- `create_withValidData_savesEmployeeAndOutbox`
- `list_withoutToken_returns401`

**KHÔNG dùng Testcontainers** (chưa implement), chỉ pure unit + MVC slice tests.

### 6.2 Smoke Tests (Shell)

```bash
#!/usr/bin/env bash
# scripts/verify-*.sh
set -u

API=http://localhost:8080
KC=http://keycloak:8080/realms/hrms

token() { curl -s -X POST "$KC/protocol/openid-connect/token" \
    -d "client_id=hrms-spa&grant_type=password&username=$1&password=$2&scope=openid" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'; }

check() { # label expected actual
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); echo "PASS  $1"
  else FAIL=$((FAIL+1)); echo "FAIL  $1: expected=$2 actual=$3"; fi
}

# Idempotent: unique data per run
SUF=$RANDOM$RANDOM
DEPT_CODE="D$SUF"

# ... tests ...

echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
```

**Test accounts:** `hr/hr@123`, `manager1/manager@123`, `employee1/emp@123`

**Tài khoản NV auto-provision:** mật khẩu mặc định `<employeeCode>@hrms` (suffix cấu hình `hrms.keycloak.password-suffix`, env `HRMS_PASSWORD_SUFFIX`) tạo với **`temporary=true`** → Keycloak buộc đổi ở lần đăng nhập đầu; password-grant bị chặn (`invalid_grant: Account is not fully set up`) tới khi đổi. Tạo khi Tạo nhân viên + idempotent khi Tạo hợp đồng; HR cấp mật khẩu không-temporary qua `PATCH /api/v1/accounts/{username}/password`. API Cài đặt: `GET /api/v1/accounts`, `POST /api/v1/accounts/change-password`, `PATCH /api/v1/accounts/{username}/password|enabled`.

---

## 7. Common Pitfalls & Notes

### 7.1 Avro Serialization

```
LỖI: SerializationException khi deserialize class mới
NGUYÊN NHÂN: Avro chặn class không được khai báo tin tưởng
FIX: Thêm package vào org.apache.avro.SERIALIZABLE_PACKAGES=com.hrms trong root pom.xml
     (surefire cho test; KafkaAvroConfiguration set lúc runtime)
```

### 7.2 Port Conflicts

```
Khi docker-compose.app.yml đang chạy:
- localhost:8080 = app gateway (KHÔNG phải identity dev)
- TỪ TRONG dev container: localhost:8080 KHÔNG trỏ app gateway
  (không host-network) → smoke scripts (mặc định localhost:8080/8085)
  PHẢI override: API=http://gateway:8080
  (verify-attendance: ATT_URL=http://attendance:8080 GW_URL=http://gateway:8080;
   verify-gateway-routes: GW=http://gateway:8080)
- identity dev chỉ truy cập từ trong dev container:
  docker exec hrms-dev curl http://localhost:8080/swagger-ui/index.html
```

### 7.3 CORS

```
Gateway CORS cần allow origin FE:
- Dev: http://localhost:5173, http://localhost:3000
- Config trong SecurityConfig (KHÔNG phải spring.cloud.gateway.globalcors)
  → vì Security filter chain chạy trước
```

### 7.4 Keycloak Issuer

```
Token luôn có iss=http://localhost:8081/realms/hrms
Dù request qua localhost:8081 (host) hay keycloak:8080 (in-network).
Service tự build JwtDecoder với issuer cố định (hrms.keycloak.issuer).
```

### 7.5 Outbox eventId

```
eventId = outbox row id (UUID)
Khi retry → cùng eventId → consumer dedupe không xử lý lại.
```

### 7.6 Flyway Naming

```
Double underscore: V3__employee_photo.sql (KHÔNG PHẢI V3_employee_photo.sql)
Append-only: KHÔNG sửa migration đã chạy → tạo V<n+1> mới.
```

### 7.7 Dev identity phải start từ /workspaces/HRMS

```
LỖI: upload tài liệu -> 500 "Không ghi được file: /data"
NGUYÊN NHÂN: java -jar chạy với cwd=/ (docker exec default) → DOCS_DIR data/documents
             resolve thành /data/documents (không tồn tại/permission)
FIX: docker exec -d hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && java -jar identity-service/target/identity-service-0.1.0-SNAPSHOT.jar > /tmp/identity.log 2>&1"
```

### 7.8 Dev jar thiếu env + CRLF/EOL

```
LỖI (env): start dev identity fail — Flyway "password authentication failed for user hrms"
           (application.yml P0: DB_PASSWORD/HRMS_IDENTITY_CLIENT_SECRET dùng ${VAR:?...} —
           container hrms-dev cũ không nạp env_file .env; THIẾU var → lỗi DB auth khó hiểu,
           KHÔNG hiện message fail-fast)
FIX: nạp .env trước khi chạy (xem §1): set -a && . ./.env && set +a

LỖI (EOL): bash scripts/*.sh báo "$'\r': command not found" / syntax error
NGUYÊN NHÂN: core.autocrlf=true trên Windows → checkout CRLF; container Linux+Git
             KHÔNG chấp nhận \r trong .sh
FIX: GIỮ NGUYÊN `.gitattributes` (`* text=auto eol=lf`) — không xóa file này;
     sau khi pull/chuyển nhánh: `git ls-files --eol | grep w/crlf` phải rỗng
     (nếu không: git checkout -- . hoặc sed -i 's/\r$//' file CRLF)
LƯU Ý: .env cũng phải LF (docker compose đọc được CRLF nhưng `bash source` thì không)
```

### 7.9 readOnly self-invocation không commit

```
LỖI: GET trả 200 nhưng row do self-heal (getOrCreate*/ensure*) không tồn tại trong DB
     (ví dụ DELETE ngay sau GET → 404)
NGUYÊN NHÂN: method @Transactional(readOnly=true) gọi self-heal save() qua this
             → không qua Spring proxy → save chạy ngoài tx readOnly → KHÔNG commit
FIX: method nào TỰ HEAL phải @Transactional (write) dù API là "đọc" —
     xem LeaveSettingsService.getSettings, LeaveQuotaService.getPolicy,
     LeaveSettingsService.listWorkDays (đã đổi, có javadoc giải thích)
```

### 7.10 Hibernate cấm đổi @Id của managed entity

```
LỖI: Kafka consumer apply EmployeeCreatedEvent (identity tạo lại id mới, cùng
     employee_code) → JpaSystemException "identifier altered" → retry vô hạn
     → poison partition (projection đứng yên, smoke 404)
NGUYÊN NHÂN: findOrCreate re-key employee projection bằng setId() trên entity
             managed → Hibernate không cho đổi @Id của row đang attached
FIX: re-key = FK 4 bảng (shift_assignments, leave_entitlements,
     leave_employee_notes — V14 defer FK, leave_work_days — V15 defer FK)
     → delete(row cũ) → flush() →
     save(row mới với id từ event qua copyWithId);
     KHÔNG save() trong findOrCreate (caller save — tránh duplicate flush);
     insert row mới phải xong trước khi flush → tránh unique violation
     (uq_attendance_emp_code) khi row cũ chưa delete.
     LƯU Ý: thiếu defer FK bảng nào còn row → delete employee lỗi FK
     (poison partition, smoke shift 404).
```

### 7.11 Upload tài liệu — validate server-side (P0)

```
LỖI: FE chặn MIME/size nhưng có thể bypass (curl/Postman) → upload .pdf chứa HTML/JS,
     .exe, hoặc Content-Type giả; file được lưu + tải lại với header client gửi.
FIX: DocumentContentValidator (identity document) — whitelist extension + magic bytes:
     pdf %PDF-; jpg/jpeg FFD8FF; png 89504E47; gif GIF87a/89a; webp RIFF....WEBP;
     bmp BM; docx/xlsx/pptx/zip PK\x03\x04; doc/xls/ppt OLE D0CF11E0;
     txt/csv không có NUL; rtf {\rtf. SVG/HTML/JS/EXE bị loại.
     Content-Type lưu DB = chuẩn hóa theo extension (KHÔNG tin file.getContentType);
     filename gốc strip CR/LF/control + / \ " (chống header injection/path traversal).
     File > 10MB (Spring multipart) → 413 PAYLOAD_TOO_LARGE (GlobalExceptionHandler).
```

---

## 8. Key Files Reference

| Purpose | Path |
|---|---|
| Parent POM | `pom.xml` |
| Shared lib | `hrms-common/src/main/java/com/hrms/common/` |
| Avro schemas | `hrms-common/src/main/avro/` |
| Kafka topics | `hrms-common/src/main/java/com/hrms/common/kafka/KafkaTopics.java` |
| Outbox entity | `hrms-common/src/main/java/com/hrms/common/outbox/OutboxEvent.java` |
| Keycloak sync tasks (P0-6) | `identity-service/src/main/java/com/hrms/identity/keycloak/KeycloakSync*.java` |
| Keycloak sync ops API | `identity-service/src/main/java/com/hrms/identity/keycloak/KeycloakSyncTaskController.java` |
| Global exception | `hrms-common/src/main/java/com/hrms/common/web/GlobalExceptionHandler.java` |
| Document upload validator | `identity-service/src/main/java/com/hrms/identity/document/DocumentContentValidator.java` |
| Roles constants | `hrms-common/src/main/java/com/hrms/common/security/Roles.java` |
| Attendance IDOR scope helper | `attendance-service/src/main/java/com/hrms/attendance/employee/EmployeeAccess.java` (`EmployeeAccessResolver.java`) |
| Leave quota (settings/policy/period) | `attendance-service/src/main/java/com/hrms/attendance/leave/` |
| FE leave quota page | `frontend/src/pages/LeaveQuotaPage.tsx` |
| FE Cài đặt→Tham số/Chính sách phép | `frontend/src/pages/SettingsPoliciesPage.tsx` |
| FE Keycloak sync panel | `frontend/src/components/KeycloakSyncPanel.tsx` |
| FE API client | `frontend/src/api/client.ts` |
| FE types | `frontend/src/api/types.ts` |
| FE routes | `frontend/src/App.tsx` |
| FE auth context | `frontend/src/auth/AuthContext.tsx` |
| JWT audience mapper (P0-5) | `keycloak/realm-hrms.json` (client `hrms-spa`) + `scripts/kc-add-audience.sh` |
| JWT decoders (aud check) | `*/config/SecurityConfig.java` (`jwtValidator()`) |
| Smoke tests | `scripts/verify-*.sh` |

---

## 9. Adding New Feature Checklist

### Backend
- [ ] Entity extends `BaseEntity`, explicit `@Column(name=snake_case)`
- [ ] Flyway `V<n>__<feature>.sql` (append-only)
- [ ] Repository with derived queries + `@Query` for search
- [ ] DTO records: `<Feature>CreateRequest`, `<Feature>UpdateRequest`, `<Feature>Response`
- [ ] Mapper (MapStruct) hoặc static factory
- [ ] Service với `@Transactional`, outbox nếu cần publish event
- [ ] Controller: `/api/v1/...`, `@PreAuthorize`, `@Operation`
- [ ] Unit tests + MVC slice tests
- [ ] Nếu publish event: thêm Avro schema, update `OutboxMessageConverter`, `KafkaTopics`

### Frontend
- [ ] Types trong `api/types.ts`
- [ ] API functions trong `api/<resource>.ts`
- [ ] Page component `pages/<Feature>Page.tsx`
- [ ] i18n strings `i18n/locales/{vi,en}/<feature>.json`
- [ ] Route trong `App.tsx` với `ProtectedRoute`
- [ ] Role gating theo matrix §4.14 (`hasRole('ADMIN', ...)`)

### Verification
- [ ] Build: `docker exec hrms-dev mvn clean verify`
- [ ] Smoke test: `scripts/verify-<feature>.sh`
- [ ] Frontend build: `npm run build`
