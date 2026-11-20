package com.hrms.identity.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.web.PageResponse;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.history.dto.JobHistoryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class JobHistoryServiceTest {

  @Mock
  private EmployeeJobHistoryRepository historyRepository;

  @Mock
  private EmployeeRepository employeeRepository;

  @InjectMocks
  private JobHistoryService jobHistoryService;

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
}
