# CHANGELOG

## [5 role ADMIN/GLOBAL_LEAD/DEPT_LEAD/MANAGER/EMPLOYEE + scoping] 2026-10-20

Rename `HR_ADMIN`→`ADMIN`, thêm `GLOBAL_LEAD`/`DEPT_LEAD`, auto-assign role khi tạo
tài khoản (creation-only), sửa roles ở Cài đặt→Tài khoản, mở rộng scoping đọc NV
(subtree phòng ban) cho cả identity lẫn attendance (event `hrms.department.v1` +
projection). Q&A matrix đầy đủ trong AGENTS §4.14.

### Nội dung thay đổi

**1. Roles & scoping (hrms-common + identity)**
- `Roles.java`: 5 role chuẩn + `all()`/`level()`/`maxLevel()`; sweep `HR_ADMIN`→`ADMIN`
  toàn bộ backend + FE + realm (KHÔNG còn chỗ nào).
- `EmployeeScope` (common/security — dùng chung identity + attendance): `ALL`/
  `DEPARTMENT`/`SELF`, `fromAuthorities(jwt)`, fail-closed `SELF` khi không nhận ra role.
- `EmployeeViewer` (identity): wrap username/email/scope — `unrestricted()`/`maskOthers()`;
  EmployeeService: `departmentScope(...)` BFS descendants phòng ACTIVE mình quản lý,
  `assertWriteScope` (DEPT_LEAD KHÔNG được ghi self — chống tự sửa dept mở rộng quyền;
  xem self vẫn full PII).
- `EmployeeRepository.search/countSearch` + `deptScope`; `DepartmentRepository` +
  queries theo manager; `ContractService.isSalaryVisible` = ADMIN + GLOBAL_LEAD
  (DEPT_LEAD/MANAGER mask lương hợp đồng).
- @PreAuthorize matrix toàn bộ controller identity + attendance: reads =
  ADMIN/GLOBAL_LEAD/DEPT_LEAD/MANAGER (+EMPLOYEE cho my-*), writes = ADMIN
  (+GLOBAL_LEAD/DEPT_LEAD cho employee update), **tạo NV = ADMIN duy nhất**.

**2. Auto-assign role (creation-only)**
- `RoleAssignmentService.resolveDefaultRole`: manager dept `parent=null` ACTIVE →
  `GLOBAL_LEAD` > manager dept ACTIVE bất kỳ → `DEPT_LEAD` > có reports ACTIVE →
  `MANAGER` > `EMPLOYEE`. Gọi lúc Tạo nhân viên + idempotent khi Tạo hợp đồng.

**3. PATCH `/api/v1/accounts/{username}/roles` (ADMIN, 204)**
- `KeycloakAdminClient.realmRolesByUsername` (bỏ `default-roles-*`) + `updateRealmRoles`
  (diff add/remove); service validate subset `Roles.all()` (422) + chặn tự hạ mình
  khi target==actor (422).
- FE: `SettingsAccountsPage` modal sửa roles (5 checkbox, chặn self như enable/disable),
  `updateAccountRoles` API; menu/route Cài đặt→Tài khoản chỉ ADMIN.

**4. DEPARTMENT_V1 event + attendance departments projection**
- Avro `DepartmentCreatedEvent`/`DepartmentUpdatedEvent` (namespace
  `com.hrms.common.avro.department`), topic `hrms.department.v1`, outbox từ
  `DepartmentService` (cùng transaction, actorId = jwt subject).
- Attendance: Flyway `V7__departments.sql` + entity/repository + `DepartmentService`
  (`applyCreated/applyUpdated` idempotent qua `processed_events`, `managedSubtreeIds`
  BFS) + `DepartmentEventConsumer`.
- Scope attendance list: DEPT_LEAD → subtree ∪ reports ∪ self; ALL/SELF giữ nguyên
  (MANAGER không đổi hành vi).

**5. KC realm + scripts**
- `keycloak/realm-hrms.json`: 5 roles + groups HR/Board/DeptLeads/Managers;
  user `hr` realmRoles `["ADMIN"]`.
- `scripts/kc-init-roles.sh` (mới): rename HR_ADMIN→ADMIN trên KC đang chạy
  (giữ role ID) + tạo GLOBAL_LEAD/DEPT_LEAD + group HR=[ADMIN]; idempotent
  (POST add / DELETE remove — KC không có PUT role-mappings).
- `scripts/verify-roles.sh` (mới, 22 check): 5 realm roles, auto-assign EMPLOYEE,
  PATCH 204/422 (role lạ, tự hạ mình)/403 (non-ADMIN), scoping 403/200 identity +
  attendance.

**6. Regression (2026-10-20)**
- `mvn clean verify`: 181 tests xanh (18+96+5+62, 0 fail).
- Smoke qua app stack: verify-roles 22/22, verify-identity 222/222,
  verify-p1-scoping 32/32, verify-attendance DONE, verify-gateway-routes ALL OK.
- FE `tsc --noEmit` + `vite build` pass; app stack identity/attendance/frontend
  đã rebuild image.
- Pitfall mới (AGENTS §7.2): từ trong dev container smoke PHẢI override
  `API=http://gateway:8080` (localhost:8080 ≠ app gateway).

## [Cài đặt — Tài khoản/Bảo mật + auto-provision tài khoản NV] 2026-10-19

Màn **Cài đặt** (2 trang *Tài khoản* + *Bảo mật*), tự tạo tài khoản Keycloak khi
tạo nhân viên với mật khẩu mặc định `<ma>@hrms`, API quản lý tài khoản/đổi mật
khẩu/reset. Ghép nhánh với 2 commit bảo mật P0/P1 trước đó (giữ nguyên
client_credentials của P0).

### Nội dung thay đổi

**1. Backend — Accounts API (mới, `identity-service` package `account`)**
- `AccountController` (`/api/v1/accounts`): `GET` danh sách (HR_ADMIN),
  `POST /change-password` (self, cần mật khẩu hiện tại),
  `PATCH /{username}/password` + `PATCH /{username}/enabled` (HR_ADMIN;
  không tự tắt/tự reset chính mình → 422; reset EMPLOYEE không phải mình → 403).
- `AccountService`: gọi Keycloak Admin API; validate mật khẩu mới ≥ 8 ký tự.
- DTOs: `AccountResponse`, `ChangePasswordRequest`, `ResetPasswordRequest`,
  `SetEnabledRequest` (records + validation).
- `ContractService.ensureAccount()` idempotent: tạo hợp đồng cho NV thiếu tài
  khoản → tạo bù (không lỗi khi đã có).

**2. Auto-provision tài khoản khi tạo nhân viên**
- `KeycloakAdminClient`: thêm `password-suffix` (`hrms.keycloak.admin.password-suffix`,
  env `HRMS_PASSWORD_SUFFIX`, default `@hrms`) — mật khẩu mặc định = `<ma>@hrms`;
  methods `ensureEmployeeAccount` (tạo user + realm role theo level, đã có → bỏ qua),
  `resetPassword`, `setEnabled`.
- **Đổi design so với P0**: bỏ `HRMS_DEFAULT_PASSWORD` (một mật khẩu chung) →
  suffix theo username; cập nhật `.env.example`, `docker-compose.app.yml`
  (env `HRMS_PASSWORD_SUFFIX`), `verify-p1-scoping.sh` (login `$MGR_CODE@hrms`).
- `KeycloakAdminClient` hợp nhất với P0: constructor nhận thêm `passwordSuffix`,
  vẫn dùng client_credentials qua client `hrms-identity` (không revert P0).

**3. Frontend — màn Cài đặt**
- `pages/SettingsAccountsPage.tsx` (bảng tài khoản, reset mật khẩu, bật/tắt
  tài khoản — chỉ `HR_ADMIN`), `pages/SettingsSecurityPage.tsx` (đổi mật khẩu
  của chính mình).
- `api/accounts.ts`, routes `/settings/accounts|security` trong `App.tsx` với
  `ProtectedRoute`, menu **Cài đặt** mọi role — con *Tài khoản* chỉ HR_ADMIN;
  i18n `i18n/locales/{vi,en}/settings.json`.

**4. Quyết định đã chốt (Q&A)**
- Q1: menu Cài đặt mọi role, trang *Tài khoản* chỉ HR_ADMIN.
- Q2: tự đổi mật khẩu (self) + admin reset (đúng khả năng tách endpoint).
- Q3: giữ tạo tài khoản ở Tạo nhân viên + idempotent khi Tạo hợp đồng
  (không chờ hợp đồng làm trigger).
- Không thêm realm `passwordPolicy` — validate tối thiểu 8 ký tự ở API.

**5. Smoke + hạ tầng dev**
- `verify-identity.sh`: section 15 accounts (23 check) + login `<ma>@hrms`
  cho NV sinh ra; `verify-new-features.sh`: section B login `<ma>@hrms`.
- `.gitattributes` (mới): `* text=auto eol=lf` — `core.autocrlf=true` trên
  Windows từng checkout `.sh` ra CRLF → bash trong container báo
  `$'\r': command not found`; `.env` cũng phải LF (compose đọc được nhưng
  `bash source` thì không).
- KC running (volume cũ, không re-import realm): đã patch client
  `hrms-identity` + service-account realm-admin qua admin API (script tạm đã
  xóa; volume mới chỉ cần import `keycloak/realm-hrms.json`).
- Dev identity (`hrms-dev`): container cũ không nạp `env_file` → PHẢI start
  với env (`set -a && . ./.env && set +a`, xem AGENTS §1/§7.7).

### ✅ Kết quả verify (chạy thật trên merged stack P0+P1+feature)

- `mvn clean verify` (JDK 21 container): **BUILD SUCCESS** — identity **82 tests**
  (18 test mới: `AccountServiceTest` + `AccountControllerTest`)
- `tsc --noEmit` + `vite build`: **PASS**
- Smoke: `verify-identity` **222/222**, `verify-new-features` **16/16**,
  `verify-p1-scoping` **32/32**, `verify-gateway-routes` **ALL OK**
  (`GW=http://gateway:8080`), `verify-attendance` **PASS**
  (`ATT_URL/GW_URL` override — dev gateway 8085/attendance 8086 không chạy)
- App stack: rebuild 4 image (`identity/gateway/attendance/frontend`) + `up -d` OK
  (`.env` fail-fast, KC `client_credentials` hoạt động — token OK)

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
