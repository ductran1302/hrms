package com.hrms.attendance.employee;

import static org.assertj.core.api.Assertions.assertThat;

import com.hrms.attendance.event.ProcessedEventRepository;
import com.hrms.attendance.leave.LeaveEmployeeNote;
import com.hrms.attendance.leave.LeaveEmployeeNoteRepository;
import com.hrms.attendance.leave.LeaveEntitlement;
import com.hrms.attendance.leave.LeaveEntitlementRepository;
import com.hrms.attendance.leave.LeaveSource;
import com.hrms.attendance.leave.LeaveWorkDay;
import com.hrms.attendance.leave.LeaveWorkDayRepository;
import com.hrms.attendance.leave.WorkDaySource;
import com.hrms.attendance.shift.ShiftAssignment;
import com.hrms.attendance.shift.ShiftAssignmentRepository;
import com.hrms.attendance.shift.ShiftType;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test P0-2: projection nhân sự từ Kafka event trên PostgreSQL thật.
 *
 * <p>Bao phủ 2 regression quan trọng:
 * <ul>
 *   <li>Idempotency: replay cùng eventId không ghi thêm.</li>
 *   <li>Re-key: identity tạo lại nhân viên (id mới, cùng employeeCode) → dời FK
 *       (ca, quota, ghi chú, ngày công) sang id mới, không vi phạm unique/FK.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
class EmployeeProjectionIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    registry.add("spring.kafka.properties.schema.registry.url", () -> "http://localhost:8081");
    registry.add("spring.kafka.listener.auto-startup", () -> "false");
  }

  @Autowired
  EmployeeService employeeService;
  @Autowired
  EmployeeRepository employeeRepository;
  @Autowired
  ProcessedEventRepository processedEventRepository;
  @Autowired
  ShiftAssignmentRepository shiftAssignmentRepository;
  @Autowired
  WorkShiftRepository workShiftRepository;
  @Autowired
  LeaveEntitlementRepository leaveEntitlementRepository;
  @Autowired
  LeaveEmployeeNoteRepository leaveNoteRepository;
  @Autowired
  LeaveWorkDayRepository leaveWorkDayRepository;

  @Test
  void applyCreated_isIdempotentByEventId() {
    EmployeeCreatedEvent event = created("evt-idem-1", "emp-idem-1", "IT1001");

    employeeService.applyCreated(event);
    // Replay cùng eventId: nếu không dedupe, markProcessed sẽ vi phạm PK processed_events.
    employeeService.applyCreated(event);

    assertThat(employeeRepository.findByEmployeeCode("IT1001")).isPresent();
    assertThat(processedEventRepository.findById("evt-idem-1")).isPresent();
  }

  @Test
  void applyCreated_whenEmployeeCodeReusedWithNewId_rekeysChildren() {
    String code = "IT2001";
    String oldId = "emp-old-2001";
    String newId = "emp-new-2001";
    employeeService.applyCreated(created("evt-old-2001", oldId, code));

    WorkShift shift = new WorkShift();
    shift.setId(UUID.randomUUID());
    shift.setName("IT Shift " + UUID.randomUUID());
    shift.setShiftType(ShiftType.FIXED);
    shift.setStartWorkTime(LocalTime.of(8, 0));
    shift.setEndWorkTime(LocalTime.of(17, 0));
    shift.setStartBreakTime(LocalTime.of(12, 0));
    shift.setEndBreakTime(LocalTime.of(13, 0));
    shift.setWorkingTime(480);
    shift.setBreakingTime(60);
    shift.setActive(true);
    workShiftRepository.save(shift);

    ShiftAssignment assignment = new ShiftAssignment();
    assignment.setId(UUID.randomUUID());
    assignment.setEmployeeId(oldId);
    assignment.setWorkShiftId(shift.getId());
    assignment.setEffectiveFrom(LocalDate.of(2024, 1, 1));
    shiftAssignmentRepository.save(assignment);

    LeaveEntitlement entitlement = new LeaveEntitlement();
    entitlement.setId(UUID.randomUUID());
    entitlement.setEmployeeId(oldId);
    entitlement.setYear(2024);
    entitlement.setSource(LeaveSource.BONUS);
    entitlement.setDays(new BigDecimal("1.5"));
    entitlement.setAuto(false);
    leaveEntitlementRepository.save(entitlement);

    LeaveEmployeeNote note = new LeaveEmployeeNote();
    note.setId(UUID.randomUUID());
    note.setEmployeeId(oldId);
    note.setYear(2024);
    note.setNote("re-key");
    leaveNoteRepository.save(note);

    LeaveWorkDay workDay = new LeaveWorkDay();
    workDay.setId(UUID.randomUUID());
    workDay.setEmployeeId(oldId);
    workDay.setPeriodStart(LocalDate.of(2024, 1, 1));
    workDay.setPeriodEnd(LocalDate.of(2024, 1, 31));
    workDay.setWorkDays(new BigDecimal("20.00"));
    workDay.setSource(WorkDaySource.MANUAL);
    leaveWorkDayRepository.save(workDay);

    employeeService.applyCreated(created("evt-new-2001", newId, code));

    assertThat(employeeRepository.findById(oldId)).isEmpty();
    assertThat(employeeRepository.findById(newId)).isPresent();
    assertThat(shiftAssignmentRepository.findByEmployeeIdOrderByEffectiveFromDesc(newId))
        .hasSize(1);
    assertThat(leaveEntitlementRepository.findByEmployeeIdAndYearOrderBySourceAsc(newId, 2024))
        .hasSize(1);
    assertThat(leaveNoteRepository.findByEmployeeIdAndYear(newId, 2024)).isPresent();
    assertThat(leaveWorkDayRepository.findByEmployeeIdAndPeriodStart(newId, LocalDate.of(2024, 1, 1)))
        .isPresent();
  }

  private static EmployeeCreatedEvent created(String eventId, String employeeId, String code) {
    return EmployeeCreatedEvent.newBuilder()
        .setEventId(eventId)
        .setEventType("employee.created")
        .setOccurredAt(Instant.now())
        .setEmployeeId(employeeId)
        .setEmployeeCode(code)
        .setFullName("Nhan Vien " + code)
        .setEmail(code.toLowerCase() + "@hrms.vn")
        .setStatus("ACTIVE")
        .build();
  }
}
