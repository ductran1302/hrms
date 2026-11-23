package com.hrms.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.security.Roles;
import com.hrms.identity.account.dto.RoleAuditResponse;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.employee.RoleAssignmentService;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import com.hrms.identity.keycloak.KeycloakAdminClient.AccountSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleAuditServiceTest {

  @Mock
  private KeycloakAdminClient keycloakAdminClient;

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private RoleAssignmentService roleAssignmentService;

  @Mock
  private AuditService auditService;

  @InjectMocks
  private RoleAuditService service;

  @Test
  void audit_dryRun_reportsSingleWrongRoleWithoutChangingKeycloak() {
    Employee employee = employee("emp1");
    when(keycloakAdminClient.listUsers("", 0, 100))
        .thenReturn(List.of(snapshot("emp1", "GLOBAL_LEAD")));
    when(employeeRepository.findByEmployeeCode("emp1")).thenReturn(Optional.of(employee));
    when(roleAssignmentService.resolveDefaultRole(employee.getId())).thenReturn(Roles.EMPLOYEE);

    RoleAuditResponse response = service.audit(null, false);

    assertThat(response.applied()).isFalse();
    assertThat(response.scanned()).isEqualTo(1);
    assertThat(response.mapped()).isEqualTo(1);
    assertThat(response.ok()).isZero();
    assertThat(response.corrected()).isZero();
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).action()).isEqualTo(RoleAuditAction.WOULD_CORRECT);
    assertThat(response.items().get(0).currentRoles()).containsExactly("GLOBAL_LEAD");
    assertThat(response.items().get(0).expectedRole()).isEqualTo(Roles.EMPLOYEE);
    verify(keycloakAdminClient, never()).updateRealmRoles(anyString(), anyList());
  }

  @Test
  void audit_apply_correctsSingleWrongRoleAndKeepsNonHrmsRoles() {
    Employee employee = employee("emp1");
    when(keycloakAdminClient.listUsers("", 0, 100))
        .thenReturn(List.of(snapshot("emp1", "GLOBAL_LEAD", "offline_access")));
    when(employeeRepository.findByEmployeeCode("emp1")).thenReturn(Optional.of(employee));
    when(roleAssignmentService.resolveDefaultRole(employee.getId())).thenReturn(Roles.EMPLOYEE);

    RoleAuditResponse response = service.audit("", true);

    assertThat(response.applied()).isTrue();
    assertThat(response.corrected()).isEqualTo(1);
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).action()).isEqualTo(RoleAuditAction.CORRECTED);
    verify(keycloakAdminClient).updateRealmRoles(
        "emp1", List.of("offline_access", Roles.EMPLOYEE));
    verify(auditService).success(eq("ACCOUNT_ROLES_RECONCILE"), eq("Account"), eq("emp1"),
        contains("to=EMPLOYEE"));
    verify(auditService).success(eq("ACCOUNT_ROLES_AUDIT"), eq("Account"), isNull(),
        contains("corrected=1"));
  }

  @Test
  void audit_apply_skipsAccountWithAdminRole() {
    Employee employee = employee("emp1");
    when(keycloakAdminClient.listUsers("", 0, 100))
        .thenReturn(List.of(snapshot("emp1", "ADMIN", "EMPLOYEE")));
    when(employeeRepository.findByEmployeeCode("emp1")).thenReturn(Optional.of(employee));
    when(roleAssignmentService.resolveDefaultRole(employee.getId())).thenReturn(Roles.EMPLOYEE);

    RoleAuditResponse response = service.audit(null, true);

    assertThat(response.skippedAdmin()).isEqualTo(1);
    assertThat(response.corrected()).isZero();
    assertThat(response.items().get(0).action()).isEqualTo(RoleAuditAction.SKIP_ADMIN);
    verify(keycloakAdminClient, never()).updateRealmRoles(anyString(), anyList());
  }

  @Test
  void audit_multipleHrmsRoles_requiresManualReview() {
    Employee employee = employee("emp1");
    when(keycloakAdminClient.listUsers("", 0, 100))
        .thenReturn(List.of(snapshot("emp1", "DEPT_LEAD", "MANAGER")));
    when(employeeRepository.findByEmployeeCode("emp1")).thenReturn(Optional.of(employee));
    when(roleAssignmentService.resolveDefaultRole(employee.getId())).thenReturn(Roles.DEPT_LEAD);

    RoleAuditResponse response = service.audit(null, true);

    assertThat(response.reviewRequired()).isEqualTo(1);
    assertThat(response.items().get(0).action()).isEqualTo(RoleAuditAction.REVIEW);
    verify(keycloakAdminClient, never()).updateRealmRoles(anyString(), anyList());
  }

  @Test
  void audit_usernameWithoutEmployee_isUnmapped() {
    when(keycloakAdminClient.listUsers("", 0, 100))
        .thenReturn(List.of(snapshot("ghost", "EMPLOYEE")));
    when(employeeRepository.findByEmployeeCode("ghost")).thenReturn(Optional.empty());

    RoleAuditResponse response = service.audit(null, true);

    assertThat(response.unmapped()).isEqualTo(1);
    assertThat(response.mapped()).isZero();
    assertThat(response.items().get(0).action()).isEqualTo(RoleAuditAction.NO_EMPLOYEE);
    assertThat(response.items().get(0).employeeCode()).isNull();
    assertThat(response.items().get(0).expectedRole()).isNull();
    verify(roleAssignmentService, never()).resolveDefaultRole(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void audit_matchingRole_countsOkAndReturnsNoItems() {
    Employee employee = employee("emp1");
    when(keycloakAdminClient.listUsers("", 0, 100))
        .thenReturn(List.of(snapshot("emp1", "EMPLOYEE", "offline_access")));
    when(employeeRepository.findByEmployeeCode("emp1")).thenReturn(Optional.of(employee));
    when(roleAssignmentService.resolveDefaultRole(employee.getId())).thenReturn(Roles.EMPLOYEE);

    RoleAuditResponse response = service.audit(null, false);

    assertThat(response.ok()).isEqualTo(1);
    assertThat(response.items()).isEmpty();
    verify(keycloakAdminClient, never()).updateRealmRoles(anyString(), anyList());
  }

  @Test
  void audit_usernameCaseDiffersFromEmployeeCode_matchesIgnoringCase() {
    Employee employee = employee("NVIT001");
    when(keycloakAdminClient.listUsers("", 0, 100))
        .thenReturn(List.of(snapshot("nvit001", "EMPLOYEE")));
    when(employeeRepository.findByEmployeeCode("nvit001")).thenReturn(Optional.empty());
    when(employeeRepository.findByEmployeeCodeIgnoreCase("nvit001"))
        .thenReturn(Optional.of(employee));
    when(roleAssignmentService.resolveDefaultRole(employee.getId())).thenReturn(Roles.EMPLOYEE);

    RoleAuditResponse response = service.audit(null, false);

    assertThat(response.mapped()).isEqualTo(1);
    assertThat(response.unmapped()).isZero();
    assertThat(response.ok()).isEqualTo(1);
    assertThat(response.items()).isEmpty();
  }

  @Test
  void audit_apply_keycloakFailure_isReportedAndAudited() {
    Employee employee = employee("emp1");
    when(keycloakAdminClient.listUsers("", 0, 100))
        .thenReturn(List.of(snapshot("emp1", "DEPT_LEAD")));
    when(employeeRepository.findByEmployeeCode("emp1")).thenReturn(Optional.of(employee));
    when(roleAssignmentService.resolveDefaultRole(employee.getId())).thenReturn(Roles.EMPLOYEE);
    doThrow(new BusinessRuleException("KC down"))
        .when(keycloakAdminClient).updateRealmRoles(eq("emp1"), anyList());

    RoleAuditResponse response = service.audit(null, true);

    assertThat(response.failed()).isEqualTo(1);
    assertThat(response.corrected()).isZero();
    assertThat(response.items().get(0).action()).isEqualTo(RoleAuditAction.FAILED);
    assertThat(response.items().get(0).message()).contains("KC down");
    verify(auditService).failure(eq("ACCOUNT_ROLES_RECONCILE"), eq("Account"), eq("emp1"),
        contains("error=KC down"));
  }

  private static Employee employee(String code) {
    Employee employee = new Employee();
    employee.setId(UUID.randomUUID());
    employee.setEmployeeCode(code);
    employee.setFullName("Nguyen Van A");
    return employee;
  }

  private static AccountSnapshot snapshot(String username, String... roles) {
    return new AccountSnapshot(
        UUID.randomUUID().toString(),
        username,
        "Nguyen Van A",
        username + "@hrms.vn",
        true,
        0L,
        List.of(roles),
        null,
        null,
        null);
  }
}
