# HRMS — Human Resource Management System

Hệ thống quản lý nhân sự kiến trúc microservice: **Spring Boot 3 + React 18 + Kafka + PostgreSQL + Keycloak**.

> **Trạng thái: Bước 3 — `hrms-common` hoàn tất (build + 18 tests pass).** Bước 4: `identity-service`.

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
├── .devcontainer/
│   ├── devcontainer.json       # VS Code Dev Container config
│   ├── Dockerfile              # JDK 21 + Maven + Node (build image hrms-dev)
│   └── docker-compose.dev.yml  # service dev, join network hrms_default
├── .env.example                # biến cấu hình (copy thành .env nếu muốn ghi đè)
├── infra/postgres/init.sql     # tạo các database per-service
├── keycloak/realm-hrms.json    # realm, client, roles, user mẫu
├── pom.xml                     # parent POM (Boot 3.3, Java 21, multi-module)
├── hrms-common/                # lib chung: BaseEntity, error handler, Avro event,
│   │                           #   Kafka config, outbox, security
│   └── src/main/avro/          #   event schema (record name strategy)
├── gateway-service/            # (TODO)
├── identity-service/           # (TODO)
├── attendance-service/         # (TODO)
└── frontend/                   # (TODO)
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

## Lộ trình

- [x] **Bước 1** — Hạ tầng: Postgres, Kafka, Schema Registry, Keycloak, Zipkin, Kafka UI
- [x] **Bước 2** — Dev container (JDK 21 + Maven + Node trong container)
- [x] **Bước 3** — Parent POM + `hrms-common` (Avro event, outbox, error handler, security, Kafka config)
- [ ] **Bước 4** — `identity-service` + Transactional Outbox → Kafka
- [ ] **Bước 5** — `gateway-service` (route + JWT)
- [ ] **Bước 6** — `attendance-service` (consumer idempotent + DLQ)
- [ ] **Bước 7** — React frontend (AntD + ProComponents)
- [ ] **Bước 8** — Tracing + Dockerfile multi-stage + `docker-compose.app.yml`
