# CHANGELOG

## [Bảo mật — Security headers + Dependency scan] 2026-10-27

Giai đoạn hardening (hướng 2) — hạng mục 5/6: **siết header bảo mật + quét lỗ hổng dependency**
theo định kỳ/PR.

### Nội dung thay đổi
- Gateway `SecurityConfig`: thêm `Permissions-Policy` (`camera=(), microphone=(), geolocation=()`)
  bên cạnh `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy` đã có. Gateway
  vẫn KHÔNG bật CSP (trả JSON nội bộ) và KHÔNG bật HSTS (TLS do edge/reverse proxy).
- `GatewaySecurityTest.securityHeadersArePresentOnEveryResponse` khoá hành vi header trên
  response 401 (mọi response đều có header).
- SPA edge (`infra/nginx/frontend.conf`) đã có đủ headers + CSP từ trước — giữ nguyên, ghi
  chú deploy domain thật phải đổi CSP/`connect-src`/`frame-src` và bật HSTS khi có TLS.
- `.github/workflows/dependency-review.yml`: chặn PR thêm dependency có lỗ hổng
  `high`/`critical` (`actions/dependency-review-action@v4`, `fail-on-severity: high`).
- `.github/dependabot.yml`: cập nhật weekly cho maven, npm (`/frontend`), docker
  (các service + compose) và github-actions; nhóm minor/patch để giảm nhiễu PR.
- Root `pom.xml`: profile `security-scan` chạy OWASP `dependency-check-maven` 10.0.4
  (`failBuildOnCVSS=9`, bỏ test/provided) — thủ công/local: `mvn verify -Psecurity-scan`
  (cần tải NVD; đặt `NVD_API_KEY` để tăng hạn mức), không chạy trong CI mặc định.

## [Bảo mật — Audit logging] 2026-10-27

Giai đoạn hardening (hướng 2) — hạng mục 4/6: **audit log cho thao tác nhạy cảm** (ai làm gì,
trên đối tượng nào, lúc nào, kết quả). Append-only, mỗi service ghi vào DB riêng.

### Nội dung thay đổi
- `hrms-common` (`com.hrms.common.audit`): entity `AuditLog` (`audit_log`, không kế thừa
  `BaseEntity`), `AuditLogRepository`, `AuditService.success/failure(action, targetType,
  targetId, detail)` — actor từ SecurityContext (JWT `preferred_username` → `sub` → `system`),
  `correlationId` từ MDC, `clientIp` từ request (`X-Forwarded-For`), detail cắt 500 ký tự.
  Gọi trong transaction nghiệp vụ → audit và dữ liệu commit/rollback cùng nhau.
- Migration: identity `V12__audit_log.sql`, attendance `V17__audit_log.sql` (bảng +
  index `occurred_at desc`, `actor`, `(target_type, target_id)`).
- Attendance `PersistenceConfig` scan thêm `com.hrms.common.audit` (entity + repository) —
  vẫn KHÔNG scan toàn `com.hrms` (tránh validate `outbox_events`).
- Call sites (detail KHÔNG chứa mật khẩu/lương/PII):
  - identity: `ACCOUNT_PASSWORD_CHANGE|PASSWORD_RESET|SET_ENABLED|ROLES_UPDATE|ROLES_RESYNC`,
    `EMPLOYEE_CREATE|UPDATE|TERMINATE|ASSIGN_CALENDAR`, `CONTRACT_CREATE|UPDATE|EXTEND|
    REPLACE|TERMINATE|EXPIRE`, `DOCUMENT_UPLOAD|DELETE`, `KEYCLOAK_SYNC_RETRY`.
  - attendance: `LEAVE_POLICY_CREATE|UPDATE|SUBMIT|APPROVE|REJECT|ACTIVATE|CANCEL|DELETE`,
    `LEAVE_QUOTA_RECALCULATE`, `LEAVE_ENTITLEMENT_GRANT|DELETE`, `LEAVE_SETTINGS_UPDATE`,
    `LEAVE_NOTE_UPDATE`, `LEAVE_WORK_DAY_UPDATE`, `SHIFT_ASSIGN|UNASSIGN`.
- Đã kiểm chứng end-to-end: smoke identity 283 / p1-scoping 52 / attendance OK → bảng
  `audit_log` ghi đủ actor `hr`, correlationId + clientIp; contract expiry job ghi
  `CONTRACT_EXPIRE` với actor `system`.

## [Cấu hình — chuyển host port ra khỏi dải 8080–8089] 2026-10-26

Host port của HRMS trùng với dự án khác trên cùng máy → chuyển **toàn bộ host-facing port**
sang dải **818x**; port **nội bộ Docker giữ nguyên** (không đổi network topology):

| Vai trò | Cũ (host) | Mới (host) | Nội bộ Docker (giữ) |
|---|---|---|---|
| App gateway (`docker-compose.app.yml`) | 8080 | **8180** | gateway:8080 |
| Keycloak (`KEYCLOAK_PORT`, issuer `KC_HOSTNAME`) | 8081 | **8181** | keycloak:8080 |
| Schema Registry (`SCHEMA_REGISTRY_PORT` + advertised) | 8082 | **8182** | schema-registry:8081 |
| Dev identity (jar `SERVER_PORT`, forwardPorts) | 8080 | **8183** | — |
| Dev gateway (jar `SERVER_PORT`, FE `API_BASE`) | 8085 | **8185** | — |
| Dev attendance (jar `SERVER_PORT`) | 8086 | **8186** | — |

### Ảnh hưởng bắt buộc
- **Issuer đổi theo**: token mới có `iss=http://localhost:8181/realms/hrms` → phải
  rebuild 3 backend image (default `ISSUER_URI` trong application.yml) + FE
  (`VITE_KEYCLOAK_URL` default) + `docker compose up -d keycloak` (KC_HOSTNAME mới);
  token cũ (iss 8081) bị 401 — user đăng nhập lại.
- `.env`/`.env.example`: `KEYCLOAK_PORT=8181`, `SCHEMA_REGISTRY_PORT=8182`.
- Scripts smoke: default `API/ATT/GW` → localhost:8183/8186/8185; các URL in-network
  (`keycloak:8080`, `http://gateway:8080` override...) giữ nguyên.
- Gateway route default (dev): `IDENTITY_URI=http://localhost:8183`,
  `ATTENDANCE_URI=http://localhost:8186` (app stack vẫn env override sang in-network).
- `.devcontainer/devcontainer.json`: forwardPorts `[8183, 8185, 8186, 5173, 4173]`.
- Dev identity jar đổi lệnh start: `SERVER_PORT=8183 java -jar identity-service/...`.
- KHÔNG đổi: `server.port` default 8080 (app stack container), `EXPOSE`, nginx
  `proxy_pass gateway:8080`, JWKS/admin `keycloak:8080`, kafka-ui 8090, Vite 5173.

## [Bảo mật — Rate limiting tại gateway] 2026-10-24

Giai đoạn hardening (hướng 2) — hạng mục 3/6: **giới hạn tần suất gọi API** ở single entry
point (gateway). Chống lạm dụng/lỗi client vòng lặp, giảm tải cho identity/attendance.

### Nội dung thay đổi
- Mới `FixedWindowRateLimiter` (in-memory, cửa sổ 60s, `ConcurrentHashMap`, dọn key cũ khi
  > 50k) + `RateLimitFilter` (`GlobalFilter`, order `HIGHEST_PRECEDENCE+100`) chỉ áp `/api/**`.
- Key = `principal.getName()` (JWT — security filter chạy trước); fallback IP
  (`X-Forwarded-For` → remote address) khi principal không có tên.
- Vượt hạn mức → **429** + `Retry-After` + body
  `{"code":"RATE_LIMIT_EXCEEDED","message":...}`. Actuator/health không bị giới hạn.
- Config: `hrms.rate-limit.enabled` / `requests-per-minute` (env `RATE_LIMIT_ENABLED`,
  `RATE_LIMIT_RPM`, default true / 600); compose app stack truyền 2 env cho gateway;
  `.env.example` bổ sung 2 biến (comment).

### Tests / Smoke
- `FixedWindowRateLimiterTest` (5): trong hạn mức, vượt → retryAfter, sang cửa sổ mới reset,
  key độc lập, limit ≤ 0 lỗi. `RateLimitFilterTest` (2): key theo username, fallback khi
  principal không tên.
- Gateway: 12 test PASS. Kiểm thử thực tế với `RATE_LIMIT_RPM=3`: 3 request → 200, request 4
  → 429 (`Retry-After: 26`), `/actuator/health` → 200. Khôi phục default 600.
- Smoke sau khôi phục: `verify-gateway-routes.sh` ALL OK, `verify-identity.sh` PASS=283.

### Giới hạn đã biết
- In-memory 1 instance → scale ngang phải chuyển Redis/Bucket4j phân tán.
- Fixed window cho phép burst gấp đôi ở ranh giới cửa sổ.
- Request sai token bị security chặn (401) trước filter → không tính hạn mức (chống flood
  token rác cần thêm limit ở nginx/edge).

## [Bảo mật — P0-5: xác thực audience (aud) của JWT] 2026-10-23

Giai đoạn hardening (hướng 2) — hạng mục 2/6: **bắt buộc claim `aud`**. Trước đây cả 3
resource server chỉ kiểm tra chữ ký + `iss` (`JwtValidators.createDefaultWithIssuer`) →
token cấp cho mục đích khác (vd token service-account `hrms-identity` chỉ dùng gọi
Keycloak Admin API) vẫn được chấp nhận. Nay token thiếu `aud=hrms-api` → **401**.

### Nội dung thay đổi
- **Cả 3 service** (`identity`, `attendance`, `gateway`): `jwtValidator()` =
  `DelegatingOAuth2TokenValidator(default(issuer), JwtClaimValidator<List<String>>("aud", ...))`;
  thêm config `hrms.keycloak.audience` (env `JWT_AUDIENCE`, default `hrms-api`) trong
  `application.yml` từng service.
- **Keycloak**: thêm protocol mapper `hrms-api-audience` (`oidc-audience-mapper`,
  `included.custom.audience=hrms-api`, chỉ access token) trên client `hrms-spa` trong
  `keycloak/realm-hrms.json`. Realm đang chạy: script mới `scripts/kc-add-audience.sh`
  (idempotent) tạo mapper + tự verify token có `aud=hrms-api`.
- **Không** gắn mapper cho `hrms-identity` (client_credentials) — token của nó chỉ dùng
  Keycloak Admin API và cố ý bị resource server từ chối.

### Tests / Smoke
- Backend unit/MVC: identity 140, attendance 133, common 19 — PASS (MVC slice dùng
  `jwt()` post-processor nên không phụ thuộc aud; validator được xác minh qua smoke).
- `verify-identity.sh` thêm mục §0: token `hr` có `aud=hrms-api` (PASS) và token
  service-account (không aud) bị **401** → `PASS=283`.
- `verify-p1-scoping.sh` `PASS=52`, `verify-roles.sh` `PASS=23`, `verify-attendance.sh` OK,
  `verify-gateway-routes.sh` ALL OK. Kiểm tra trực tiếp: user token → 200, service-account
  token → 401.

### Lưu ý triển khai
- Token cũ (phát hành trước khi có mapper) bị 401 → **user phải đăng nhập lại**.
- Máy môi trường mới import `realm-hrms.json` là đủ; realm đang chạy phải chạy
  `scripts/kc-add-audience.sh` (fresh import không áp dụng lại).

## [Bảo mật — Siết scope đọc (IDOR) + P0-5 JWT aud: phần 1 IDOR/authorization] 2026-10-23

Giai đoạn hardening (hướng 2) — hạng mục 1/6: **chống truy cập trái phép chéo hồ sơ (IDOR)**
cho mọi endpoint đọc có tham chiếu nhân sự. Trước đây nhiều GET chỉ kiểm tra *role* mà không
kiểm tra *scope* → MANAGER/DEPT_LEAD/EMPLOYEE có thể đọc dữ liệu ngoài phạm vi bằng cách
đoán/nắm id. Nay áp `EmployeeScope` (ALL/DEPARTMENT/SELF) nhất quán.

### Nội dung thay đổi

**Attendance service**
- Mới `EmployeeAccess` (final class) + `EmployeeAccessResolver` (`@Component`): dựng phạm vi
  (ALL → toàn bộ; DEPARTMENT → phòng quản lý + nhánh con ∪ trực thuộc ∪ self; SELF → trực thuộc
  ∪ self), fail-closed khi không khớp hồ sơ (`username = employeeCode`).
- `EmployeeService.list/getByCode`: siết scope — trước đây MANAGER/SELF thấy *toàn bộ*; nay
  chỉ self ∪ trực thuộc (ngoài phạm vi → 403).
- `LeaveQuotaService.getQuota/listQuotas` + `LeaveQuotaController`: thêm scope/username —
  đọc quota ngoài phạm vi → 403; list lọc theo scope.
- `LeaveSettingsService.getNote/listWorkDays` + `LeaveSettingsController`: như trên.
- `ShiftAssignmentService.list` + `ShiftAssignmentController`: lọc theo scope; lọc theo
  `employeeId` ngoài phạm vi → 403.

**Identity service**
- `EmployeeViewer.of(Jwt, Authentication)`: factory dựng viewer từ token (username/email/scope);
  `EmployeeController.viewer()` dùng lại.
- `EmployeeRepository.findIdsInScope(managerScope, deptScope, viewerId)`: id nhân sự trong phạm vi
  (tái dùng mệnh đề scoping của `search`) để lọc các đọc chéo aggregate.
- `ContractService.search/listByEmployee/expiringSoon` + `ContractController`: siết scope —
  `listByEmployee` ngoài phạm vi → 403; `search`/`expiringSoon` lọc theo id trong phạm vi;
  fail-closed khi MANAGER không có hồ sơ NV.

### Tests / Smoke
- `EmployeeAccessTest` (5), cập nhật `EmployeeServiceTest`/`ShiftAssignmentServiceTest`/
  `LeaveQuotaServiceTest`/`ContractControllerTest` (chữ ký mới). Backend: identity 140, attendance
  133, common 19 — PASS.
- `verify-p1-scoping.sh` thêm mục 3b: contract + attendance IDOR theo MANAGER (self ∪ trực thuộc
  thấy được; ngoài phạm vi 403) → `PASS=52`.
- `verify-identity.sh`: `manager1` seed không gắn hồ sơ NV → fail-closed (không thấy hợp đồng/lương)
  → `PASS=281`. `verify-roles.sh` `PASS=23`.

### Lưu ý hành vi
- MANAGER/DEPT_LEAD phải có hồ sơ NV (khớp `username = employeeCode`) mới thấy dữ liệu; tài khoản
  quản lý seed không gắn hồ sơ → danh sách rỗng (fail-closed, nhất quán với `/employees`).
- Org-chart vẫn toàn công ty (giữ nguyên theo quyết định).

## [Quỹ phép — Sửa kỳ công: 12 kỳ/năm TC (bỏ kỳ đuôi clip)] 2026-10-22

**Bug**: với lưới kỳ `26→25`, `PeriodEngine.fiscalPeriods` clip kỳ đầu theo fyStart
và tạo thêm kỳ đuôi clip (`26/M–31/M`, full chỉ ~4 ngày → luôn "đạt" ≥50%) →
**13 kỳ/năm TC**, chia 13 → BASE ra số thập phân (vd khthanh `12×3/13 = 2.7692`).

**Chốt lại rule** (khớp "1 năm chỉ có 12 kỳ"): kỳ **WHOLE theo lưới**, gán năm TC
theo **ngày end** — kỳ đầu = kỳ lưới chứa fyStart (có thể bắt đầu trước fyStart),
kỳ có `end > fyEnd` thuộc năm TC sau. FY 4/2026 = `26/3/2026–25/4/2026` →
`26/2/2027–25/3/2027` = **12 kỳ**; đuôi `26/3–31/3/2027` nằm trong "kỳ tháng 4"
của FY 2027 → liên tục, không hở/không chồng giữa 2 năm TC.

### Nội dung thay đổi

- `PeriodEngine.fiscalPeriods`: bỏ clip đầu/cuôi — thêm kỳ WHOLE, dừng khi
  `end > fyEnd`; javadoc đổi mô tả (12 kỳ, partition liên năm TC).
- `LeaveSettingsService.findRawFyStart`: gán năm TC theo **end** (kỳ WHOLE đầu
  bắt đầu năm DL trước → `full`/ALTERNATING dùng đúng fyStart); `upsertWorkDay`
  thử 3 ứng viên year ±1 (kỳ đầu 26/12/2025 ∈ FY 2026, kỳ cuối 1/3/2027 ∈ FY
  4/2026); `ensureDefaultWorkDays` đồng bộ `periodEnd` khi lưới kỳ đổi.
- Tests: `PeriodEngineTest` 13→12 (fy14 + fy11) + test **partition liên năm TC**
  (FY2025.end+1 = FY2026.start, đuôi 26/3–31/3/2027 vào kỳ tháng 4/FY2027);
  test quota (grid 1→31 = 12 kỳ) không đổi.
- Sau fix + recalculate: BASE = `baseDays × số_kỳ_đạt / 12` — với `baseDays=12`
  luôn **số nguyên** (vd khthanh 2 kỳ đạt → `2.0000` thay vì `2.7692`).
- Docs: AGENTS §4.15 (12 kỳ, WHOLE theo end, baseDays chia hết 12 ⇒ nguyên).

## [Quỹ phép — Thâm niên bậc theo luật VN + strategy rule mở rộng] 2026-10-22

Đổi cách tính SENIORITY trong quota phép từ "linear step theo năm dương lịch"
sang **bậc theo luật Việt Nam** (NĐ 145/2020) với kiến trúc **strategy pattern**
để mở rộng luật Hàn/Nhật sau này mà không đập đi xây lại. Mô tả đầy đủ: AGENTS §4.15.

### Nội dung thay đổi

**1. Backend attendance — seniority rule (Flyway V16)**
- `V16__seniority_rule_tiers.sql`: `leave_policies.seniority_rule`
  (default `VN_TIERED`); bảng `leave_seniority_tiers`
  (`policy_id`, `min_years`, `bonus_days`, unique (policy_id, min_years));
  seed 5 bậc luật VN `0/5/10/15/20 → +0/2/4/6/8` cho mọi policy hiện có.
- Package `attendance.leave.seniority`: interface `SeniorityRule` +
  `SeniorityRuleRegistry` (Spring nạp mọi bean rule, fallback LINEAR khi
  chưa đăng ký); `VietnamTieredSeniorityRule` (đủ `minYears` tại
  `asOf = ngày recalculate` — `Period.between(hiredAt, asOf)`, bỏ qua
  `seniorityMaxBonus`; tiers rỗng → self-heal tạm tính LINEAR);
  `LinearSeniorityRule` (legacy: `stepYears×n × bonusDays` năm dương lịch,
  cap `maxBonus`). Mở rộng: thêm value enum + class `@Component` — không đổi schema.
- `LeavePolicyService`: create/update DRAFT apply `seniorityRule` +
  `seniorityTiers` (request null → VN_TIERED + seed luật VN; copy source
  giữ rule + tiers); validate VN_TIERED tiers rỗng/trùng `minYears`/âm → 422;
  deleteDraft cascade xóa tiers; response trả `seniorityRule` + `seniorityTiers`.
- `LeaveQuotaService.seniorityBonus` resolve rule qua registry + tiers của
  policy in-force. DTO giữ scalar `seniorityStepYears/BonusDays/MaxBonus`
  (LINEAR) → JSON cũ không đổi tên field vẫn hoạt động.
- Tests: +17 (biên bậc VN 0/5/10/15/20, anniversary vs dương lịch, self-heal
  tiers rỗng, LINEAR legacy, registry fallback, validation 422, seed 5 bậc).
  Backend verify: **127 tests PASS**.

**2. Frontend — form chính sách hiển thị/sửa theo rule**
- `types.ts`: `SeniorityRuleValue`, `LeaveSeniorityTier`, mở rộng
  `LeavePolicy`/`LeavePolicyCreate`.
- `SettingsPoliciesPage.tsx`: card in-force hiển thị Tag rule + tiers/linears;
  modal form Select `seniorityRule` — LINEAR → 3 ô cũ; VN → `Form.List`
  bậc (minYears + bonusDays) + nút Thêm bậc (sort theo minYears).
- i18n vi/en +8 keys (`seniorityRule`, `seniorityRule_VN_TIERED/LINEAR`,
  `seniorityRuleHint`, `seniorityTiers`, `seniorityTiersHint`,
  `tierMinYears`, `tierBonusDays`, `addTier`). FE build PASS.

**3. Smoke — verify xanh**
- `mvn clean verify` attendance+am: **127 tests PASS**; FE `npm run build` PASS;
  image rebuild + `up -d` → V16 applied (schema → v16).
- `verify-attendance.sh`: EXIT=0 (idempotent, NV9001 409 accept).
- `verify-identity.sh` (§13 versioning + DTO cũ không rule/tiers): **PASS=271 FAIL=0**.

## [Quỹ phép — Policy versioning 6 trạng thái + BASE per-period] 2026-10-21

Mở rộng chính sách phép từ 1 dòng/năm sang **versioning effective-dated** (nhiều
version/năm TC, 6 trạng thái) + tính lại BASE theo từng kỳ (mỗi kỳ dùng policy
hiệu lực tại `p.start`). Mô tả đầy đủ: AGENTS §4.15.

### Nội dung thay đổi

**1. Backend attendance — versioning (Flyway V13–V14)**
- `V13__leave_policy_versioning.sql`: `leave_policies` thêm `version_no`,
  `status` (DRAFT/PENDING_APPROVAL/APPROVED/ACTIVE/EXPIRED/CANCELLED),
  `effective_from/effective_to`, `submitted_at/submitted_by`,
  `approved_at/approved_by`, `activated_at`; unique (fiscal_year, version_no);
  `leave_settings.require_policy_approval` (default true).
- `V14__defer_note_fk.sql`: FK `leave_employee_notes.employee_id` chuyển
  `deferrable initially deferred` — cho phép re-key PK employees cùng transaction.
- `PolicyStatus` enum + `LeavePolicyService` (workflow 6 trạng thái):
  `POST /leave/policies` (create DRAFT; validate effectiveFrom > mọi version
  hiện có trong năm → 422), `PUT /versions/{id}` (chỉ DRAFT), `POST /{id}/submit|
  approve|reject|activate|cancel`, `DELETE /{id}` (chỉ DRAFT);
  `activate` (D ≥ hôm nay, trong FY range, D > version cũ) → version mới ACTIVE,
  version ACTIVE cũ tự EXPIRED với `effectiveTo = D−1`; reject = PENDING→DRAFT;
  cancel = PENDING/APPROVED→CANCELLED. Flag `requirePolicyApproval=false` →
  activate thẳng từ DRAFT. **approve/reject = ADMIN hoặc GLOBAL_LEAD**
  (GLOBAL_LEAD duyệt cuối chính sách toàn công ty); các thao tác khác = ADMIN.
  - **Activate ngày quá khứ được phép** (áp dụng retroactive, chỉ cần trong FY
    range + sau version ACTIVE cũ) — FE hiện Popconfirm cảnh báo riêng
    (`confirm.activatePast`) thay vì backend chặn 422.
  - **Precision FE**: `minWorkPercent` hiển thị nguyên (bỏ .00); form phiên bản
    (baseDays/carryoverMax/seniorityBonusDays/seniorityMaxBonus/roleQuotas)
    2 chữ số thập phân (từ 4).
- `GET /leave/policies/in-force?year=` (write tx — self-heal + lazy expire,
  xem §7.9); `GET /leave/policies?year=` (history); `GET /leave/policies/{id}`.
  `DELETE /{year}` (theo năm) **đã bỏ** — xóa theo id (chỉ DRAFT).

**2. Recalculate BASE per-period + snapshot**
- Mỗi kỳ `p` dùng policy hiệu lực tại `p.start` → BASE =
  `Σ baseDays/13` (chia 1 lần cuối); SENIORITY/role bonus/carryover dùng
  policy in-force tại asOf.
- Snapshot: mọi dòng auto ghi `policy_id` + `calculated_at`
  (BONUS/ADJUSTMENT policyId=null); recalculate **explicit** — không tự tính
  lại khi đổi policy.

**3. Frontend — Cài đặt 3 tabs**
- `SettingsPoliciesPage.tsx` viết lại: Tab **Tham số global** (form +
  `requirePolicyApproval` switch), Tab **Chính sách hiện tại** (in-force +
  workflow buttons theo status), Tab **Lịch sử chính sách** (table version +
  create/edit DRAFT, submit/approve/reject/activate/cancel, delete DRAFT).
- `types.ts` (PolicyStatusValue, LeavePolicy versioning, LeaveSettings
  +requirePolicyApproval, LeaveEntitlement +policyId/calculatedAt);
  `leaveQuotas.ts` (getLeavePolicy, listLeavePolicies(year?), create/update/
  submit/approve/reject/activate/cancel, deleteLeavePolicy(id)); i18n vi/en.

**4. Bug fix — re-key projection đổi @Id managed entity (AGENTS §7.10)**
- Kafka consumer apply `EmployeeCreatedEvent` re-key employee projection bằng
  `setId()` → `JpaSystemException "identifier altered"` → retry vô hạn →
  poison partition (projection đứng yên, smoke 404). Fix: `findOrCreate` =
  re-key FK 4 bảng (`shift_assignments`, `leave_entitlements`,
  `leave_employee_notes` — V14 defer FK, `leave_work_days` — **V15 defer FK**;
  thieu 1 bang → delete employee loi FK → smoke shift 404) → `delete(old)` →
  `flush()` → `copyWithId` insert row mới; không `save()` trong findOrCreate.
- Smoke `verify-identity.sh` §14: bỏ `sleep 3` thay bằng poll projection
  attendance ≤15s (Kafka async — các section trước tạo backlog event, sleep
  cố định không đủ ở lần chạy thứ 2 trở đi).

### ✅ Kết quả verify (chạy thật trước khi commit)

- `mvn clean verify` 4 module: **BUILD SUCCESS — 226 tests** (common 18,
  identity 99, gateway 5, attendance 104 — gồm 22 test LeavePolicyServiceTest
  mới + LeaveQuotaServiceTest per-period refactor).
- `vite build`: **PASS**; app stack rebuild image attendance/frontend + `up -d`.
- Smoke qua app gateway: `verify-identity.sh` **270/270** (§13 versioning cycle:
  create→edit→submit→approve→activate→ACTIVE; edit/delete ACTIVE 422;
  reject→DRAFT→delete; cancel→CANCELLED; history list; recalculate snapshot
  `"policyId":"`; settings `requirePolicyApproval`).
- `verify-attendance.sh` (ATT_URL=http://attendance:8080 GW_URL=http://gateway:8080):
  **EXIT=0** — idempotent (POST NV9001 → 409 accept, projection OK).

## [Thiết kế lại tính quỹ phép — Tham số global + Chính sách per-year] 2026-10-21

Thiết kế lại tính quỹ phép theo **kỳ công (period)** + **năm tài chính**: màn
*Cài đặt* thêm Tab **Tham số global** + trang **Danh sách chính sách phép** (1
dòng/năm TC), quota tự tính lại theo kỳ đạt (BASE), thâm niên, bonus theo role
(max) và carryover auto. Q&A 9 điểm đã chốt (mục 5 dưới). Mô tả đầy đủ: AGENTS §4.15.

### Nội dung thay đổi

**1. Backend attendance — settings / policy / work-days / note (Flyway V9–V12)**
- `V9__leave_settings.sql`: `leave_settings` 1 row global — `fyStartMonth`/
  `fyStartDay` (năm tài chính), `periodStartDay`/`periodEndDay` (chỉ 2 combo
  hợp lệ: start=1,end≥29 hoặc start=end+1 → hở 422; FY 1/4 + kỳ 26/25 → 13 kỳ),
  `saturdayPolicy` (OFF/HALF/ALTERNATING/ALL), `minWorkPercent` (x%, default 50).
- `V10__leave_notes_workdays.sql`: `leave_employee_notes` (note ≤500 ký tự/NV/năm)
  + `leave_work_days` (ngày công từng kỳ/NV, source DEFAULT/MANUAL).
- `V11__employee_roles_carryover.sql`: `employees.roles` (projection sync từ event)
  + unique dedupe CARRYOVER theo (employee_id, year); `V12__leave_role_quotas.sql`:
  `leave_role_quotas` (bonus theo role/năm, Source=POSITION) + `is_auto` trên
  `leave_entitlements`.
- `LeaveSettingsController` (`/api/v1/attendance/leave`): `GET/PUT /settings`
  (đọc ADMIN/GLOBAL_LEAD/DEPT_LEAD/MANAGER, ghi ADMIN, validate combo kỳ),
  `GET/PUT /quotas/{employeeId}/note`, `GET/PUT /quotas/{employeeId}/work-days`
  (PUT = upsert MANUAL, validate ≤full workingDays).
- `LeavePolicyController` (`/leave/policies`): GET/POST (ADMIN) +
  `DELETE /{year}` → **422 nếu năm còn entitlements**.

**2. PeriodEngine + recalculate quota (attendance)**
- `PeriodEngine` (pure, không Spring): `validate` combo kỳ, `fiscalPeriods`
  (clip theo hiredAt/terminatedAt, wrap year), `workingDaysIn` (T2–T6 + T7 theo
  policy + CN nghỉ; **ngày lễ KHÔNG trừ**; ALTERNATING: Sat #0 = đi làm, reset
  mỗi năm TC).
- `LeaveQuotaService.recalculate` (ADMIN, `{year, employeeIds?}`):
  - **BASE** = `baseDays × số_kỳ_đạt / tổng_số_kỳ` — 1 kỳ đạt khi
    `actual×100 ≥ full×minWorkPercent`; kỳ clipped vẫn tính 1 kỳ;
  - **SENIORITY** theo `seniorityBaseDays` (≥24 tháng, half-year step);
  - **role bonus** = max các role khớp (`employees.roles`), không cộng dồn;
  - **carryover auto** = min(còn lại năm trước, carryoverMax), upsert CARRYOVER
    `is_auto=false` (idempotent — recalculate lần 2 không tạo dòng thừa);
  - dòng DEFAULT `leave_work_days` được ensureDefaultWorkDays auto-fill;
    recalculate **không đụng** dòng MANUAL.
- Entitlement nguồn AUTO (BASE/SENIORITY/CARRYOVER) `is_auto=true`,
  `DELETE /entitlements/{id}` → **422**; grant thủ công (ADMIN) vẫn xóa được.
- `LeaveQuotaResponse` mở rộng: `roleDays`, `roles[]`, `note`; FE detail modal 3 Tabs.

**3. Roles sync sang attendance (event `hrms.employee.v1`)**
- Avro 2 schema +field `roles` (array of string, default `[]`); identity emit
  roles khi create/update + PATCH `/api/v1/accounts/{username}/roles` (đã có)
  → emit thêm `updateRoles`; `POST /api/v1/accounts/resync-role-events` (ADMIN,
  201 `{emitted:n}`) — bắn lại event cho toàn NV để backfill projection.
- Attendance `applyRoles`: null → giữ nguyên (backward-compat event cũ),
  empty → null; idempotent qua `processed_events`.

**4. Frontend**
- `LeaveQuotaPage.tsx` viết lại: bỏ button/modal *Chính sách phép* (chuyển sang
  Cài đặt), thêm cột **Phép role** (roleDays) + **Ghi chú**, nút **Tính lại**
  (recalculate), modal **Chi tiết** 3 Tabs: *Dòng cấp phép* (tag AUTO/manual,
  chặn xóa AUTO), *Ngày công* (per-row save → MANUAL), *Ghi chú* (editable).
- `SettingsPoliciesPage.tsx` (mới, route `/settings/policies`, ADMIN, menu con
  Cài đặt): Tab **Tham số global** (form GET/PUT settings + validate combo) +
  Tab **Danh sách chính sách** (table theo năm, modal CRUD với `Form.List` roleQuotas).
- `api/leaveQuotas.ts` viết lại + `types.ts` (LeaveSettings, LeaveWorkDay,
  LeaveRoleQuota, SaturdayPolicyValue, WorkDaySourceValue, +roles/note/roleDays/
  isAuto); i18n vi/en `settings.leavePolicies.*`, `leave.workday.*`, menu item.

**5. Q&A đã chốt (9 điểm)**
1. Ngày công DEFAULT auto-fill từ hiredAt/terminatedAt; HR sửa tay → MANUAL.
2. BASE theo số kỳ đạt / tổng số kỳ năm TC (kỳ clipped vẫn tính 1 kỳ).
3. Policy theo năm tài chính (1 dòng/năm); DELETE 422 nếu còn entitlements.
4. Nhiều role → lấy bonus cao nhất (không cộng dồn); DB enum source giữ POSITION.
5. Carryover auto = min(còn lại năm trước, carryoverMax); CARRYOVER = upsert single row.
6. Note mỗi NV/năm ≤500 ký tự.
7. Kỳ công chỉ 2 combo: (start=1,end≥29) hoặc (start=end+1).
8. Ngày lễ KHÔNG trừ — chỉ T2–T6 + T7 theo policy + CN nghỉ.
9. x% (minWorkPercent) global ở Tab Tham số; thâm niên/carryoverMax per-year Tab Chính sách.

**6. Bugs fix phát hiện khi smoke/test end-to-end**
- **readOnly self-invocation không commit** (AGENTS §7.9): `getSettings`,
  `listWorkDays`, `getPolicy` vốn `@Transactional(readOnly=true)` gọi self-heal
  `getOrCreate*` qua this → không qua proxy → save không commit (GET 200 nhưng
  row không tồn tại). Đã đổi sang `@Transactional` (write).
- **ensureAuto ghi đè BASE**: self-heal GET quota từng set BASE = `baseDays` thô
  → đắp lên dòng BASE đã recalculate. Đã tách `computeBase(...)` (gồm
  ensureDefaultWorkDays + countEligiblePeriods) dùng chung cho recalculate +
  ensureAuto — idempotent.

### ✅ Kết quả verify (chạy thật trước khi commit)

- `mvn clean verify` 4 module: **BUILD SUCCESS — 206 tests** (common 18,
  identity 99, gateway 5, attendance 84 — gồm 15 test PeriodEngineTest mới +
  LeaveQuotaServiceTest viết lại + 3 test resync/emit roles mới).
- `tsc --noEmit` + `vite build`: **PASS**; app stack rebuild image
  attendance/identity/frontend + `up -d`.
- Smoke qua app gateway: `verify-identity.sh` **237/237** (§13 mới: GET/PUT
  settings, combo hở 422, note, work-days, DELETE policy 204/422,
  resync-role-events 201).
- Test case end-to-end tính lại (FY 4/1, kỳ 26/25, NV vào 1/10/2026, x%=50,
  T7 OFF): **21/21** — BASE=6.4615 (12×7/13), kỳ 7 workDays=17, 13 dòng
  DEFAULT, recalculate lần 2 idempotent.

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
