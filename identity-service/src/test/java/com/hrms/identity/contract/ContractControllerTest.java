package com.hrms.identity.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.identity.config.SecurityConfig;
import com.hrms.identity.contract.dto.ContractResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test RBAC + route cho contract evolution: PENDING khi startDate tương lai,
 * gia hạn/thay thế/expiring-soon. Logic nghiệp vụ nằm ở ContractServiceTest-level verify
 * qua script E2E (verify-identity.sh section 11).
 */
@WebMvcTest(controllers = ContractController.class)
@Import(SecurityConfig.class)
class ContractControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ContractService contractService;

  private static final SimpleGrantedAuthority ADMIN =
      new SimpleGrantedAuthority("ROLE_ADMIN");
  private static final SimpleGrantedAuthority MANAGER =
      new SimpleGrantedAuthority("ROLE_MANAGER");
  private static final SimpleGrantedAuthority EMPLOYEE =
      new SimpleGrantedAuthority("ROLE_EMPLOYEE");

  private final UUID employeeId = UUID.randomUUID();
  private final UUID contractId = UUID.randomUUID();

  @Test
  void create_withManager_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/employees/{id}/contracts", employeeId)
            .with(jwt().authorities(MANAGER))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"contractType\":\"FULL_TIME\",\"startDate\":\"2030-01-01\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void create_futureStartDate_returnsPending() throws Exception {
    when(contractService.create(eq(employeeId), any())).thenReturn(
        pendingResponse());

    mockMvc.perform(post("/api/v1/employees/{id}/contracts", employeeId)
            .with(jwt().authorities(ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"contractType\":\"FULL_TIME\",\"startDate\":\"2030-01-01\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.effectiveStatus").value("NOT_YET_EFFECTIVE"));
  }

  @Test
  void expiringSoon_withAdmin_returns200() throws Exception {
    when(contractService.expiringSoon(eq(30), any())).thenReturn(List.of(activeResponse()));

    mockMvc.perform(get("/api/v1/contracts/expiring-soon")
            .with(jwt().authorities(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].effectiveStatus").value("IN_EFFECT"));
  }

  @Test
  void expiringSoon_withEmployee_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/contracts/expiring-soon")
            .with(jwt().authorities(EMPLOYEE)))
        .andExpect(status().isForbidden());
  }

  @Test
  void extend_withAdmin_returns200() throws Exception {
    when(contractService.extend(eq(contractId), any())).thenReturn(activeResponse());

    mockMvc.perform(post("/api/v1/contracts/{id}/extend", contractId)
            .with(jwt().authorities(ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"endDate\":\"2028-12-31\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.endDate").value("2026-11-01"));
  }

  @Test
  void replace_withAdmin_returns201() throws Exception {
    when(contractService.replace(eq(contractId), any())).thenReturn(pendingResponse());

    mockMvc.perform(post("/api/v1/contracts/{id}/replace", contractId)
            .with(jwt().authorities(ADMIN))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"contractType\":\"FULL_TIME\",\"startDate\":\"2030-01-01\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void terminate_withManager_returns403() throws Exception {
    mockMvc.perform(patch("/api/v1/contracts/{id}/terminate", contractId)
            .with(jwt().authorities(MANAGER)))
        .andExpect(status().isForbidden());
  }

  private ContractResponse pendingResponse() {
    return new ContractResponse(contractId, employeeId, "nthieu", "Nguyễn Văn Thiệu",
        "FULL_TIME", LocalDate.of(2030, 1, 1), LocalDate.of(2031, 12, 31),
        "Developer", 20_000_000L, LocalDate.of(2029, 12, 20), null,
        "PENDING", "NOT_YET_EFFECTIVE", null);
  }

  private ContractResponse activeResponse() {
    return new ContractResponse(contractId, employeeId, "nthieu", "Nguyễn Văn Thiệu",
        "FULL_TIME", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 1),
        "Developer", 20_000_000L, LocalDate.of(2024, 12, 20), null,
        "ACTIVE", "IN_EFFECT", null);
  }
}
