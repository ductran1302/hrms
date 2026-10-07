package com.hrms.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Map role trong realm Keycloak ({@code realm_access.roles}) sang Spring authority dạng
 * {@code ROLE_<name>} để dùng với {@code hasRole(...)}.
 *
 * <p>Cấu hình ở service:
 * <pre>
 * http.oauth2ResourceServer(oauth2 -&gt; oauth2.jwt(jwt -&gt; jwt.jwtAuthenticationConverter(
 *     new KeycloakRealmRolesConverter())));
 * </pre>
 */
public class KeycloakRealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  public static final String ROLE_PREFIX = "ROLE_";
  private static final String CLAIM_REALM_ACCESS = "realm_access";
  private static final String CLAIM_ROLES = "roles";

  @Override
  @SuppressWarnings("unchecked")
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS);
    if (realmAccess == null) {
      return List.of();
    }
    Object roles = realmAccess.get(CLAIM_ROLES);
    if (!(roles instanceof Collection<?> roleNames)) {
      return List.of();
    }
    return roleNames.stream()
        .filter(java.util.Objects::nonNull)
        .map(Object::toString)
        .distinct()
        .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + name))
        .toList();
  }
}
