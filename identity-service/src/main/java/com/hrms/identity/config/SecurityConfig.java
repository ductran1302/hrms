package com.hrms.identity.config;

import java.util.Collection;

import com.hrms.common.security.KeycloakRealmRolesConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Resource server: validate JWT Keycloak (JWKS in-network), map role realm sang ROLE_xxx.
 *
 * <p>Không dùng {@code issuer-uri} của Boot vì discovery lấy theo request-host sẽ ra
 * {@code keycloak:8080} trong khi token issuer là {@code localhost:8181} — tự build decoder:
 * fetch JWKS qua {@code keycloak:8080} (mạng compose), validate {@code iss} theo giá trị cố định.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
  private String jwkSetUri;

  @Value("${hrms.keycloak.issuer}")
  private String issuer;

  @Value("${hrms.keycloak.audience}")
  private String audience;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        // Security headers: Spring defaults đã bật nosniff/X-Frame-Options/Cache-Control,
        // bổ sung Referrer-Policy (không bật CSP — service trả JSON, CSP cho SPA ở nginx)
        .headers(headers -> headers.referrerPolicy(
            ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
    return http.build();
  }

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());
    return converter;
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(jwtValidator());
    return decoder;
  }

  /**
   * Validator mặc định (timestamp + issuer) + bắt buộc claim {@code aud} chứa
   * audience của service — chống dùng token cấp cho mục đích khác.
   */
  private OAuth2TokenValidator<Jwt> jwtValidator() {
    OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<Object>(
        "aud", aud -> aud instanceof Collection<?> audiences
            ? audiences.contains(audience)
            : audience.equals(aud));
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
  }
}
