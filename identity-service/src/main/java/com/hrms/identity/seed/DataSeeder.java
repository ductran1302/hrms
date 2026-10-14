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
import com.hrms.identity.employee.dto.EmployeeUpdateRequest;
import com.hrms.identity.jobtitle.JobTitle;
import com.hrms.identity.jobtitle.JobTitleRepository;
import com.hrms.identity.holiday.HolidayCalendar;
import com.hrms.identity.holiday.HolidayCalendarRepository;
import com.hrms.identity.holiday.HolidayCalendarStatus;
import com.hrms.identity.holiday.HolidayService;
import com.hrms.identity.holiday.dto.HolidayCreateRequest;
import com.hrms.identity.keycloak.KeycloakAdminClient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seed dữ liệu: cây tổ chức (Tập đoàn → Khối → Ban → Phòng theo cấu trúc Big Tech),
 * danh mục chức vụ, và phòng IT mẫu — cấu trúc cây đủ 3 cấp để màn "Sơ đồ tổ chức"
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
  private final JobTitleRepository jobTitleRepository;
  private final DepartmentService departmentService;
  private final EmployeeService employeeService;
  private final KeycloakAdminClient keycloakAdminClient;
  private final HolidayCalendarRepository holidayCalendarRepository;
  private final HolidayService holidayService;

  @Value("${hrms.seed.enabled:true}")
  private boolean enabled;

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      log.info("Seed dữ liệu bị tắt (hrms.seed.enabled=false)");
      return;
    }
    // Mỗi nhóm seed try/catch riêng — lỗi một nhóm không chặn nhóm còn lại;
    // idempotent nên lần boot sau tự thử lại (Keycloak chưa sẵn sàng lúc khởi động).
    runSeed("cây tổ chức", this::seedOrgDepartments);
    runSeed("danh mục chức vụ", this::seedJobTitles);
    runSeed("phòng IT", this::seedItDepartment);
    runSeed("cây nhân sự mẫu", this::seedOrgEmployees);
    runSeed("lịch nghỉ lễ", this::seedHolidayCalendars);
  }

  private void runSeed(String what, Runnable seed) {
    try {
      seed.run();
    } catch (Exception ex) {
      log.error("Seed {} thất bại, sẽ thử lại ở lần khởi động sau: {}", what, ex.getMessage());
    }
  }

  /** Cây phòng ban: Tập đoàn → HĐQT/GĐ + 3 khối → ban → phòng (mã code block tổ chức). */
  private void seedOrgDepartments() {
    List<DeptSpec> specs = List.of(
        new DeptSpec("ORG", "Tập đoàn Công nghệ", null),
        new DeptSpec("BOARD", "Hội đồng Quản trị", "ORG"),
        new DeptSpec("EXCO", "Ban Giám đốc Điều hành", "ORG"),
        new DeptSpec("ENG", "Khối Kỹ thuật & Công nghệ", "ORG"),
        new DeptSpec("ENG-DEV", "Ban Phát triển Phần mềm", "ENG"),
        new DeptSpec("ENG-DEV-BE", "Phòng Backend", "ENG-DEV"),
        new DeptSpec("ENG-DEV-FE", "Phòng Frontend", "ENG-DEV"),
        new DeptSpec("ENG-DEV-MOB", "Phòng Mobile App", "ENG-DEV"),
        new DeptSpec("ENG-INF", "Ban Hạ tầng & Cloud", "ENG"),
        new DeptSpec("ENG-INF-SRE", "Phòng DevOps & SRE", "ENG-INF"),
        new DeptSpec("ENG-INF-DAT", "Phòng Dữ liệu Big Data", "ENG-INF"),
        new DeptSpec("ENG-SEC", "Ban Bảo mật", "ENG"),
        new DeptSpec("ENG-SEC-OPS", "Phòng Security Ops", "ENG-SEC"),
        new DeptSpec("ENG-QA", "Ban Đảm bảo Chất lượng", "ENG"),
        new DeptSpec("ENG-QA-AUT", "Phòng QA Automation", "ENG-QA"),
        new DeptSpec("PRD", "Khối Sản phẩm & Thiết kế", "ORG"),
        new DeptSpec("PRD-PMO", "Ban Quản lý Sản phẩm", "PRD"),
        new DeptSpec("PRD-PMO-PM", "Phòng Quản lý Sản phẩm", "PRD-PMO"),
        new DeptSpec("PRD-DES", "Ban Thiết kế UX/UI", "PRD"),
        new DeptSpec("PRD-DES-UX", "Phòng Thiết kế Trải nghiệm", "PRD-DES"),
        new DeptSpec("OPS", "Khối Vận hành & Quản trị", "ORG"),
        new DeptSpec("HRM", "Ban Nhân sự", "OPS"),
        new DeptSpec("FIN", "Ban Tài chính Kế toán", "OPS"),
        new DeptSpec("ITN", "Ban CNTT Nội bộ", "OPS"));

    Map<String, UUID> ids = new HashMap<>();
    for (DeptSpec spec : specs) {
      UUID parentId = spec.parentCode() == null ? null : ids.get(spec.parentCode());
      ids.put(spec.code(), ensureDepartment(spec.code(), spec.name(), parentId).getId());
    }
    log.info("Seed cây tổ chức hoàn tất — {} phòng/ban/khối (idempotent, bỏ qua nếu đã có)",
        specs.size());
  }

  /** Danh mục chức vụ (job_titles) — idempotent theo code. */
  private void seedJobTitles() {
    List<TitleSpec> specs = List.of(
        // — Hội đồng Quản trị —
        new TitleSpec("BOARD_CHAIR", "Chủ tịch Hội đồng Quản trị",
            "Chairman of the Board", "BOARD", 10),
        new TitleSpec("BOARD_MEMBER", "Thành viên Hội đồng Quản trị",
            "Board Member", "BOARD", 20),
        // — Ban Giám đốc (C-Suite) —
        new TitleSpec("CEO", "Tổng Giám đốc Điều hành",
            "Chief Executive Officer", "EXEC", 30),
        new TitleSpec("COO", "Giám đốc Vận hành",
            "Chief Operating Officer", "EXEC", 40),
        new TitleSpec("CFO", "Giám đốc Tài chính",
            "Chief Financial Officer", "EXEC", 50),
        new TitleSpec("CHRO", "Giám đốc Nhân sự",
            "Chief Human Resources Officer", "EXEC", 60),
        new TitleSpec("CIO", "Giám đốc CNTT Nội bộ",
            "Chief Information Officer", "EXEC", 70),
        new TitleSpec("CPO", "Giám đốc Sản phẩm",
            "Chief Product Officer", "EXEC", 80),
        new TitleSpec("CTO", "Giám đốc Công nghệ",
            "Chief Technology Officer", "EXEC", 90),
        new TitleSpec("CISO", "Giám đốc Bảo mật",
            "Chief Information Security Officer", "EXEC", 100),
        new TitleSpec("CHIEF_SCIENTIST", "Khoa học trưởng / Trưởng phòng Nghiên cứu & Phát triển",
            "Chief Scientist / Head of R&D", "EXEC", 110),
        // — Khối Sản phẩm —
        new TitleSpec("HEAD_PRODUCT", "Giám đốc Sản phẩm",
            "Head of Product / Product Director", "PRODUCT", 120),
        new TitleSpec("GPM", "Trưởng nhóm Quản lý Sản phẩm",
            "Group Product Manager", "PRODUCT", 130),
        new TitleSpec("SENIOR_PM", "Quản lý Sản phẩm Cao cấp",
            "Senior Product Manager", "PRODUCT", 140),
        new TitleSpec("PM", "Quản lý Sản phẩm",
            "Product Manager", "PRODUCT", 150),
        new TitleSpec("APM", "Chuyên viên Quản lý Sản phẩm",
            "Associate Product Manager", "PRODUCT", 160),
        new TitleSpec("BA", "Chuyên viên Phân tích Nghiệp vụ / Hệ thống",
            "Business / System Analyst", "PRODUCT", 170),
        // — Thiết kế —
        new TitleSpec("HEAD_DESIGN", "Giám đốc Thiết kế",
            "Head of Design / UX-UI Director", "DESIGN", 180),
        new TitleSpec("UX_RESEARCHER", "Chuyên viên Nghiên cứu Trải nghiệm Người dùng",
            "UX Researcher", "DESIGN", 190),
        new TitleSpec("UIUX_DESIGNER", "Kỹ sư Thiết kế Giao diện & Trải nghiệm",
            "UI/UX Designer", "DESIGN", 200),
        // — Bảo mật —
        new TitleSpec("SECURITY_ENGINEER", "Kỹ sư Bảo mật & An ninh mạng",
            "Security Engineer / SecOps", "SECURITY", 210),
        // — Nghiên cứu —
        new TitleSpec("AI_RESEARCH_SCIENTIST", "Nhà nghiên cứu Trí tuệ Nhân tạo / Học máy",
            "AI/ML Research Scientist", "RND", 220),
        // — Kỹ thuật: nhánh quản lý —
        new TitleSpec("VP_ENGINEERING", "Phó Tổng Giám đốc Kỹ thuật",
            "VP of Engineering", "ENG", 230),
        new TitleSpec("ENG_DIRECTOR", "Giám đốc Khối Kỹ thuật",
            "Engineering Director", "ENG", 240),
        new TitleSpec("ENG_MANAGER", "Quản lý Kỹ thuật",
            "Engineering Manager", "ENG", 250),
        new TitleSpec("ASSOCIATE_EM", "Trợ lý Quản lý Kỹ thuật",
            "Associate Engineering Manager", "ENG", 260),
        // — Kỹ thuật: chuyên gia (Staff & Principal) —
        new TitleSpec("TECHNICAL_FELLOW", "Chuyên gia Cốt lõi",
            "Technical Fellow / Distinguished Engineer", "EXPERT", 270),
        new TitleSpec("PRINCIPAL_ENGINEER", "Kiến trúc sư / Chuyên gia Cấp cao",
            "Principal Engineer", "EXPERT", 280),
        new TitleSpec("SENIOR_STAFF_ENGINEER", "Kỹ sư Trưởng Cấp cao",
            "Senior Staff Engineer", "EXPERT", 290),
        new TitleSpec("STAFF_ENGINEER", "Kỹ sư Trưởng / Kiến trúc sư Giải pháp",
            "Staff Engineer / Solution Architect", "EXPERT", 300),
        // — Kỹ thuật: phát triển phần mềm —
        new TitleSpec("TECH_LEAD", "Trưởng nhóm Kỹ thuật / Kiến trúc sư Phần mềm",
            "Tech Lead / Software Architect", "SOFTWARE", 310),
        new TitleSpec("SENIOR_SWE", "Lập trình viên Cao cấp",
            "Senior Software Engineer", "SOFTWARE", 320),
        new TitleSpec("MID_SWE", "Lập trình viên Trung cấp",
            "Mid-level Software Engineer", "SOFTWARE", 330),
        new TitleSpec("JUNIOR_SWE", "Lập trình viên Sơ cấp",
            "Junior / Fresher Software Engineer", "SOFTWARE", 340),
        // — Kỹ thuật: hạ tầng —
        new TitleSpec("CLOUD_ENGINEER", "Kỹ sư Hạ tầng Đám mây",
            "Cloud / Infrastructure Engineer", "INFRA", 350),
        new TitleSpec("SRE_DEVOPS", "Kỹ sư Độ tin cậy Hệ thống & Vận hành",
            "SRE / DevOps Engineer", "INFRA", 360),
        // — Kỹ thuật: dữ liệu —
        new TitleSpec("DATA_ARCHITECT", "Kiến trúc sư Dữ liệu",
            "Data Architect", "DATA", 370),
        new TitleSpec("DATA_ENGINEER", "Kỹ sư Dữ liệu lớn",
            "Data Engineer", "DATA", 380),
        new TitleSpec("DATA_SCIENTIST", "Nhà khoa học / Chuyên viên Phân tích Dữ liệu",
            "Data Scientist / Data Analyst", "DATA", 390),
        // — Kiểm thử —
        new TitleSpec("QA_LEAD", "Trưởng nhóm Đảm bảo Chất lượng",
            "QA Lead", "QA", 400),
        new TitleSpec("QA_ENGINEER", "Kỹ sư Kiểm thử Tự động / Chất lượng",
            "Automation QA / QC Engineer", "QA", 410));

    int inserted = 0;
    for (TitleSpec spec : specs) {
      if (jobTitleRepository.findByCode(spec.code()).isPresent()) {
        continue;
      }
      JobTitle title = new JobTitle();
      title.setCode(spec.code());
      title.setName(spec.name());
      title.setNameEn(spec.nameEn());
      title.setCategory(spec.category());
      title.setSortOrder(spec.sortOrder());
      jobTitleRepository.save(title);
      inserted++;
    }
    log.info("Seed danh mục chức vụ hoàn tất — {} dòng ({} mới, idempotent)",
        specs.size(), inserted);
  }

  /**
   * Cây nhân sự mẫu cho các phòng đã seed: HĐQT → CEO → C-Suite → khối → ban → phòng.
   * Idempotent theo employeeCode; cuối cùng gắn GĐ CNTT cũ (NVIT001) dưới CIO để
   * sơ đồ tổ chức gộp IT thành một root duy nhất.
   */
  private void seedOrgEmployees() {
    List<EmpSpec> specs = List.of(
        // — Hội đồng Quản trị —
        new EmpSpec("nhminh", "Nguyễn Hoàng Minh", "minh.nh@hrms.vn",
            "Chủ tịch Hội đồng Quản trị", "BOARD", null,
            "2015-01-05T00:00:00Z", "1968-04-12", "MALE"),
        new EmpSpec("tglinh", "Trần Gia Linh", "linh.tg@hrms.vn",
            "Thành viên Hội đồng Quản trị", "BOARD", "nhminh",
            "2016-03-01T00:00:00Z", "1972-09-03", "FEMALE"),
        // — Ban Giám đốc Điều hành —
        new EmpSpec("lmquan", "Lê Minh Quân", "quan.lm@hrms.vn",
            "Tổng Giám đốc Điều hành", "EXCO", "nhminh",
            "2017-06-15T00:00:00Z", "1975-11-20", "MALE"),
        new EmpSpec("pttrang", "Phạm Thu Trang", "trang.pt@hrms.vn",
            "Giám đốc Vận hành", "EXCO", "lmquan",
            "2018-02-01T00:00:00Z", "1980-06-08", "FEMALE"),
        new EmpSpec("datuan", "Đỗ Anh Tuấn", "tuan.da@hrms.vn",
            "Giám đốc Tài chính", "FIN", "lmquan",
            "2018-05-01T00:00:00Z", "1979-01-25", "MALE"),
        new EmpSpec("vthuong", "Vũ Thanh Hương", "huong.vt@hrms.vn",
            "Giám đốc Nhân sự", "HRM", "lmquan",
            "2018-08-01T00:00:00Z", "1981-08-14", "FEMALE"),
        new EmpSpec("nqbao", "Ngô Quốc Bảo", "bao.nq@hrms.vn",
            "Giám đốc CNTT Nội bộ", "ITN", "lmquan",
            "2019-01-01T00:00:00Z", "1982-02-17", "MALE"),
        new EmpSpec("bkngan", "Bùi Kim Ngân", "ngan.bk@hrms.vn",
            "Giám đốc Sản phẩm", "EXCO", "lmquan",
            "2019-04-01T00:00:00Z", "1983-12-05", "FEMALE"),
        new EmpSpec("hdtri", "Hoàng Đức Trí", "tri.hd@hrms.vn",
            "Giám đốc Công nghệ", "EXCO", "lmquan",
            "2017-09-01T00:00:00Z", "1980-03-30", "MALE"),
        // — Khối Sản phẩm & Thiết kế —
        new EmpSpec("dmchau", "Đặng Minh Châu", "chau.dm@hrms.vn",
            "Giám đốc Sản phẩm", "PRD-PMO", "bkngan",
            "2019-07-01T00:00:00Z", "1984-07-19", "FEMALE"),
        new EmpSpec("lqduy", "Lý Quốc Duy", "duy.lq@hrms.vn",
            "Trưởng nhóm Quản lý Sản phẩm", "PRD-PMO", "dmchau",
            "2020-02-01T00:00:00Z", "1987-05-11", "MALE"),
        new EmpSpec("tnbich", "Trịnh Ngọc Bích", "bich.tn@hrms.vn",
            "Quản lý Sản phẩm", "PRD-PMO-PM", "lqduy",
            "2021-06-01T00:00:00Z", "1990-10-02", "FEMALE"),
        new EmpSpec("pnvy", "Phan Ngọc Vy", "vy.pn@hrms.vn",
            "Chuyên viên Quản lý Sản phẩm", "PRD-PMO-PM", "lqduy",
            "2023-08-01T00:00:00Z", "1995-03-27", "FEMALE"),
        new EmpSpec("ntvy", "Nguyễn Thảo Vy", "vy.nt@hrms.vn",
            "Chuyên viên Phân tích Nghiệp vụ / Hệ thống", "PRD-PMO-PM", "lqduy",
            "2022-01-10T00:00:00Z", "1993-09-15", "FEMALE"),
        new EmpSpec("mhnam", "Mai Hoàng Nam", "nam.mh@hrms.vn",
            "Giám đốc Thiết kế", "PRD-DES", "dmchau",
            "2020-05-01T00:00:00Z", "1985-01-08", "MALE"),
        new EmpSpec("dhmi", "Đào Hà Mi", "mi.dh@hrms.vn",
            "Chuyên viên Nghiên cứu Trải nghiệm Người dùng", "PRD-DES", "mhnam",
            "2022-03-01T00:00:00Z", "1994-04-22", "FEMALE"),
        new EmpSpec("vmlinh", "Vương Mỹ Linh", "linh.vm@hrms.vn",
            "Kỹ sư Thiết kế Giao diện & Trải nghiệm", "PRD-DES", "mhnam",
            "2022-09-01T00:00:00Z", "1996-08-30", "FEMALE"),
        // — Khối Kỹ thuật —
        new EmpSpec("lvphuc", "Lâm Văn Phúc", "phuc.lv@hrms.vn",
            "Phó Tổng Giám đốc Kỹ thuật", "ENG", "hdtri",
            "2018-01-01T00:00:00Z", "1978-10-09", "MALE"),
        new EmpSpec("nhdang", "Nguyễn Hải Đăng", "dang.nh@hrms.vn",
            "Giám đốc Khối Kỹ thuật", "ENG", "lvphuc",
            "2019-03-01T00:00:00Z", "1982-07-13", "MALE"),
        new EmpSpec("tvdung", "Trần Việt Dũng", "dung.tv@hrms.vn",
            "Quản lý Kỹ thuật", "ENG-DEV", "nhdang",
            "2020-01-01T00:00:00Z", "1986-12-01", "MALE"),
        new EmpSpec("dqhuy", "Đỗ Quang Huy", "huy.dq@hrms.vn",
            "Trưởng nhóm Kỹ thuật / Kiến trúc sư Phần mềm", "ENG-DEV-BE", "tvdung",
            "2021-02-01T00:00:00Z", "1991-06-18", "MALE"),
        new EmpSpec("ntyen", "Nguyễn Thị Yến", "yen.nt@hrms.vn",
            "Lập trình viên Cao cấp", "ENG-DEV-BE", "dqhuy",
            "2021-07-01T00:00:00Z", "1994-02-09", "FEMALE"),
        new EmpSpec("phlinh", "Phạm Hà Linh", "linh.ph@hrms.vn",
            "Lập trình viên Trung cấp", "ENG-DEV-BE", "dqhuy",
            "2023-09-01T00:00:00Z", "1998-11-23", "FEMALE"),
        new EmpSpec("hakhoa", "Hoàng Anh Khoa", "khoa.ha@hrms.vn",
            "Trưởng nhóm Kỹ thuật / Kiến trúc sư Phần mềm", "ENG-DEV-FE", "tvdung",
            "2021-04-01T00:00:00Z", "1990-05-16", "MALE"),
        new EmpSpec("vnanh", "Vũ Ngọc Anh", "anh.vn@hrms.vn",
            "Lập trình viên Cao cấp", "ENG-DEV-FE", "hakhoa",
            "2022-06-01T00:00:00Z", "1995-01-29", "FEMALE"),
        new EmpSpec("cdphat", "Cao Đình Phát", "phat.cd@hrms.vn",
            "Trưởng nhóm Kỹ thuật / Kiến trúc sư Phần mềm", "ENG-DEV-MOB", "tvdung",
            "2022-02-01T00:00:00Z", "1992-09-07", "MALE"),
        new EmpSpec("nhphuc", "Nguyễn Hoàng Phúc", "phuc.nh@hrms.vn",
            "Quản lý Kỹ thuật", "ENG-INF", "nhdang",
            "2020-04-01T00:00:00Z", "1987-03-14", "MALE"),
        new EmpSpec("ttvinh", "Trương Thế Vinh", "vinh.tt@hrms.vn",
            "SRE / DevOps Engineer", "ENG-INF-SRE", "nhphuc",
            "2021-11-01T00:00:00Z", "1993-07-21", "MALE"),
        new EmpSpec("dhlong", "Đinh Hải Long", "long.dh@hrms.vn",
            "Kỹ sư Dữ liệu lớn", "ENG-INF-DAT", "nhphuc",
            "2022-04-01T00:00:00Z", "1994-12-12", "MALE"),
        new EmpSpec("bnha", "Bùi Ngọc Hà", "ha.bn@hrms.vn",
            "Nhà khoa học / Chuyên viên Phân tích Dữ liệu", "ENG-INF-DAT", "dhlong",
            "2023-02-01T00:00:00Z", "1996-06-04", "FEMALE"),
        new EmpSpec("tthai", "Tống Thanh Hải", "hai.tt@hrms.vn",
            "Giám đốc Bảo mật", "ENG-SEC", "hdtri",
            "2019-05-01T00:00:00Z", "1981-09-28", "MALE"),
        new EmpSpec("ntky", "Nguyễn Trọng Kỳ", "ky.nt@hrms.vn",
            "Kỹ sư Bảo mật & An ninh mạng", "ENG-SEC-OPS", "tthai",
            "2022-07-01T00:00:00Z", "1995-05-19", "MALE"),
        new EmpSpec("dtbang", "Đinh Thị Băng", "bang.dt@hrms.vn",
            "Trưởng nhóm Đảm bảo Chất lượng", "ENG-QA", "nhdang",
            "2020-08-01T00:00:00Z", "1988-02-11", "FEMALE"),
        new EmpSpec("vnnam", "Vũ Nhật Nam", "nam.vn@hrms.vn",
            "Kỹ sư Kiểm thử Tự động / Chất lượng", "ENG-QA-AUT", "dtbang",
            "2023-01-01T00:00:00Z", "1997-10-25", "MALE"));

    String[] addresses = {"Hà Nội", "Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ", "Huế"};
    Map<String, UUID> ids = new HashMap<>();
    int i = 0;
    for (EmpSpec s : specs) {
      i++;
      Department dept = departmentRepository.findByCode(s.deptCode())
          .orElseThrow(() -> new IllegalStateException("Chưa seed phòng ban: " + s.deptCode()));
      UUID managerId = s.managerCode() == null ? null : ids.get(s.managerCode());
      Employee emp = ensureEmployee(new EmployeeCreateRequest(
          s.code(), s.fullName(), s.email(), dept.getId(), s.position(), managerId,
          Instant.parse(s.hiredAt()), LocalDate.parse(s.birthDate()), s.gender(),
          String.format("0912%07d", i), String.format("001%09d", i),
          addresses[i % addresses.length]));
      ids.put(s.code(), emp.getId());
    }
    attachItDirectorUnderCio(ids.get("nqbao"));
    log.info("Seed cây nhân sự mẫu hoàn tất — {} người (idempotent, bỏ qua nếu đã có)",
        specs.size());
  }

  /** Gắn GĐ CNTT cũ (NVIT001) dưới CIO — gộp cây IT vào một root duy nhất. */
  private void attachItDirectorUnderCio(UUID cioId) {
    if (cioId == null) {
      return;
    }
    Employee itDirector = employeeRepository.findByEmployeeCode("NVIT001").orElse(null);
    if (itDirector == null || cioId.equals(itDirector.getManagerId())) {
      return;
    }
    employeeService.update(itDirector.getId(), new EmployeeUpdateRequest(
        null, null, null, null, null, cioId, null, null, null, null, null, null, null,
        null, null, null, null, null), ACTOR);
  }

  private void seedItDepartment() {
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
      ensureKeycloakUser(existing);
      return existing;
    }
    var created = employeeService.create(request, ACTOR);
    return employeeRepository.findById(created.id()).orElseThrow();
  }

  /** Backfill: NV cũ (trước khi có auto-provision) chưa có tài khoản đăng nhập. */
  private void ensureKeycloakUser(Employee employee) {
    try {
      if (!keycloakAdminClient.userExists(employee.getEmployeeCode())) {
        keycloakAdminClient.createUser(employee.getEmployeeCode(), employee.getFullName(),
            employee.getEmail());
        log.info("Backfill tài khoản Keycloak cho {}", employee.getEmployeeCode());
      }
    } catch (Exception ex) {
      log.warn("Chưa tạo được tài khoản Keycloak cho {}: {} (sẽ thử lại lần boot sau)",
          employee.getEmployeeCode(), ex.getMessage());
    }
  }

  /**
   * Lịch nghỉ lễ mẫu: VN (lịch mặc định — nhân viên chưa gán calendar dùng code VN)
   * + ONSITE (company-wide, gán cho NV onsite). Ngày lễ VN 2026 seed thô — HR chỉnh sau.
   */
  private void seedHolidayCalendars() {
    HolidayCalendar vn = ensureCalendar("VN", "Lịch nghỉ lễ Việt Nam", "VN",
        "Ngày lễ quốc gia theo luật lao động Việt Nam");
    HolidayCalendar onsite = ensureCalendar("ONSITE", "Lịch nghỉ lễ công ty (ONSITE)", "VN",
        "Ngày lễ riêng của công ty — áp dụng cho nhân sự onsite");
    ensureHoliday(vn, new HolidayCreateRequest("Tết Dương lịch",
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), "NATIONAL", false, null));
    ensureHoliday(vn, new HolidayCreateRequest("Tết Nguyên đán",
        LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 18), "NATIONAL", false,
        "Nghỉ chính thức 3 ngày (chưa kể ngày bù)"));
    ensureHoliday(vn, new HolidayCreateRequest("Giỗ Tổ Hùng Vương",
        LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 13), "NATIONAL", false, null));
    ensureHoliday(vn, new HolidayCreateRequest("Giải phóng miền Nam / Quốc tế lao động",
        LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 1), "NATIONAL", false, null));
    ensureHoliday(vn, new HolidayCreateRequest("Quốc khánh Việt Nam",
        LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 2), "NATIONAL", false,
        "2/9/2026 thứ tư — nghỉ bù 1/9 hoặc 3/9 do HR quyết định"));
    ensureHoliday(onsite, new HolidayCreateRequest("Ngày thành lập công ty",
        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), "COMPANY", false, null));
    ensureHoliday(onsite, new HolidayCreateRequest("Team building cuối năm",
        LocalDate.of(2026, 12, 18), LocalDate.of(2026, 12, 18), "COMPANY", true,
        "Chiều — half-day"));
  }

  private HolidayCalendar ensureCalendar(String code, String name, String country,
      String description) {
    return holidayCalendarRepository.findByCode(code).orElseGet(() -> {
      HolidayCalendar calendar = new HolidayCalendar();
      calendar.setCode(code);
      calendar.setName(name);
      calendar.setCountry(country);
      calendar.setDescription(description);
      calendar.setStatus(HolidayCalendarStatus.ACTIVE);
      HolidayCalendar saved = holidayCalendarRepository.save(calendar);
      log.info("Seed lịch nghỉ lễ: {}", code);
      return saved;
    });
  }

  /** Tạo qua service (không insert thẳng) để outbox → Kafka → attendance sync. */
  private void ensureHoliday(HolidayCalendar calendar, HolidayCreateRequest request) {
    try {
      holidayService.create(calendar.getId(), request, ACTOR);
      log.info("Seed ngày lễ: {} ({})", request.name(), request.dateFrom());
    } catch (com.hrms.common.exception.ConflictException ex) {
      // đã seed rồi — idempotent
    }
  }

  /** PATCH của department set parentId theo giá trị gửi (null = gỡ) — phải kèm parentId hiện tại. */
  private void ensureDepartmentManager(Department department, Employee manager) {
    if (manager.getId().equals(department.getManagerId())) {
      return;
    }
    departmentService.update(department.getId(),
        new DepartmentUpdateRequest(null, null, department.getParentId(), manager.getId(), null));
  }

  /** Một nút phòng ban trong cây seed — parent chỉ định theo code (đã seed trước đó). */
  private record DeptSpec(String code, String name, String parentCode) {}

  /** Một dòng danh mục chức vụ. */
  private record TitleSpec(
      String code, String name, String nameEn, String category, int sortOrder) {}

  /** Một người trong cây nhân sự mẫu — managerCode trỏ tới code của người đã seed trước. */
  private record EmpSpec(
      String code, String fullName, String email, String position,
      String deptCode, String managerCode, String hiredAt, String birthDate, String gender) {}
}
