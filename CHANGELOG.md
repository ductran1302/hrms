# CHANGELOG

## [P1 — Mask PII + MANAGER scoping] 2026-10-19

Nhóm fix bảo mật P1 cho `/api/v1/employees` (backend) + refresh token (frontend).
10 file — không thay đổi format API, chỉ siết dữ liệu theo vai trò.

### Nội dung thay đổi

**1. Backend — scoping: MANAGER chỉ thấy nhân viên trực thuộc + chính mình**
- `EmployeeViewer.java` (mới): bối cảnh người gọi — username/email từ JWT +
  cờ `hrAdmin` (lấy từ authorities, cùng nguồn với `@PreAuthorize`).
- `EmployeeRepository`: `search`/`countSearch` thêm `managerScope`/`viewerId`
  (nullable) — `managerScope=null` → toàn bộ (HR_ADMIN); khác null → chỉ
  `managerId = managerScope` hoặc `id = viewerId`.
- `EmployeeService`: MANAGER không khớp hồ sơ nhân viên nào → danh sách **rỗng**
  và chi tiết ngoài phạm vi → **403** (`AccessDeniedException` → `FORBIDDEN`,
  handler sẵn có trong `GlobalExceptionHandler`).
- `EmployeeController`: endpoint đọc (list/detail/history) nhận thêm
  `Jwt` + `Authentication` để xây viewer. Ghi vẫn chỉ HR_ADMIN (giữ nguyên).

**2. Backend — mask PII theo vai trò**
- `EmployeeResponse` trả về cho MANAGER (không phảiHR_ADMIN/chính chủ) bị mask:
  - `nationalId` → `****` + 4 số cuối (vd `****8901`)
  - `address` → `***`
  - `emergencyContactPhone` → `***`
- `phone` + `emergencyContactName/Relation` giữ nguyên (quan hệ công việc với
  nhân viên trực thuộc); xem chính mình/`/me`/HR_ADMIN → đầy đủ.
- Ghi đè giá trị mask không xảy ra được: form sửa nhân viên chỉ HR_ADMIN mở được.

**3. Frontend — bỏ refresh token theo interval**
- `AuthContext.tsx`: bỏ `setInterval(30s) updateToken(30)` — đua với
  refresh lazy trong axios interceptor (`api/client.ts`) có thể bắn 2 refresh
  đồng thời. Chỉ còn lazy refresh ngay trước mỗi request (đã có sẵn).

**4. Smoke tests + scripts**
- `scripts/verify-p1-scoping.sh` (mới, 32 checks): HR tạo quản lý/thuộc cấp/NV
  ngoài phạm vi → gán realm role MANAGER qua **service account `hrms-identity`**
  (client_credentials, secret từ env `HRMS_IDENTITY_CLIENT_SECRET`) → login
  mật khẩu mặc định → assert: list `total=2`, không thấy NV khác/seed, mask
  PII, self+`/me` đầy đủ, detail/history ngoài phạm vi 403 → cleanup nghỉ việc.
- `verify-identity.sh`: +3 check (MANAGER không hồ sơ → `total=0`;
  detail/history ngoài phạm vi → 403) — **199/199 PASS**.
- Env-override (mặc định giữ nguyên, devcontainer chạy không đổi):
  `API`/`KC` cho `verify-identity`, `verify-new-features`, `verify-seed-it`;
  `GW` cho `verify-gateway-attendance` — giúp chạy được từ container
  trong-network app stack (vd `API=http://gateway:8080`).

**5. Tests**
- `EmployeeServiceTest`: +6 test scoping/mask (list self+report mask,
  fail-closed rỗng, detail self full, report mask, ngoài phạm vi 403,
  HR full) — tổng 28 test.
- `EmployeeControllerTest`: cập nhật theo chữ ký mới + test `detail EMPLOYEE → 403`.

### ✅ Kết quả verify (chạy thật trên app stack trước khi commit)

- `mvn clean verify` 4 module (JDK 21 container): **BUILD SUCCESS**
  (identity 64/64, gateway 5/5, attendance 62/62)
- `tsc --noEmit` + `vite build`: **PASS**
- Smoke: `verify-identity` **199/199**, `verify-p1-scoping` **32/32**,
  `verify-new-features` **16/16**, `verify-users` **3/3**, seed-it/
  gateway-routes/gateway-attendance/pkce **ALL OK**, attendance **DONE**
  (E2E `409` ở lần chạy thứ 2 là cố định mã `NV9001` — pre-existing)
- SPA `:3000` CSP + security headers đầy đủ sau khi build lại image

### 📌 Hành vi mới cần biết

- **MANAGER thấy danh sách hẹp hơn**: chỉ nhân viên trực thuộc + chính mình.
  `manager1` (user smoke) không có hồ sơ nhân viên → thấy list rỗng,
  mọi chi tiết 403 — đúng semantics fail-closed.
- **MANAGER xem hồ sơ thuộc cấp thấy PII mask** (`****8901`/`***`) — số đầy
  đủ chỉ HR_ADMIN và chính chủ nhìn thấy.
- MANAGER có thể gán role qua realm API (verify-p1-scoping) không cần
  master admin password.

### ❌ Còn lại (candidate P2 / follow-up)

- Scope tiếp `contracts`/`documents` theo MANAGER: **đang có chủ đích open**
  (verify-identity assert MANAGER xem được contracts mọi NV với `salary=null`,
  org-chart thấy cả công ty) — muốn siết thì phải sửa cả script + UX org-chart.
- Seed passwords trong realm JSON, `temporary: false`, FE `check-sso`,
  JWT `aud` validation (giữ nguyên như mục P0).

---

## [P0 — Bảo mật] 2026-10-19

Nhóm fix bảo mật ưu tiên cao nhất (từ kết quả audit backend + frontend).
19 file, +234/−36 — không thay đổi API/luồng nghiệp vụ.

### Nội dung thay đổi

**1. Secrets không còn nằm trong git**
- `docker-compose.yml`, `docker-compose.app.yml`: `POSTGRES_PASSWORD`,
  `KEYCLOAK_ADMIN_PASSWORD` → fail-fast `${VAR:?message}` — thiếu `.env` thì
  compose dừng ngay với message hướng dẫn (không còn default `admin`/`hrms_dev_pw`).
- `identity-service/application.yml`, `attendance-service/application.yml`:
  bỏ default secret (`DB_PASSWORD`, `HRMS_DEFAULT_PASSWORD`,
  `HRMS_IDENTITY_CLIENT_SECRET`) → fail-fast như trên.
- `.env` (gitignored) là nơi chứa secret cục bộ; `.env.example` là template DEV-ONLY.
- `.devcontainer/docker-compose.dev.yml`: thêm `env_file: ../.env` (required: false)
  → jar chạy trong hrms-dev đọc được biến secret, **lệnh start không đổi**.

**2. KeycloakAdminClient: password-grant admin-cli → client_credentials (Service Account)**
- `keycloak/realm-hrms.json`: thêm client confidential `hrms-identity`
  (`serviceAccountsEnabled`, secret `identity-dev-secret-change-me` cho dev)
  + user service-account gắn `realm-management: realm-admin`.
- `KeycloakAdminClient.java`: lấy admin token qua `client_credentials` của
  `hrms-identity` (không còn giữ master admin password trong service);
  thiếu config → `IllegalState` với message rõ.
- `scripts/verify-new-features.sh`: bỏ hardcode `admin/admin`, lấy từ env.

**3. Security headers**
- Gateway (WebFlux SecurityConfig): `X-Frame-Options: DENY`, `Referrer-Policy`,
  nosniff; CORS `setAllowedHeaders` siết từ `*` xuống
  `Authorization, Content-Type, Accept, X-Correlation-Id, X-Requested-With`.
- Identity + Attendance (servlet SecurityConfig): thêm `Referrer-Policy`.
- `infra/nginx/frontend.conf` (SPA): CSP đầy đủ + `X-Content-Type-Options`,
  `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`.
- Swagger tắt ở app stack: `SPRINGDOC_API_DOCS/UI=false` trong
  `docker-compose.app.yml` (dev giữ nguyên, verify-swagger vẫn ALL OK).

**4. Frontend validate upload**
- `EmployeeProfile.tsx`: whitelist MIME/extension (PDF, ảnh, Word/Excel/PowerPoint,
  TXT/CSV/RTF/ZIP — chặn exe/sh/script/svg/html) + size ≤ 10MB trước khi gửi.
- i18n: `profile.docs.invalidType`, `profile.docs.fileTooLarge` (vi/en).

**5. Tài liệu**: README (bước `cp .env.example .env`, mô tả client `hrms-identity`,
mục migration), AGENTS.md, `.gitignore` thêm `session-*.md`.

---

### 🔧 MACHINE MỚI pull code về

```bash
cp .env.example .env     # BẮT BUỘC — compose/application.yml đã fail-fast
docker compose up -d --wait
# Dev container: reopen in container (tự nạp .env)
# App stack: docker compose -f docker-compose.app.yml up -d --build
# Các lệnh dev/smoke khác: GIỮ NGUYÊN như trước
```

### ⚠️ MACHINE ĐÃ CÓ stack cũ — migration BẮT BUỘC

1. `cp .env.example .env` (nếu chưa có — giữ giá trị dev trong file mẫu).
2. **`docker compose down -v`** rồi `docker compose up -d --wait`
   → realm Keycloak PHẢI import lại để có client `hrms-identity`.
   Không làm bước này: tạo/nghỉ nhân viên sẽ lỗi (identity không lấy được admin token).
3. Mở lại Dev Container (env_file `.env` mới).
4. Nếu host đã kẹt port 5432 (project khác): sửa `POSTGRES_PORT` trong `.env`
   (vd. `5543`) — service trong network vẫn kết nối `postgres:5432` bình thường.

### 📌 Quy ước secret

- `.env` **KHÔNG BAO GIỜ commit** (đã ở `.gitignore`).
- Giá trị trong `.env.example` là DEV-ONLY — production phải sinh giá trị random mạnh.
- Dev giữ `HRMS_DEFAULT_PASSWORD=hrms@123` đúng như mẫu → `scripts/verify-*.sh`
  (login bằng password này) mới pass.
- Đổi client secret phải đổi **cả 2 chỗ**: `HRMS_IDENTITY_CLIENT_SECRET` (.env)
  và `secret` trong `keycloak/realm-hrms.json` (rồi `down -v` để import lại).
- Seed users `hr@123`, `manager@123`, `emp@123` vẫn nằm trong realm JSON —
  là tài khoản smoke test có chủ đích, KHÔNG dùng cho production.

### ✅ Kết quả verify (chạy thật trước khi commit)

- `mvn test` toàn bộ 4 module (JDK 21 container): **BUILD SUCCESS**
- `tsc --noEmit` + `vite build`: **PASS**
- Keycloak 25.0.6 import realm mới: client `hrms-identity` + service-account
  `realm-admin` hoạt động (tạo user 201, gán role 204, disable 204)
- 8/8 smoke scripts: `verify-identity` **196/196**, `verify-new-features`
  **16/16**, `verify-users` **3/3**, seed-it/swagger/gateway-routes/
  gateway-attendance/pkce **ALL OK**
- App stack: springdoc 404, nginx CSP + headers đầy đủ, gateway/service headers đúng
- E2E qua app gateway: tạo NV (201) → login mật khẩu mặc định → nghỉ việc (204)
  → tài khoản Keycloak bị tắt → login bị chặn

### ❌ Không thuộc thay đổi này (candidate P1/P2)

- Seed passwords vẫn trong realm JSON (bắt buộc cho `--import-realm`).
- `temporary: false` cho user mới (bật sẽ phá smoke test password-grant).
- FE chuyển `login-required` → `check-sso` (UX, không phải bảo mật).
- JWT `aud` validation (cần audience mapper ở realm trước).
