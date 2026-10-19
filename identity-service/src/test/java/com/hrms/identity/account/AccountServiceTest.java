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
import com.hrms.common.web.PageResponse;
import com.hrms.identity.account.dto.AccountResponse;
import com.hrms.identity.account.dto.ChangePasswordRequest;
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
    assertEquals(List.of("HR_ADMIN"), first.roles());
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

  private AccountSnapshot snapshot(String username) {
    return new AccountSnapshot(
        UUID.randomUUID().toString(),
        username,
        "Nguyễn Trung Hiếu",
        username + "@hrms.vn",
        true,
        1_700_000_000_000L,
        List.of("HR_ADMIN"),
        "hr",
        "hr",
        "2026-01-01T00:00:00Z");
  }
}
