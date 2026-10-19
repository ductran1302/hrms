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
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-identity.sh"
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-p1-scoping.sh"
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-attendance.sh"
docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-gateway-routes.sh"

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
  @PreAuthorize("hasRole('HR_ADMIN')")
  @Operation(summary = "Tạo nhân viên mới")
  public EmployeeResponse create(@Valid @RequestBody EmployeeCreateRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
    return employeeService.create(request, jwt.getSubject());
  }

  @PatchMapping("/{id}")  // Partial update → PATCH, not PUT
  @PreAuthorize("hasAnyRole('HR_ADMIN','MANAGER')")
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
  
  public static String dlq(String topic) {
    return topic + ".DLQ";
  }
}
```

**Pattern:** `hrms.<aggregate>.v1` (version suffix), RecordNameStrategy → nhiều event types chung 1 topic.

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
  const canWrite = hasRole('HR_ADMIN');
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
- Role gating: `const canWrite = hasRole('HR_ADMIN')`
- Message: `AntApp.useApp()` → `message.success()`, không import trực tiếp

### 5.5 Routing & Auth

```tsx
// App.tsx
<Route path="/" element={<ProtectedRoute><AppLayout/></ProtectedRoute>}>
  <Route index element={<Navigate to="/employees" replace/>}/>
  <Route path="employees" element={
    <ProtectedRoute roles={['HR_ADMIN', 'MANAGER']}>
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

**Tài khoản NV auto-provision:** mật khẩu mặc định `<employeeCode>@hrms` (suffix cấu hình `hrms.keycloak.password-suffix`, env `HRMS_PASSWORD_SUFFIX`); tạo khi Tạo nhân viên + idempotent khi Tạo hợp đồng. API Cài đặt: `GET /api/v1/accounts`, `POST /api/v1/accounts/change-password`, `PATCH /api/v1/accounts/{username}/password|enabled`.

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

---

## 8. Key Files Reference

| Purpose | Path |
|---|---|
| Parent POM | `pom.xml` |
| Shared lib | `hrms-common/src/main/java/com/hrms/common/` |
| Avro schemas | `hrms-common/src/main/avro/` |
| Kafka topics | `hrms-common/src/main/java/com/hrms/common/kafka/KafkaTopics.java` |
| Outbox entity | `hrms-common/src/main/java/com/hrms/common/outbox/OutboxEvent.java` |
| Global exception | `hrms-common/src/main/java/com/hrms/common/web/GlobalExceptionHandler.java` |
| Roles constants | `hrms-common/src/main/java/com/hrms/common/security/Roles.java` |
| FE API client | `frontend/src/api/client.ts` |
| FE types | `frontend/src/api/types.ts` |
| FE routes | `frontend/src/App.tsx` |
| FE auth context | `frontend/src/auth/AuthContext.tsx` |
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
- [ ] Role gating: `hasRole('HR_ADMIN')`

### Verification
- [ ] Build: `docker exec hrms-dev mvn clean verify`
- [ ] Smoke test: `scripts/verify-<feature>.sh`
- [ ] Frontend build: `npm run build`
