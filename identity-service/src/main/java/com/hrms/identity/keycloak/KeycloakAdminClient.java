package com.hrms.identity.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Gọi Keycloak Admin API — tạo user khi tạo nhân viên (mã NV = username đăng nhập),
 * kiểm tra username trùng, bật/tắt tài khoản khi nghỉ việc.
 *
 * <p>Admin token lấy qua password-grant {@code admin-cli} (realm master) và cache đến
 * khi gần hết hạn. Lỗi mạng được retry 3 lần rồi ném {@link BusinessRuleException}.
 */
@Slf4j
@Service
public class KeycloakAdminClient {

  private static final int MAX_ATTEMPTS = 3;

  private final RestClient restClient;
  private final String realm;
  private final String adminUsername;
  private final String adminPassword;
  private final String defaultPassword;

  private volatile String adminToken;
  private volatile Instant adminTokenExpiresAt = Instant.EPOCH;

  public KeycloakAdminClient(
      @Value("${hrms.keycloak.admin.base-url:http://keycloak:8080}") String baseUrl,
      @Value("${hrms.keycloak.admin.realm:hrms}") String realm,
      @Value("${hrms.keycloak.admin.username:admin}") String adminUsername,
      @Value("${hrms.keycloak.admin.password:admin}") String adminPassword,
      @Value("${hrms.keycloak.admin.default-password:hrms@123}") String defaultPassword) {
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    this.realm = realm;
    this.adminUsername = adminUsername;
    this.adminPassword = adminPassword;
    this.defaultPassword = defaultPassword;
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
   * Tạo user Keycloak: role EMPLOYEE, mật khẩu mặc định (không temporary).
   *
   * @throws ConflictException nếu username đã tồn tại trong Keycloak
   */
  public void createUser(String username, String fullName, String email) {
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
        "value", defaultPassword,
        "temporary", false)));
    body.put("realmRoles", List.of("EMPLOYEE"));

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
    log.info("Đã tạo tài khoản Keycloak '{}' (role EMPLOYEE)", username);
  }

  /** Bật/tắt tài khoản (nghỉ việc → disabled). Không ném lỗi nếu user không tìm thấy. */
  public void setUserEnabled(String username, boolean enabled) {
    User[] matches = withRetry("tìm user " + username, () -> restClient.get()
        .uri("/admin/realms/{realm}/users?username={username}&exact=true", realm, username)
        .header("Authorization", "Bearer " + adminToken())
        .retrieve()
        .body(User[].class));
    if (matches == null || matches.length == 0) {
      log.warn("Không tìm thấy user Keycloak '{}' để {}", username, enabled ? "bật" : "tắt");
      return;
    }
    User full = withRetry("đọc user " + username, () -> restClient.get()
        .uri("/admin/realms/{realm}/users/{id}", realm, matches[0].getId())
        .header("Authorization", "Bearer " + adminToken())
        .retrieve()
        .body(User.class));
    full.setEnabled(enabled);
    withRetry("cập nhật user " + username, () -> restClient.put()
        .uri("/admin/realms/{realm}/users/{id}", realm, full.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + adminToken())
        .body(full)
        .retrieve()
        .toBodilessEntity());
    log.info("Đã {} tài khoản Keycloak '{}'", enabled ? "bật" : "tắt", username);
  }

  private synchronized String adminToken() {
    if (adminToken != null && Instant.now().isBefore(adminTokenExpiresAt)) {
      return adminToken;
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", "admin-cli");
    form.add("username", adminUsername);
    form.add("password", adminPassword);
    TokenResponse token = withRetry("lấy admin token", () -> restClient.post()
        .uri("/realms/master/protocol/openid-connect/token")
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

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenResponse(String access_token, long expires_in) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class User {
    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private boolean enabled;

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
  }
}
