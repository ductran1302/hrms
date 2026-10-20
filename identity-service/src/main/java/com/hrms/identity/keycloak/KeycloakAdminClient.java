package com.hrms.identity.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Gọi Keycloak Admin API — tạo user khi tạo nhân viên/hợp đồng (mã NV = username đăng nhập),
 * kiểm tra username trùng, bật/tắt tài khoản khi nghỉ việc và quản lý tài khoản ở màn Cài đặt
 * (danh sách, đặt lại mật khẩu, xác thực mật khẩu).
 *
 * <p>Admin token lấy qua {@code client_credentials} của client confidential
 * {@code hrms-identity} (service account gắn realm-management role trong realm
 * {@code hrms}) và cache đến khi gần hết hạn — không dùng master admin password
 * (password-grant {@code admin-cli} đã bỏ). Lỗi mạng được retry 3 lần rồi ném
 * {@link BusinessRuleException}.
 *
 * <p>KC không lưu actor/thời điểm cập nhật → ghi vào user attributes
 * ({@code createdBy}/{@code updatedBy}/{@code updatedAt}); actor lấy từ JWT
 * ({@code preferred_username}) — seeder/test không có request → để rỗng.
 */
@Slf4j
@Service
public class KeycloakAdminClient {

  private static final int MAX_ATTEMPTS = 3;

  private final RestClient restClient;
  private final String realm;
  private final String clientId;
  private final String clientSecret;
  private final String passwordSuffix;
  private final String spaClientId;

  private volatile String adminToken;
  private volatile Instant adminTokenExpiresAt = Instant.EPOCH;

  public KeycloakAdminClient(
      @Value("${hrms.keycloak.admin.base-url:http://keycloak:8080}") String baseUrl,
      @Value("${hrms.keycloak.admin.realm:hrms}") String realm,
      @Value("${hrms.keycloak.admin.client-id:hrms-identity}") String clientId,
      @Value("${hrms.keycloak.admin.client-secret:}") String clientSecret,
      @Value("${hrms.keycloak.admin.password-suffix:@hrms}") String passwordSuffix,
      @Value("${hrms.keycloak.admin.spa-client-id:hrms-spa}") String spaClientId) {
    if (clientSecret == null || clientSecret.isBlank()) {
      throw new IllegalStateException(
          "hrms.keycloak.admin.client-secret chưa được cấu hình — "
              + "copy .env.example thành .env và set HRMS_IDENTITY_CLIENT_SECRET "
              + "(phải khớp secret của client hrms-identity trong keycloak/realm-hrms.json)");
    }
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    this.realm = realm;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.passwordSuffix = passwordSuffix;
    this.spaClientId = spaClientId;
  }

  /** Mật khẩu mặc định khi tạo tài khoản: {@code <username>} + suffix — vd {@code thduc1@hrms}. */
  public String defaultPassword(String username) {
    return username + passwordSuffix;
  }

  /** Username đã tồn tại trong Keycloak chưa (exact match). */
  public boolean userExists(String username) {
    User[] found = withRetry("kiểm tra username", () -> restClient.get()
        .uri("/admin/realms/{realm}/users?username={username}&exact=true", realm, username)
        .header("Authorization", "Bearer " + adminToken())
        .retrieve()
        .body(User[].class));
    return found != null && found.length > 0;
  }

  /**
   * Tạo user Keycloak với realm role mặc định (auto-assign: ADMIN/GLOBAL_LEAD/DEPT_LEAD/
   * MANAGER/EMPLOYEE — do {@code RoleAssignmentService} suy ra), mật khẩu mặc định.
   *
   * @throws ConflictException nếu username đã tồn tại trong Keycloak
   */
  public void createUser(String username, String fullName, String email, String defaultRole) {
    String[] familyGiven = splitFamilyGiven(fullName);
    Map<String, Object> body = new HashMap<>();
    body.put("username", username);
    body.put("enabled", true);
    body.put("lastName", familyGiven[0]);
    body.put("firstName", familyGiven[1]);
    // User profile của KC 25 yêu cầu email (thiếu → VERIFY_PROFILE → "Account is not fully
    // set up" khi login). NV không có email thật → dùng email tạm theo mã NV.
    String resolvedEmail = (email == null || email.isBlank()) ? username + "@hrms.vn" : email;
    body.put("email", resolvedEmail);
    body.put("emailVerified", true);
    body.put("credentials", List.of(Map.of(
        "type", "password",
        "value", defaultPassword(username),
        "temporary", false)));
    body.put("realmRoles", List.of(defaultRole));
    body.put("attributes", auditOnCreate());

    try {
      withRetry("tạo user " + username, () -> restClient.post()
          .uri("/admin/realms/{realm}/users", realm)
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + adminToken())
          .body(body)
          .retrieve()
          .toBodilessEntity());
    } catch (HttpClientErrorException.Conflict ex) {
      throw new ConflictException("Tên đăng nhập đã tồn tại trong Keycloak: " + username);
    } catch (HttpClientErrorException ex) {
      throw new BusinessRuleException(
          "Keycloak từ chối tạo tài khoản '" + username + "' (HTTP " + ex.getStatusCode() + ")");
    }
    // KC bỏ qua realmRoles trong CreateUserRepresentation khi gọi REST thô —
    // phải gán qua endpoint role-mappings/realm riêng.
    assignRealmRole(username, defaultRole);
    log.info("Đã tạo tài khoản Keycloak '{}' (role {})", username, defaultRole);
  }

  /** Đếm user theo search term (rỗng = tất cả). */
  public long countUsers(String search) {
    String q = search == null ? "" : search.trim();
    String body = withRetry("đếm user", () -> {
      if (q.isEmpty()) {
        return restClient.get()
            .uri("/admin/realms/{realm}/users/count", realm)
            .header("Authorization", "Bearer " + adminToken())
            .retrieve()
            .body(String.class);
      }
      return restClient.get()
          .uri("/admin/realms/{realm}/users/count?search={search}", realm, q)
          .header("Authorization", "Bearer " + adminToken())
          .retrieve()
          .body(String.class);
    });
    try {
      return Long.parseLong(body == null ? "0" : body.trim());
    } catch (NumberFormatException ex) {
      throw new BusinessRuleException("Keycloak không trả về số user hợp lệ");
    }
  }

  /**
   * Danh sách user theo search term (username/họ tên/email), phân trang theo
   * {@code first}/{@code max} — kèm realm roles và audit attributes cho màn Cài đặt.
   */
  public List<AccountSnapshot> listUsers(String search, int first, int max) {
    String q = search == null ? "" : search.trim();
    String base = "/admin/realms/{realm}/users?briefRepresentation=false&first={first}&max={max}";
    User[] users = withRetry("liệt kê user", () -> {
      if (q.isEmpty()) {
        return restClient.get()
            .uri(base, realm, first, max)
            .header("Authorization", "Bearer " + adminToken())
            .retrieve()
            .body(User[].class);
      }
      return restClient.get()
          .uri(base + "&search={search}", realm, first, max, q)
          .header("Authorization", "Bearer " + adminToken())
          .retrieve()
          .body(User[].class);
    });
    List<AccountSnapshot> snapshots = new ArrayList<>();
    if (users == null) {
      return snapshots;
    }
    for (User user : users) {
      snapshots.add(new AccountSnapshot(
          user.getId(),
          user.getUsername(),
          fullName(user),
          user.getEmail(),
          user.isEnabled(),
          user.getCreatedTimestamp(),
          realmRoles(user.getId()),
          attribute(user, "createdBy"),
          attribute(user, "updatedBy"),
          attribute(user, "updatedAt")));
    }
    return snapshots;
  }

  /**
   * Realm roles của 1 user — soft-fail: lỗi mạng trả về list rỗng
   * (không chặn danh sách tài khoản chỉ vì 1 user đọc role lỗi).
   */
  public List<String> realmRoles(String userId) {
    try {
      RoleName[] roles = withRetry("đọc role user " + userId, () -> restClient.get()
          .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
          .header("Authorization", "Bearer " + adminToken())
          .retrieve()
          .body(RoleName[].class));
      if (roles == null) {
        return List.of();
      }
      return Arrays.stream(roles)
          .map(RoleName::name)
          .filter(name -> name != null && !name.isBlank())
          // bỏ default-roles-* (realm role mặc định Keycloak gán mọi user, không phải role HRMS)
          .filter(name -> !name.startsWith("default-roles"))
          .toList();
    } catch (Exception ex) {
      log.warn("Chưa đọc được realm role của user {}: {}", userId, ex.getMessage());
      return List.of();
    }
  }

  /**
   * Xác thực mật khẩu của user qua password-grant ({@code hrms-spa}).
   *
   * @return true nếu KC cấp token (mật khẩu đúng, user enabled); false nếu sai mật khẩu
   *     / user bị khóa hoặc tắt (401/400)
   */
  public boolean verifyUserPassword(String username, String password) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", spaClientId);
    form.add("username", username);
    form.add("password", password);
    form.add("scope", "openid");
    try {
      withRetry("xác thực mật khẩu " + username, () -> restClient.post()
          .uri("/realms/{realm}/protocol/openid-connect/token", realm)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity());
      return true;
    } catch (HttpClientErrorException ex) {
      return false;
    }
  }

  /** Đặt lại mật khẩu (admin reset / tự đổi) — không temporary, ghi audit updatedBy/updatedAt. */
  public void resetPassword(String username, String newPassword) {
    User user = requireUser(username);
    touchAudit(user);
    saveUser(user);
    Map<String, Object> credential = new HashMap<>();
    credential.put("type", "password");
    credential.put("value", newPassword);
    credential.put("temporary", false);
    withRetry("đặt lại mật khẩu " + username, () -> restClient.put()
        .uri("/admin/realms/{realm}/users/{id}/reset-password", realm, user.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + adminToken())
        .body(credential)
        .retrieve()
        .toBodilessEntity());
    log.info("Đã đặt lại mật khẩu Keycloak '{}'", username);
  }

  /**
   * Đảm bảo user đã tồn tại có realm role (idempotent, soft-fail).
   * Dùng heal các user auto-provision tạo trước khi có gán role riêng.
   */
  public void ensureRealmRole(String username, String roleName) {
    assignRealmRole(username, roleName);
  }

  /** Gán realm role cho user đã tồn tại (idempotent — đã có role thì KC trả 204/409 bỏ qua). */
  private void assignRealmRole(String username, String roleName) {
    try {
      withRetry("gán role " + roleName + " cho " + username, () -> {
        User[] matches = restClient.get()
            .uri("/admin/realms/{realm}/users?username={username}&exact=true", realm, username)
            .header("Authorization", "Bearer " + adminToken())
            .retrieve()
            .body(User[].class);
        if (matches == null || matches.length == 0) {
          throw new BusinessRuleException("KC không tìm thấy user để gán role: " + username);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> role = restClient.get()
            .uri("/admin/realms/{realm}/roles/{roleName}", realm, roleName)
            .header("Authorization", "Bearer " + adminToken())
            .retrieve()
            .body(Map.class);
        return restClient.post()
            .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, matches[0].getId())
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + adminToken())
            .body(List.of(role))
            .retrieve()
            .toBodilessEntity();
      });
    } catch (Exception ex) {
      // Không chặn tạo nhân viên nếu gán role lỗi — sẽ thử lại ở lần tạo user sau / manual
      log.warn("Chưa gán được role {} cho '{}': {}", roleName, username, ex.getMessage());
    }
  }

  /**
   * Realm roles của 1 user theo username — hard-fail (ném BusinessRuleException nếu lỗi mạng)
   * — dùng chặn tự hạ role trước khi ghi (không được đọc nhầm rỗng).
   */
  public List<String> realmRolesByUsername(String username) {
    return realmRoles(requireUser(username).getId());
  }

  /**
   * Đồng bộ realm roles của user về {@code desiredRoles} (màn Cài đặt → Tài khoản):
   * diff add/remove qua role-mappings/realm, ghi audit updatedBy/updatedAt.
   * Role không tồn tại trong KC → 422 {@link BusinessRuleException}.
   */
  public void updateRealmRoles(String username, List<String> desiredRoles) {
    User user = requireUser(username);
    List<String> desired = desiredRoles == null ? List.of() : desiredRoles.stream()
        .filter(r -> r != null && !r.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
    List<String> current = realmRoles(user.getId());
    List<String> toAdd = desired.stream().filter(r -> !current.contains(r)).toList();
    List<String> toRemove = current.stream().filter(r -> !desired.contains(r)).toList();
    if (toAdd.isEmpty() && toRemove.isEmpty()) {
      return;
    }
    if (!toAdd.isEmpty()) {
      List<Map<String, Object>> roles = toAdd.stream().map(this::requireRole).toList();
      withRetry("gán role " + toAdd + " cho " + username, () -> restClient.post()
          .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, user.getId())
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + adminToken())
          .body(roles)
          .retrieve()
          .toBodilessEntity());
    }
    if (!toRemove.isEmpty()) {
      List<Map<String, Object>> roles = toRemove.stream().map(this::requireRole).toList();
      withRetry("gỡ role " + toRemove + " của " + username, () -> restClient
          .method(org.springframework.http.HttpMethod.DELETE)
          .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, user.getId())
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + adminToken())
          .body(roles)
          .retrieve()
          .toBodilessEntity());
    }
    touchAudit(user);
    saveUser(user);
    log.info("Đã đồng bộ roles của '{}': +{} -{} (còn {})", username, toAdd, toRemove, desired);
  }

  /** Đọc full role representation theo tên (cần id cho role-mappings) — role lạ → 422. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> requireRole(String roleName) {
    try {
      return withRetry("đọc role " + roleName, () -> restClient.get()
          .uri("/admin/realms/{realm}/roles/{roleName}", realm, roleName)
          .header("Authorization", "Bearer " + adminToken())
          .retrieve()
          .body(Map.class));
    } catch (Exception ex) {
      throw new BusinessRuleException("Role không tồn tại trong Keycloak: " + roleName);
    }
  }

  /** Bật/tắt tài khoản (nghỉ việc → disabled). Không ném lỗi nếu user không tìm thấy. */
  public void setUserEnabled(String username, boolean enabled) {
    User full;
    try {
      full = requireUser(username);
    } catch (ResourceNotFoundException ex) {
      log.warn("Không tìm thấy user Keycloak '{}' để {}", username, enabled ? "bật" : "tắt");
      return;
    }
    full.setEnabled(enabled);
    touchAudit(full);
    saveUser(full);
    log.info("Đã {} tài khoản Keycloak '{}'", enabled ? "bật" : "tắt", username);
  }

  /** Đọc full user (có attributes — cần cho audit). Ném 404 nếu username không tồn tại. */
  private User requireUser(String username) {
    User[] matches = withRetry("tìm user " + username, () -> restClient.get()
        .uri("/admin/realms/{realm}/users?username={username}&exact=true", realm, username)
        .header("Authorization", "Bearer " + adminToken())
        .retrieve()
        .body(User[].class));
    if (matches == null || matches.length == 0) {
      throw new ResourceNotFoundException("Tài khoản", username);
    }
    return withRetry("đọc user " + username, () -> restClient.get()
        .uri("/admin/realms/{realm}/users/{id}?briefRepresentation=false", realm,
            matches[0].getId())
        .header("Authorization", "Bearer " + adminToken())
        .retrieve()
        .body(User.class));
  }

  private void saveUser(User user) {
    withRetry("cập nhật user " + user.getUsername(), () -> restClient.put()
        .uri("/admin/realms/{realm}/users/{id}", realm, user.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + adminToken())
        .body(user)
        .retrieve()
        .toBodilessEntity());
  }

  /** Ghi audit updatedBy/updatedAt vào attributes của user sắp PUT (giữ nguyên attributes cũ). */
  private void touchAudit(User user) {
    Map<String, List<String>> attributes = user.getAttributes() == null
        ? new HashMap<>()
        : new HashMap<>(user.getAttributes());
    attributes.put("updatedAt", List.of(Instant.now().toString()));
    String actor = currentActor();
    if (actor != null) {
      attributes.put("updatedBy", List.of(actor));
    }
    user.setAttributes(attributes);
  }

  /** Audit khi tạo user: createdBy/updatedBy = người thao tác (nếu có request), updatedAt = now. */
  private Map<String, List<String>> auditOnCreate() {
    Map<String, List<String>> attributes = new HashMap<>();
    String actor = currentActor();
    if (actor != null) {
      attributes.put("createdBy", List.of(actor));
      attributes.put("updatedBy", List.of(actor));
    }
    attributes.put("updatedAt", List.of(Instant.now().toString()));
    return attributes;
  }

  /** preferred_username của JWT hiện tại — null khi không có request (seeder/test). */
  private static String currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      return null;
    }
    String preferred = jwt.getClaimAsString("preferred_username");
    return preferred != null ? preferred : jwt.getSubject();
  }

  /** Đọc attribute theo key — KC có thể chuẩn hóa key về lower-case → so khớp không phân biệt. */
  private static String attribute(User user, String key) {
    if (user.getAttributes() == null) {
      return null;
    }
    List<String> values = user.getAttributes().get(key);
    if (values == null) {
      for (Map.Entry<String, List<String>> entry : user.getAttributes().entrySet()) {
        if (entry.getKey().equalsIgnoreCase(key)) {
          values = entry.getValue();
          break;
        }
      }
    }
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  private static String fullName(User user) {
    String family = user.getLastName() == null ? "" : user.getLastName();
    String given = user.getFirstName() == null ? "" : user.getFirstName();
    return (family + " " + given).trim();
  }

  private synchronized String adminToken() {
    if (adminToken != null && Instant.now().isBefore(adminTokenExpiresAt)) {
      return adminToken;
    }
    // client_credentials: service account của client hrms-identity (realm-management)
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    TokenResponse token = withRetry("lấy admin token", () -> restClient.post()
        .uri("/realms/{realm}/protocol/openid-connect/token", realm)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(TokenResponse.class));
    if (token == null || token.access_token() == null) {
      throw new BusinessRuleException("Keycloak không trả admin token");
    }
    adminToken = token.access_token();
    // cache an toàn: trừ 30s biên an toàn
    adminTokenExpiresAt = Instant.now().plusSeconds(Math.max(30, token.expires_in() - 30));
    return adminToken;
  }

  private <T> T withRetry(String action, Supplier<T> call) {
    ResourceAccessException last = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return call.get();
      } catch (ResourceAccessException ex) {
        last = ex;
        log.warn("Keycloak lỗi mạng khi {} (lần {}/{}): {}", action, attempt, MAX_ATTEMPTS,
            ex.getMessage());
        if (attempt < MAX_ATTEMPTS) {
          try {
            Thread.sleep(500L * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("Bị ngắt khi " + action);
          }
        }
      }
    }
    throw new BusinessRuleException(
        "Không kết nối được Keycloak khi " + action + ": " + last.getMessage());
  }

  /** "Nguyễn Trung Hiếu" → ["Nguyễn", "Trung Hiếu"] (họ đứng trước theo tên Việt). */
  static String[] splitFamilyGiven(String fullName) {
    String[] words = fullName.trim().replaceAll("\\s+", " ").split(" ");
    String family = words[0];
    String given = words.length > 1
        ? String.join(" ", Arrays.copyOfRange(words, 1, words.length))
        : "";
    return new String[] {family, given};
  }

  /** Ảnh chụp tài khoản cho màn Cài đặt — roles + audit lấy từ KC. */
  public record AccountSnapshot(
      String id,
      String username,
      String fullName,
      String email,
      boolean enabled,
      Long createdTimestamp,
      List<String> roles,
      String createdBy,
      String updatedBy,
      String updatedAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenResponse(String access_token, long expires_in) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RoleName(String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class User {
    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private boolean enabled;
    private Long createdTimestamp;
    private Map<String, List<String>> attributes;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getFirstName() {
      return firstName;
    }

    public void setFirstName(String firstName) {
      this.firstName = firstName;
    }

    public String getLastName() {
      return lastName;
    }

    public void setLastName(String lastName) {
      this.lastName = lastName;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Long getCreatedTimestamp() {
      return createdTimestamp;
    }

    public void setCreatedTimestamp(Long createdTimestamp) {
      this.createdTimestamp = createdTimestamp;
    }

    public Map<String, List<String>> getAttributes() {
      return attributes;
    }

    public void setAttributes(Map<String, List<String>> attributes) {
      this.attributes = attributes;
    }
  }
}
