# HRMS — Human Resource Management System

Hệ thống quản lý nhân sự kiến trúc microservice: **Spring Boot 3 + React 18 + Kafka + PostgreSQL + Keycloak**.

> **Trạng thái: HOÀN TẤT lộ trình ①–⑨.** Bước 9: identity-core — phòng ban, hợp đồng (lương che theo role), lịch sử job info, org chart, hồ sơ nhân viên mở rộng + phân trang/tìm kiếm. Trước đó: Bước 8 Docker/app stack; Bước 7 React frontend; Bước 6 attendance + springdoc.

## Kiến trúc

```
[React SPA] ──> [API Gateway] ──> [Microservices]
                                       │
        [Keycloak]  [Kafka + Schema Registry]  [Postgres per service]
                                       │
                    [Zipkin] [kafka-ui] ──>  Docker Compose (dev)
```

**Nguyên tắc:**
- Mỗi service 1 database PostgreSQL riêng — không share DB, không join chéo.
- Đồng bộ giữa service chỉ qua Kafka event (không gọi REST chéo).
- Producer ghi business data + outbox trong **cùng transaction** → poller xuất bản sang Kafka (không mất event).
- JWT do Keycloak cấp, Gateway validate, mọi service đọc role từ token.

## Yêu cầu

| Công cụ | Phiên bản | Ghi chú |
|---|---|---|
| Docker + Compose | ≥ 24 / v2 | **Bắt buộc** |
| JDK | — | **Không cần cài.** Build/run Java nằm trong dev container (JDK 21) |
| Node | — | **Không cần cài.** FE chạy trong dev container (Node 22) |
| VS Code + Dev Containers | — | Để mở repo trong container |

Máy bạn có thể có sẵn Java 8 cho các project khác — project này **không đụng vào `JAVA_HOME` hệ thống**.

## Dev container

```powershell
# Từ VS Code: Ctrl+Shift+P → "Dev Containers: Reopen in Container"
# Hoặc CLI:
npx @devcontainers/cli up --workspace-folder .
npx @devcontainers/cli exec --workspace-folder . bash
```

Bên trong container đã có sẵn: **JDK 21, Maven 3.8.7, Node 22.23.2, npm 12, git, ripgrep**.
Container join network `hrms_default` nên kết nối trực tiếp bằng hostname:
`postgres:5432`, `kafka:29092`, `schema-registry:8081`, `keycloak:8080`.

```powershell
docker exec hrms-dev mvn -v      # verify toolchain
docker compose logs -f           # log hạ tầng (chạy ở host)
```

## Bắt đầu nhanh

```powershell
# 1. Khởi động hạ tầng — chạy từ HOST (6 container)
docker compose up -d
docker compose ps          # tất cả healthy

# 2. Mở VS Code → Ctrl+Shift+P → "Dev Containers: Reopen in Container"
#    (dev container tự build image hrms-dev: JDK21 + Maven + Node)
```

> Hạ tầng chạy ở **host** (Docker Desktop), dev container join cùng network `hrms_default`.
> Hai process compose độc nhau: `docker compose` (root) cho infra,
> `.devcontainer/docker-compose.dev.yml` cho dev container.

Mở:

| Dịch vụ | URL | Ghi chú |
|---|---|---|
| Kafka UI | http://localhost:8090 | — |
| Zipkin | http://localhost:9411 | — |
| Schema Registry | http://localhost:8082/subjects | — |
| Keycloak (realm hrms) | http://localhost:8081/realms/hrms | issuer + discovery |
| Frontend (Vite dev) | http://localhost:5173 | cần `npm run dev` trong container |
| Swagger UI | http://localhost:8080/swagger-ui.html | identity (:8080), attendance (:8086) |

> **Về Keycloak Admin Console:** chưa cần cho giai đoạn này. Toàn bộ cấu hình realm
> (client, roles, users) nằm trong `keycloak/realm-hrms.json` và được import tự động.
> Backend chỉ cần **JWKS/issuer** để validate JWT — hoàn toàn không phụ thuộc console.
> Khi nào thật sự cần quản trị qua UI, sẽ xử lý ở bước riêng (import kèm admin user).

### Người dùng Keycloak (realm `hrms`)

| Username | Password | Roles |
|---|---|---|
| `hr` | `hr@123` | `HR_ADMIN`, `EMPLOYEE` |
| `manager1` | `manager@123` | `MANAGER`, `EMPLOYEE` |
| `employee1` | `emp@123` | `EMPLOYEE` |

OAuth client: `hrms-spa` (public + PKCE), redirect `http://localhost:5173/*`.

### Lấy token (dùng cho việc test API)

```powershell
$body = 'client_id=hrms-spa&grant_type=password&username=hr&password=hr@123&scope=openid profile email'
$t = Invoke-RestMethod -Uri 'http://localhost:8081/realms/hrms/protocol/openid-connect/token' `
       -Method Post -ContentType 'application/x-www-form-urlencoded' -Body $body
$t.access_token
```

## Bảng Service & Port

| Container | Host port | Purpose |
|---|---|---|
| `hrms-postgres` | 5432 | 4 DB: `identity_db`, `attendance_db`, `payroll_db`, `notification_db` |
| `hrms-kafka` | 9092 (host) / 29092 (in-network) | KRaft, 1 node |
| `hrms-schema-registry` | 8082 | Avro schema registry |
| `hrms-keycloak` | 8081 | OIDC / JWT issuer |
| `hrms-zipkin` | 9411 | Distributed tracing |
| `hrms-kafka-ui` | 8090 | Kafka admin UI |

> **Quy ước kết nối:** service chạy trong network compose dùng hostname `kafka:29092`, `postgres:5432`, `keycloak:8080`. Host dùng `localhost` + port ở trên. **Schema Registry luôn trỏ `localhost:8082`** (dùng làm cả advertised, tránh lỗi container không phân biệt được host/container).

## Lệnh thường dùng

```powershell
docker compose ps                     # trạng thái
docker compose logs -f keycloak       # log 1 service
docker compose down                   # dừng (giữ data volume)
docker compose down -v                # dừng + XÓA data (init.sql chạy lại lần sau)
docker compose up -d --wait           # start và đợi tất cả healthy
```

### Reset Keycloak realm

Realm chỉ được import khi chưa tồn tại. Muốn import lại:

```powershell
docker compose down -v
docker compose up -d --wait
```

## Cấu trúc thư mục

```
HRMS/
├── docker-compose.yml          # hạ tầng dev (chạy từ host)
├── docker-compose.app.yml      # app stack production-style (4 services, join hrms_default)
├── .dockerignore               # lọc target/, node_modules, .git khi build image
├── .devcontainer/
│   ├── devcontainer.json       # VS Code Dev Container config
│   ├── Dockerfile              # JDK 21 + Maven + Node (build image hrms-dev)
│   └── docker-compose.dev.yml  # service dev, join network hrms_default
├── .env.example                # biến cấu hình (copy thành .env nếu muốn ghi đè)
├── infra/postgres/init.sql     # tạo các database per-service
├── infra/nginx/frontend.conf   # nginx SPA + proxy /api/ → gateway
├── keycloak/realm-hrms.json    # realm, client, roles, user mẫu
├── pom.xml                     # parent POM (Boot 3.3, Java 21, multi-module)
├── hrms-common/                # lib chung: BaseEntity, error handler, Avro event,
│   │                           #   Kafka config, outbox, security
│   └── src/main/avro/          #   event schema (record name strategy)
├── identity-service/           # CRUD nhân viên + Transactional Outbox → Kafka (Dockerfile)
│   └── src/main/resources/db/migration/  # Flyway
├── gateway-service/            # Spring Cloud Gateway — route + JWT + CORS (Dockerfile)
├── attendance-service/         # consumer Kafka idempotent + DLQ (Dockerfile)
│   └── src/main/resources/db/migration/  # Flyway (attendance_db)
├── frontend/                   # React 18 + Vite + AntD v5 + ProComponents (Dockerfile → nginx)
│   └── src/{auth,api,pages,layouts}/
├── scripts/verify-identity.sh  # smoke test API (auth + CRUD + RBAC)
├── scripts/verify-attendance.sh # smoke test attendance (RBAC + E2E qua gateway)
├── scripts/verify-gateway-attendance.sh # route gateway → attendance (200/401/403)
├── scripts/verify-swagger.sh   # kiểm tra springdoc + bearerAuth (identity + attendance)
├── scripts/verify-frontend-pkce.sh # E2E PKCE: authorize → login → token → gateway API
└── scripts/fix-esbuild.sh      # npm 12 chặn postinstall esbuild → approve + rebuild
```

## Build & test

```powershell
# Chạy từ host — build diễn ra trong dev container (JDK 21)
docker exec hrms-dev mvn -f /workspaces/HRMS/pom.xml clean verify
```

> **Avro 1.12 class-security:** Avro chặn deserialize class không được khai báo tin tưởng.
> Project đã cấu hình sẵn: `org.apache.avro.SERIALIZABLE_PACKAGES=com.hrms`
> (surefire cho test; `KafkaAvroConfiguration` set lúc khởi động cho runtime).
> Khi thêm module/package mới nằm ngoài `com.hrms`, phải thêm vào property này.

### Chạy identity-service (dev)

```powershell
# build (chạy trong dev container)
docker exec hrms-dev mvn -f /workspaces/HRMS/pom.xml clean install -DskipTests

# chạy nền + xem log (port 8080 trong dev container, không publish ra host)
docker exec -d hrms-dev bash -lc "java -jar /workspaces/HRMS/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar > /tmp/identity.log 2>&1"
docker exec hrms-dev tail -f /tmp/identity.log

# smoke test: 401/403/201/409/422 + outbox → Kafka (cần hạ tầng đang chạy)
docker exec hrms-dev bash /workspaces/HRMS/scripts/verify-identity.sh
```

| Endpoint | Method | Role |
|---|---|---|
| `/api/v1/employees` | POST | `HR_ADMIN` |
| `/api/v1/employees`, `/api/v1/employees/{id}` | GET | `HR_ADMIN`, `MANAGER` |
| `/api/v1/employees/{id}` | PATCH | `HR_ADMIN` |
| `/api/v1/employees/{id}` | DELETE (nghỉ việc, soft) | `HR_ADMIN` |

> **Keycloak issuer:** compose đặt `KC_HOSTNAME=http://localhost:8081` → token luôn có
> `iss=http://localhost:8081/realms/hrms` dù request qua `localhost:8081` (host) hay
> `keycloak:8080` (in-network). Service tự build `JwtDecoder`: JWKS lấy in-network,
> validate `iss` theo giá trị cố định (`hrms.keycloak.issuer`).

### Chạy gateway-service (dev)

Gateway **không phụ thuộc `hrms-common`** (WebFlux) — tự khai báo JWT decoder, CORS,
correlation filter riêng. Trong dev container chạy `SERVER_PORT=8085` (8080 đã cho
identity); compose app (Bước 8) sẽ map container 8080 → host 8080.

```powershell
docker exec -d hrms-dev bash -lc "SERVER_PORT=8085 java -jar /workspaces/HRMS/gateway-service/target/gateway-service-0.1.0-SNAPSHOT.jar > /tmp/gateway.log 2>&1"
docker exec hrms-dev tail -f /tmp/gateway.log
```

| Route | Predicate | Upstream |
|---|---|---|
| `identity-employees` | `Path=/api/v1/employees/**` | `IDENTITY_URI` (mặc định `http://localhost:8080`) |
| `attendance-employees` | `Path=/api/v1/attendance/**` | `ATTENDANCE_URI` (mặc định `http://localhost:8086`) |

- **JWT** validate tại gateway (JWKS + issuer) rồi **forward `Authorization`** xuống
  service — identity re-validate và enforce role (403 tại service, không phải gateway).
- **CORS** khai báo trong `SecurityConfig` (Security filter chain chạy trước gateway;
  khai báo ở `spring.cloud.gateway.globalcors` sẽ bị Security từ chối preflight 403).
- **`X-Correlation-Id`**: filter ở gateway sinh/echo về client và truyền xuống service
  con (kiểm chứng được qua `correlationId` trong body lỗi của identity).
- Preflight test phải dùng client thật (`WebTestClient.bindToServer`) — mock client
  của Boot có URI tương đối, phá `CorsUtils.isSameOrigin`.

### Chạy attendance-service (dev)

Service đọc event `hrms.employee.v1` từ Kafka (cùng partition key = employeeId → thứ tự theo
nhân sự được giữ) và ghi projection vào `attendance_db`. Idempotent theo `eventId` (bảng
`processed_events`) — replay không tạo trùng. Lỗi xử lý retry 3 lần rồi đẩy vào DLQ
`hrms.employee.v1.DLQ`; message lỗi deserialization (poison pill) được đẩy sang DLQ dạng
`byte[]` gốc (không chết container).

```powershell
docker exec -d hrms-dev bash -lc "SERVER_PORT=8086 java -jar /workspaces/HRMS/attendance-service/target/attendance-service-0.1.0-SNAPSHOT.jar > /tmp/attendance.log 2>&1"
docker exec hrms-dev tail -f /tmp/attendance.log

# smoke test: RBAC 401/200/403 + E2E tạo nhân viên qua gateway → projection (cần hạ tầng + identity + gateway đang chạy)
docker exec hrms-dev bash /workspaces/HRMS/scripts/verify-attendance.sh
```

| Endpoint | Method | Role |
|---|---|---|
| `/api/v1/attendance/employees` | GET | `HR_ADMIN`, `MANAGER` |
| `/api/v1/attendance/employees/{employeeCode}` | GET | `HR_ADMIN`, `MANAGER` |

### Swagger UI + Bearer Auth (springdoc)

Mỗi service tự phục vụ Swagger UI (per-service, không aggregate qua gateway):

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| identity (8080) | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| attendance (8086) | http://localhost:8086/swagger-ui.html | http://localhost:8086/v3/api-docs |

Cách dùng: mở Swagger UI → bấm **Authorize** → dán `access_token` lấy theo mục
"Lấy token" ở trên (scheme `bearerAuth`, JWT) → **Try it out**.

```powershell
# kiểm tra spec + security scheme
docker exec hrms-dev bash /workspaces/HRMS/scripts/verify-swagger.sh
```

> **Lưu ý version:** `kafka-schema-registry-client` (Confluent) kéo `swagger-annotations:2.1.10`
> trùng package với `swagger-annotations-jakarta` của springdoc → `NoSuchMethodError` trên
> `/v3/api-docs`. Parent POM đã pin `swagger-annotations` = 2.2.22 trong `dependencyManagement`.

### Chạy frontend (dev)

React 18 + Vite + TypeScript, UI **AntD v5 + ProComponents**, đăng nhập qua Keycloak
(client `hrms-spa`, PKCE S256, `onLoad: login-required` → tự redirect tới trang login).
API gọi thẳng gateway `http://localhost:8085` (CORS đã allow origin `http://localhost:5173`).

```powershell
# cài deps (chỉ lần đầu — npm 12 chặn postinstall của esbuild, phải approve)
docker exec hrms-dev bash -lc "cd /workspaces/HRMS/frontend && npm install"
docker exec hrms-dev bash /workspaces/HRMS/scripts/fix-esbuild.sh

# chạy dev server (nền, publish host port 5173)
docker exec -d hrms-dev bash -lc "cd /workspaces/HRMS/frontend && npm run dev > /tmp/vite.log 2>&1"
docker exec hrms-dev tail -f /tmp/vite.log

# build production (tsc strict + vite build)
docker exec hrms-dev bash -lc "cd /workspaces/HRMS/frontend && npm run build"
```

Mở **http://localhost:5173** → tự redirect Keycloak → đăng nhập `hr` / `hr@123`:

| Trang | Route | Quyền |
|---|---|---|
| Nhân sự (ProTable CRUD) | `/nhan-su` | `HR_ADMIN` thêm/sửa/nghỉ việc; `MANAGER` xem |
| Chấm công (projection Kafka) | `/cham-cong` | `HR_ADMIN`, `MANAGER` |

Smoke test E2E PKCE không cần browser (authorize → login form → code → token → gateway):

```powershell
docker exec hrms-dev bash /workspaces/HRMS/scripts/verify-frontend-pkce.sh
```

> **Port:** `.devcontainer/docker-compose.dev.yml` publish `5173:5173` (VS Code `forwardPorts`
> cũng có sẵn). Đổi port thì sửa thêm `redirectUris`/`webOrigins` trong `keycloak/realm-hrms.json`.
> Env override: `VITE_KEYCLOAK_URL`, `VITE_API_BASE` (mặc định `http://localhost:8081`, `http://localhost:8085`).

### Tracing (Zipkin)

Ba backend đều có Micrometer Tracing (brave) → Zipkin endpoint in-network
(`http://zipkin:9411/api/v2/spans`), log pattern
`[service,%X{traceId:-},%X{spanId:-},%X{correlationId:-}]`:

```powershell
# lấy trace (sau khi gọi vài API)
Invoke-RestMethod "http://localhost:9411/api/v2/traces?serviceName=identity-service&limit=3"
```

Đã verify: gateway ↔ identity ↔ attendance có span (trace sample 7 span/trace).

### Docker images & app stack

Multi-stage Dockerfile (build trong image, host không cần JDK/Node):

| Dockerfile | Build stage | Runtime |
|---|---|---|
| `identity/gateway/attendance-service/Dockerfile` | `maven:3.9-eclipse-temurin-21` | `eclipse-temurin:21-jre-jammy` |
| `frontend/Dockerfile` | `node:22-bookworm-slim` | `nginx:1.27-alpine` (SPA + proxy `/api/` → gateway) |

```powershell
# hạ tầng (docker-compose.yml) phải đang chạy trước
docker compose -f docker-compose.app.yml up -d --build
```

| Endpoint | URL | Ghi chú |
|---|---|---|
| App gateway | http://localhost:8080 | route identity + attendance |
| App frontend | http://localhost:3000 | nginx same-origin proxy → gateway |

Flow: browser → nginx (:3000, serve SPA + `/api/**` proxy → gateway) → gateway →
`identity:8080` / `attendance:8080` (in-network). Bundle build với `VITE_API_BASE=""`
→ API relative, **không cần CORS**. Keycloak realm đã có redirect `http://localhost:3000/*`.

```powershell
docker compose -f docker-compose.app.yml down            # dừng app stack (giữ hạ tầng)
docker compose -f docker-compose.app.yml up -d --build   # build lại khi đổi code
```

> **Xung đột port 8080:** khi app stack chạy, `localhost:8080` là **app gateway** —
> Swagger UI dev (identity :8080) chỉ truy cập từ trong dev container
> (`docker exec hrms-dev curl http://localhost:8080/swagger-ui/index.html`).
> Dừng app stack để dùng lại Swagger dev qua host.

> **Keycloak admin:** realm `hrms` import tự động từ `keycloak/realm-hrms.json`
> (không cần Admin Console). Muốn import lại: xóa volume keycloak rồi
> `docker compose up -d keycloak` — users/secrets re-import từ JSON, mất session cũ.

## Lộ trình

- [x] **Bước 1** — Hạ tầng: Postgres, Kafka, Schema Registry, Keycloak, Zipkin, Kafka UI
- [x] **Bước 2** — Dev container (JDK 21 + Maven + Node trong container)
- [x] **Bước 3** — Parent POM + `hrms-common` (Avro event, outbox, error handler, security, Kafka config)
- [x] **Bước 4** — `identity-service` + Transactional Outbox → Kafka
- [x] **Bước 5** — `gateway-service` (route + JWT + CORS + correlation)
- [x] **Bước 6** — `attendance-service` (consumer idempotent + DLQ) + springdoc Swagger UI / Bearer Auth
- [x] **Bước 7** — React frontend (Vite + AntD v5 + ProComponents, Keycloak PKCE)
- [x] **Bước 8** — Tracing (verify Zipkin) + Dockerfile multi-stage + `docker-compose.app.yml`
- [x] **Bước 9** — identity-core (tham khảo SF4C/SAP SuccessFactors EC):
  - Migration `V2`: bảng `departments`, `contracts`, `employee_job_history`;
    `employees` thêm personal info (birth_date/gender/phone/national_id/address);
    `department_id` → uuid FK, `manager_id` → uuid self-FK (org chart).
  - API: `GET/POST/PATCH/DELETE /api/v1/departments` (+ `/tree`), `/api/v1/contracts`
    + `/api/v1/employees/{id}/contracts` (POST/PATCH/terminate — **lương chỉ HR_ADMIN
    thấy**, 1 hợp đồng ACTIVE/nhân viên), `/api/v1/employees/{id}/history`,
    `GET /api/v1/org-chart`; `GET /api/v1/employees` đổi sang **PageResponse**
    (`page,size,status,departmentId,q`).
  - Ghi lịch sử job info (phòng ban/vị trí/quản lý/trạng thái/luồng hợp đồng) khi
    thay đổi; đổi tên phòng ban → đồng bộ `department_name` của nhân viên + outbox.
  - Gateway thêm route `identity-api` (`Path=/api/v1/**` → identity, sau attendance).
  - FE: trang Phòng ban, Hợp đồng, Sơ đồ tổ chức (`Tree`), Hồ sơ nhân viên 3 tab
    (Thông tin/Hợp đồng/Lịch sử); danh sách NV tìm kiếm + lọc phòng ban + phân trang.
  - Verify: `scripts/verify-identity.sh` (39/39), `scripts/verify-gateway-routes.sh`.
- [x] **Bước 10** — Seed data phòng IT (`identity-service` `DataSeeder`, tự chạy khi
  khởi động, idempotent — tắt bằng `hrms.seed.enabled=false`): cây 3 cấp
  (Giám đốc CNTT → 3 trưởng bộ phận Backend/Frontend/OPS → 4 nhân viên), mã `NVIT001–008`,
  gán cả `department.manager_id` + `employees.manager_id` để màn **Sơ đồ tổ chức**
  hiển thị cây ngay. Tạo qua Service → outbox → attendance sync. Verify:
  `scripts/verify-seed-it.sh` (root + 3 cấp + đếm = 8, restart không nhân đôi).
