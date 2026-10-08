package com.hrms.identity.seed;

import com.hrms.identity.department.Department;
import com.hrms.identity.department.DepartmentRepository;
import com.hrms.identity.department.DepartmentService;
import com.hrms.identity.department.dto.DepartmentCreateRequest;
import com.hrms.identity.department.dto.DepartmentUpdateRequest;
import com.hrms.identity.employee.Employee;
import com.hrms.identity.employee.EmployeeRepository;
import com.hrms.identity.employee.EmployeeService;
import com.hrms.identity.employee.dto.EmployeeCreateRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seed dữ liệu mẫu phòng IT — cấu trúc cây đủ 3 cấp để màn "Sơ đồ tổ chức"
 * hiển thị cây có ý nghĩa ngay khi demo.
 *
 * <pre>
 * Trần Đình Khoa (Giám đốc CNTT)          ← root org-chart (không có manager)
 * ├── Lê Thu Hà (Trưởng phòng Backend)
 * │   ├── Nguyễn Văn Bình
 * │   └── Đỗ Thanh Mai
 * ├── Phạm Minh Tuấn (Trưởng phòng Frontend)
 * │   └── Hoàng Gia Huy
 * └── Vũ Ngọc Sơn (Trưởng phòng OPS)
 *     └── Bùi Thị Lan
 * </pre>
 *
 * Idempotent: mã NVITxx hoặc phòng IT đã tồn tại thì bỏ qua — restart bao nhiêu
 * lần cũng không nhân đôi. Tắt bằng {@code hrms.seed.enabled=false}.
 * Tạo qua Service (không insert thẳng) để outbox → Kafka → attendance sync.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

  private static final String ACTOR = "seed";

  private final DepartmentRepository departmentRepository;
  private final EmployeeRepository employeeRepository;
  private final DepartmentService departmentService;
  private final EmployeeService employeeService;

  @Value("${hrms.seed.enabled:true}")
  private boolean enabled;

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      log.info("Seed dữ liệu bị tắt (hrms.seed.enabled=false)");
      return;
    }

    // ---- Cấp 1: phòng IT ----
    Department it = ensureDepartment("IT", "Phòng Công nghệ thông tin", null);
    Employee director = ensureEmployee(new EmployeeCreateRequest(
        "NVIT001", "Trần Đình Khoa", "khoa.td@hrms.vn", it.getId(),
        "Giám đốc Công nghệ thông tin", null, Instant.parse("2020-01-15T00:00:00Z"),
        LocalDate.of(1988, 3, 12), "MALE", "0912000001", "001088012345",
        "Hà Nội"));
    ensureDepartmentManager(it, director);

    // ---- Cấp 2: bộ phận (cha = phòng IT) ----
    Department be = ensureDepartment("IT-BE", "Bộ phận Phát triển Backend", it.getId());
    Department fe = ensureDepartment("IT-FE", "Bộ phận Phát triển Frontend", it.getId());
    Department ops = ensureDepartment("IT-OPS", "Bộ phận Vận hành & Hạ tầng", it.getId());

    Employee beLead = ensureEmployee(new EmployeeCreateRequest(
        "NVIT002", "Lê Thu Hà", "ha.lt@hrms.vn", be.getId(),
        "Trưởng phòng Phát triển Backend", director.getId(),
        Instant.parse("2020-06-01T00:00:00Z"),
        LocalDate.of(1992, 7, 22), "FEMALE", "0912000002", "001092054321", "Hà Nội"));
    Employee feLead = ensureEmployee(new EmployeeCreateRequest(
        "NVIT003", "Phạm Minh Tuấn", "tuan.pm@hrms.vn", fe.getId(),
        "Trưởng phòng Phát triển Frontend", director.getId(),
        Instant.parse("2021-02-01T00:00:00Z"),
        LocalDate.of(1993, 11, 5), "MALE", "0912000003", "001093098765", "Hà Nội"));
    Employee opsLead = ensureEmployee(new EmployeeCreateRequest(
        "NVIT004", "Vũ Ngọc Sơn", "son.vn@hrms.vn", ops.getId(),
        "Trưởng phòng Vận hành & Hạ tầng", director.getId(),
        Instant.parse("2021-09-01T00:00:00Z"),
        LocalDate.of(1990, 1, 30), "MALE", "0912000004", "001090011223", "Đà Nẵng"));

    ensureDepartmentManager(be, beLead);
    ensureDepartmentManager(fe, feLead);
    ensureDepartmentManager(ops, opsLead);

    // ---- Cấp 3: thành viên (manager = trưởng bộ phận) ----
    ensureEmployee(new EmployeeCreateRequest(
        "NVIT005", "Nguyễn Văn Bình", "binh.nv@hrms.vn", be.getId(),
        "Lập trình viên Backend", beLead.getId(), Instant.parse("2022-03-01T00:00:00Z"),
        LocalDate.of(1997, 4, 18), "MALE", "0912000005", "001097044556", "Hà Nội"));
    ensureEmployee(new EmployeeCreateRequest(
        "NVIT006", "Đỗ Thanh Mai", "mai.dt@hrms.vn", be.getId(),
        "Lập trình viên Backend", beLead.getId(), Instant.parse("2023-01-09T00:00:00Z"),
        LocalDate.of(1999, 9, 9), "FEMALE", "0912000006", "001099066778", "Hải Phòng"));
    ensureEmployee(new EmployeeCreateRequest(
        "NVIT007", "Hoàng Gia Huy", "huy.hg@hrms.vn", fe.getId(),
        "Lập trình viên Frontend", feLead.getId(), Instant.parse("2023-05-15T00:00:00Z"),
        LocalDate.of(1998, 12, 1), "MALE", "0912000007", "001098077889", "Huế"));
    ensureEmployee(new EmployeeCreateRequest(
        "NVIT008", "Bùi Thị Lan", "lan.bt@hrms.vn", ops.getId(),
        "Kỹ sư Hạ tầng", opsLead.getId(), Instant.parse("2022-08-01T00:00:00Z"),
        LocalDate.of(1996, 2, 14), "FEMALE", "0912000008", "001096088990", "HCM"));

    log.info("Seed phòng IT hoàn tất — 4 phòng ban, 8 nhân viên (idempotent, bỏ qua nếu đã có)");
  }

  private Department ensureDepartment(String code, String name, UUID parentId) {
    Department existing = departmentRepository.findByCode(code).orElse(null);
    if (existing != null) {
      return existing;
    }
    var created = departmentService.create(new DepartmentCreateRequest(code, name, parentId, null));
    return departmentRepository.findById(created.id()).orElseThrow();
  }

  private Employee ensureEmployee(EmployeeCreateRequest request) {
    Employee existing = employeeRepository.findByEmployeeCode(request.employeeCode()).orElse(null);
    if (existing != null) {
      return existing;
    }
    var created = employeeService.create(request, ACTOR);
    return employeeRepository.findById(created.id()).orElseThrow();
  }

  /** PATCH của department set parentId theo giá trị gửi (null = gỡ) — phải kèm parentId hiện tại. */
  private void ensureDepartmentManager(Department department, Employee manager) {
    if (manager.getId().equals(department.getManagerId())) {
      return;
    }
    departmentService.update(department.getId(),
        new DepartmentUpdateRequest(null, null, department.getParentId(), manager.getId(), null));
  }
}
