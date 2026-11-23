package com.hrms.identity.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hrms.common.outbox.OutboxRepository;
import com.hrms.common.web.PageResponse;
import com.hrms.identity.department.dto.DepartmentResponse;
import com.hrms.identity.employee.EmployeeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

  @Mock
  private DepartmentRepository departmentRepository;

  @Mock
  private EmployeeRepository employeeRepository;

  @Mock
  private OutboxRepository outboxRepository;

  @InjectMocks
  private DepartmentService service;

  @Test
  void list_clampsPageAndSizeAndBatchesEmployeeCounts() {
    Department it = department("IT");
    Department hr = department("HR");
    Page<Department> page = new PageImpl<>(
        List.of(it, hr), PageRequest.of(1, 200), 500);
    when(departmentRepository.findAll(any(Pageable.class))).thenReturn(page);
    when(departmentRepository.countActiveEmployeesByDepartmentIds(
        List.of(it.getId(), hr.getId())))
        .thenReturn(List.of(activeCount(it.getId(), 7), activeCount(hr.getId(), 0)));

    PageResponse<DepartmentResponse> response = service.list(1, 500);

    assertEquals(1, response.page());
    assertEquals(200, response.size());
    assertEquals(500, response.total());
    assertEquals(7L, response.items().get(0).employeeCount());
    assertEquals(0L, response.items().get(1).employeeCount());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(departmentRepository).findAll(pageable.capture());
    assertEquals(1, pageable.getValue().getPageNumber());
    assertEquals(200, pageable.getValue().getPageSize());
    verify(departmentRepository, never()).countActiveEmployees(any());
  }

  @Test
  void list_clampsNegativePageAndZeroSize() {
    when(departmentRepository.findAll(any(Pageable.class)))
        .thenReturn(Page.<Department>empty());

    PageResponse<DepartmentResponse> response = service.list(-3, 0);

    assertEquals(0, response.page());
    assertEquals(1, response.size());
    assertTrue(response.items().isEmpty());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(departmentRepository).findAll(pageable.capture());
    assertEquals(0, pageable.getValue().getPageNumber());
    assertEquals(1, pageable.getValue().getPageSize());
  }

  @Test
  void list_emptyPage_skipsBatchEmployeeCount() {
    when(departmentRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    PageResponse<DepartmentResponse> response = service.list(0, 20);

    assertTrue(response.items().isEmpty());
    verify(departmentRepository, never()).countActiveEmployeesByDepartmentIds(anyList());
    verify(departmentRepository, never()).countActiveEmployees(any());
  }

  private Department department(String code) {
    Department department = new Department();
    department.setId(UUID.randomUUID());
    department.setCode(code);
    department.setName(code);
    department.setStatus(DepartmentStatus.ACTIVE);
    return department;
  }

  private DepartmentRepository.ActiveEmployeeCount activeCount(UUID departmentId, long count) {
    return new DepartmentRepository.ActiveEmployeeCount() {
      @Override
      public UUID getDepartmentId() {
        return departmentId;
      }

      @Override
      public long getActiveCount() {
        return count;
      }
    };
  }
}
