# CHANGELOG

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
