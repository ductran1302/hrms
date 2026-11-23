package com.hrms.identity.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.security.Roles;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.history.dto.JobHistoryResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JobHistoryServiceTest {

  @Mock
  private EmployeeJobHistoryRepository historyRepository;

  @Mock
  private EmployeeRepository employeeRepository;

  @InjectMocks
  private JobHistoryService jobHistoryService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void listByEmployee_clampsPageAndSize() {
    UUID employeeId = UUID.randomUUID();
    when(historyRepository.findByEmployee_Id(eq(employeeId), any(Pageable.class)))
        .thenAnswer(invocation -> new PageImpl<EmployeeJobHistory>(
            List.of(), invocation.getArgument(1), 0));

    PageResponse<JobHistoryResponse> response =
        jobHistoryService.listByEmployee(employeeId, -2, 999);

    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(200);
    assertThat(response.total()).isZero();

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(historyRepository).findByEmployee_Id(eq(employeeId), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
  }

  @Test
  void listByEmployee_managerRole_hidesOnlyContractSalaryValues() {
    Employee employee = employee();
    EmployeeJobHistory salary = entry(employee, "contract.salary", "1000", "2000");
    EmployeeJobHistory position = entry(employee, "position", "DEV", "SENIOR");
    when(historyRepository.findByEmployee_Id(eq(employee.getId()), any(Pageable.class)))
        .thenAnswer(invocation -> new PageImpl<EmployeeJobHistory>(
            List.of(salary, position), invocation.getArgument(1), 2));

    authenticate(Roles.MANAGER);
    PageResponse<JobHistoryResponse> response =
        jobHistoryService.listByEmployee(employee.getId(), 0, 20);

    JobHistoryResponse hidden = response.items().get(0);
    assertThat(hidden.field()).isEqualTo("contract.salary");
    assertThat(hidden.oldValue()).isNull();
    assertThat(hidden.newValue()).isNull();

    JobHistoryResponse visible = response.items().get(1);
    assertThat(visible.field()).isEqualTo("position");
    assertThat(visible.oldValue()).isEqualTo("DEV");
    assertThat(visible.newValue()).isEqualTo("SENIOR");
  }

  @Test
  void listByEmployee_globalLeadRole_showsContractSalaryValues() {
    Employee employee = employee();
    EmployeeJobHistory salary = entry(employee, "contract.salary", "1000", "2000");
    when(historyRepository.findByEmployee_Id(eq(employee.getId()), any(Pageable.class)))
        .thenAnswer(invocation -> new PageImpl<EmployeeJobHistory>(
            List.of(salary), invocation.getArgument(1), 1));

    authenticate(Roles.GLOBAL_LEAD);
    PageResponse<JobHistoryResponse> response =
        jobHistoryService.listByEmployee(employee.getId(), 0, 20);

    assertThat(response.items().get(0).oldValue()).isEqualTo("1000");
    assertThat(response.items().get(0).newValue()).isEqualTo("2000");
  }

  @Test
  void listByEmployee_noAuthentication_failsClosedAndHidesSalary() {
    Employee employee = employee();
    EmployeeJobHistory salary = entry(employee, "contract.salary", "1000", "2000");
    when(historyRepository.findByEmployee_Id(eq(employee.getId()), any(Pageable.class)))
        .thenAnswer(invocation -> new PageImpl<EmployeeJobHistory>(
            List.of(salary), invocation.getArgument(1), 1));

    PageResponse<JobHistoryResponse> response =
        jobHistoryService.listByEmployee(employee.getId(), 0, 20);

    assertThat(response.items().get(0).oldValue()).isNull();
    assertThat(response.items().get(0).newValue()).isNull();
  }

  private static Employee employee() {
    Employee employee = new Employee();
    employee.setId(UUID.randomUUID());
    employee.setEmployeeCode("NV0001");
    employee.setFullName("Nguyen Van A");
    return employee;
  }

  private static EmployeeJobHistory entry(Employee employee, String field, String oldValue,
      String newValue) {
    EmployeeJobHistory entry = new EmployeeJobHistory();
    entry.setId(UUID.randomUUID());
    entry.setEmployee(employee);
    entry.setEffectiveDate(Instant.now());
    entry.setField(field);
    entry.setOldValue(oldValue);
    entry.setNewValue(newValue);
    entry.setActorId("hr");
    return entry;
  }

  private static void authenticate(String... roles) {
    List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("actor", null, authorities));
  }
}
