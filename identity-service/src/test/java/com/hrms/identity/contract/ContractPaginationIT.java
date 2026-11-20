package com.hrms.identity.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.employee.EmployeeStatus;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test PostgreSQL cho query phân trang contract: scope employee phải lọc ở DB,
 * không load toàn bộ rồi filter trong bộ nhớ.
 */
@SpringBootTest
@Testcontainers
class ContractPaginationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("hrms.outbox.enabled", () -> "false");
    registry.add("hrms.seed.enabled", () -> "false");
    registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    registry.add("spring.kafka.properties.schema.registry.url",
        () -> "http://localhost:8182/apis/ccompat/v7");
  }

  @Autowired
  EmployeeRepository employeeRepository;

  @Autowired
  ContractRepository contractRepository;

  @Test
  void search_withScopeIds_returnsOnlyAllowedEmployees() {
    Employee allowed = employee("ITPAG1");
    Employee denied = employee("ITPAG2");
    contract(allowed, ContractStatus.ACTIVE, LocalDate.now().plusDays(10));
    contract(denied, ContractStatus.ACTIVE, LocalDate.now().plusDays(10));

    Page<Contract> page = contractRepository.search(null, null, Set.of(allowed.getId()),
        PageRequest.of(0, 20, Sort.by("createdAt").descending()));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).extracting(Contract::getEmployeeId)
        .containsExactly(allowed.getId());
  }

  @Test
  void findExpiring_filtersByStatusDateAndScope() {
    Employee allowed = employee("ITPAG3");
    Employee denied = employee("ITPAG4");
    contract(allowed, ContractStatus.ACTIVE, LocalDate.now().plusDays(10));
    contract(allowed, ContractStatus.EXPIRED, LocalDate.now().plusDays(10));
    contract(denied, ContractStatus.ACTIVE, LocalDate.now().plusDays(10));

    Page<Contract> page = contractRepository.findExpiring(ContractStatus.ACTIVE,
        LocalDate.now(), LocalDate.now().plusDays(30), Set.of(allowed.getId()),
        PageRequest.of(0, 20, Sort.by("endDate").ascending()));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).extracting(Contract::getEmployeeId)
        .containsExactly(allowed.getId());
  }

  private Employee employee(String code) {
    Employee employee = new Employee();
    employee.setEmployeeCode(code);
    employee.setFullName("Pagination IT " + code);
    employee.setStatus(EmployeeStatus.ACTIVE);
    return employeeRepository.save(employee);
  }

  private Contract contract(Employee employee, ContractStatus status, LocalDate endDate) {
    Contract contract = new Contract();
    contract.setEmployee(employee);
    contract.setContractType(ContractType.FULL_TIME);
    contract.setStartDate(LocalDate.now().minusMonths(1));
    contract.setEndDate(endDate);
    contract.setStatus(status);
    return contractRepository.save(contract);
  }
}
