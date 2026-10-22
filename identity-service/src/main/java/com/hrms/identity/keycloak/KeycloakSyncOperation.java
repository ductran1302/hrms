package com.hrms.identity.keycloak;

/** Loại thao tác Keycloak được xếp hàng (P0-6 — không gọi KC trong transaction nghiệp vụ). */
public enum KeycloakSyncOperation {
  CREATE_USER,
  SET_ENABLED
}
