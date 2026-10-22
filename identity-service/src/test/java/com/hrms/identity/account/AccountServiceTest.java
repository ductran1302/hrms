package com.hrms.identity.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.outbox.OutboxRepository;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.account.dto.AccountResponse;
import com.hrms.identity.account.dto.ChangePasswordRequest;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import com.hrms.identity.keycloak.KeycloakAdminClient.AccountSnapshot;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

  @Mock
  private KeycloakAdminClient keycloakAdminClient;

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private OutboxRepository outboxRepository;

  @InjectMocks
  private AccountService accountService;

  @Test
  void search_withResults_returnsMappedPage() {
    when(keycloakAdminClient.countUsers("thieu")).thenReturn(2L);
    when(keycloakAdminClient.listUsers(eq("thieu"), anyInt(), anyInt()))
        .thenReturn(List.of(snapshot("nthieu"), snapshot("nthieu1")));

    PageResponse<AccountResponse> page = accountService.search("thieu", 0, 20);

    assertEquals(2, page.total());
    assertEquals(2, page.items().size());
    AccountResponse first = page.items().get(0);
    assertEquals("nthieu", first.username());
    assertEquals("Nguyễn Trung Hiếu", first.fullName());
    assertEquals(List.of("ADMIN"), first.roles());
    assertEquals("hr", first.createdBy());
    assertEquals("hr", first.updatedBy());
  }

  @Test
  void search_pageBeyondTotal_skipsListCall() {
    when(keycloakAdminClient.countUsers("")).thenReturn(0L);

    PageResponse<AccountResponse> page = accountService.search(null, 5, 20);

    assertEquals(0, page.total());
    assertEquals(0, page.items().size());
    verify(keycloakAdminClient, never()).listUsers(anyString(), anyInt(), anyInt());
  }

  @Test
  void search_normalizesPageAndSize() {
    when(keycloakAdminClient.countUsers("")).thenReturn(1L);
    when(keycloakAdminClient.listUsers(eq(""), anyInt(), anyInt()))
        .thenReturn(List.of(snapshot("hr")));

    PageResponse<AccountResponse> page = accountService.search("  ", -1, 0);

    assertEquals(0, page.page());
    assertEquals(1, page.size());
    verify(keycloakAdminClient).listUsers("", 0, 1);
  }

  @Test
  void changeOwnPassword_wrongCurrentPassword_throwsWithoutReset() {
    when(keycloakAdminClient.verifyUserPassword("nthieu", "sai-mat-khau")).thenReturn(false);

    assertThrows(BusinessRuleException.class,
        () -> accountService.changeOwnPassword(
            "nthieu", new ChangePasswordRequest("sai-mat-khau", "moi-123456")));

    verify(keycloakAdminClient, never()).resetPassword(anyString(), anyString());
  }

  @Test
  void changeOwnPassword_correctCurrentPassword_resetsPassword() {
    when(keycloakAdminClient.verifyUserPassword("nthieu", "cu-123456")).thenReturn(true);

    accountService.changeOwnPassword("nthieu", new ChangePasswordRequest("cu-123456", "moi-123456"));

    verify(keycloakAdminClient).resetPassword("nthieu", "moi-123456");
  }

  @Test
  void setEnabled_disableOwnAccount_throws() {
    assertThrows(BusinessRuleException.class,
        () -> accountService.setEnabled("hr", false, "hr"));

    verify(keycloakAdminClient, never()).setUserEnabled(anyString(), eq(false));
  }

  @Test
  void setEnabled_disableOtherAccount_disablesIt() {
    accountService.setEnabled("nthieu", false, "hr");

    verify(keycloakAdminClient).setUserEnabled("nthieu", false);
  }

  @Test
  void resetPassword_delegatesToKeycloak() {
    accountService.resetPassword("nthieu", "new-123456");

    verify(keycloakAdminClient).resetPassword("nthieu", "new-123456");
  }

  // ---- Đổi roles (5-role system) ----

  @Test
  void updateRoles_validRoles_updatesInKeycloak() {
    accountService.updateRoles("nthieu", List.of("DEPT_LEAD", "EMPLOYEE"), "hr");

    verify(keycloakAdminClient).updateRealmRoles("nthieu", List.of("DEPT_LEAD", "EMPLOYEE"));
  }

  @Test
  void updateRoles_unknownRole_throwsBusinessRule() {
    assertThrows(BusinessRuleException.class,
        () -> accountService.updateRoles("nthieu", List.of("SUPER_ADMIN"), "hr"));

    verify(keycloakAdminClient, never()).updateRealmRoles(anyString(), any());
  }

  @Test
  void updateRoles_emptyRoles_throwsBusinessRule() {
    assertThrows(BusinessRuleException.class,
        () -> accountService.updateRoles("nthieu", List.of(), "hr"));

    verify(keycloakAdminClient, never()).updateRealmRoles(anyString(), any());
  }

  @Test
  void updateRoles_selfDemotion_throwsBusinessRule() {
    when(keycloakAdminClient.realmRolesByUsername("hr")).thenReturn(List.of("ADMIN"));

    assertThrows(BusinessRuleException.class,
        () -> accountService.updateRoles("hr", List.of("MANAGER"), "hr"));

    verify(keycloakAdminClient, never()).updateRealmRoles(anyString(), any());
  }

  @Test
  void updateRoles_selfSameLevelOrHigher_passes() {
    when(keycloakAdminClient.realmRolesByUsername("lead1")).thenReturn(List.of("DEPT_LEAD"));

    accountService.updateRoles("lead1", List.of("GLOBAL_LEAD", "DEPT_LEAD"), "lead1");

    verify(keycloakAdminClient).updateRealmRoles("lead1", List.of("GLOBAL_LEAD", "DEPT_LEAD"));
  }

  @Test
  void updateRoles_mapsToEmployee_emitsOutboxWithRoles() {
    when(employeeRepository.findByEmployeeCode("nthieu")).thenReturn(
        java.util.Optional.of(employee("nthieu")));

    accountService.updateRoles("nthieu", List.of("DEPT_LEAD"), "hr");

    verify(keycloakAdminClient).updateRealmRoles("nthieu", List.of("DEPT_LEAD"));
    verify(outboxRepository).save(any());
  }

  @Test
  void updateRoles_userWithoutEmployee_skipsOutbox() {
    when(employeeRepository.findByEmployeeCode("admin-realm")).thenReturn(java.util.Optional.empty());

    accountService.updateRoles("admin-realm", List.of("ADMIN"), "hr");

    verify(keycloakAdminClient).updateRealmRoles("admin-realm", List.of("ADMIN"));
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void resyncRoleEvents_emitsOnlyForMappedUsers() {
    when(keycloakAdminClient.listUsers(eq(""), anyInt(), anyInt()))
        .thenReturn(List.of(snapshot("nthieu"), snapshot("khong-phai-nv")));
    when(employeeRepository.findByEmployeeCode("nthieu")).thenReturn(
        java.util.Optional.of(employee("nthieu")));
    when(employeeRepository.findByEmployeeCode("khong-phai-nv")).thenReturn(
        java.util.Optional.empty());

    int emitted = accountService.resyncRoleEvents();

    assertEquals(1, emitted);
    verify(outboxRepository).save(any());
  }

  private com.hrms.identity.employee.Employee employee(String code) {
    com.hrms.identity.employee.Employee employee = new com.hrms.identity.employee.Employee();
    employee.setId(UUID.randomUUID());
    employee.setEmployeeCode(code);
    employee.setFullName("Nguyễn Trung Hiếu");
    employee.setStatus(com.hrms.identity.employee.EmployeeStatus.ACTIVE);
    return employee;
  }

  private AccountSnapshot snapshot(String username) {
    return new AccountSnapshot(
        UUID.randomUUID().toString(),
        username,
        "Nguyễn Trung Hiếu",
        username + "@hrms.vn",
        true,
        1_700_000_000_000L,
        List.of("ADMIN"),
        "hr",
        "hr",
        "2026-01-01T00:00:00Z");
  }
}
