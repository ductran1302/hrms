package com.hrms.attendance.schedule;

import com.hrms.attendance.schedule.dto.WorkSchedulePolicyRequest;
import com.hrms.attendance.schedule.dto.WorkSchedulePolicyResponse;
import com.hrms.attendance.shift.WorkShift;
import com.hrms.attendance.shift.WorkShiftRepository;
import com.hrms.common.audit.AuditService;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý policy lịch tuần (chỉ ADMIN ghi). */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkSchedulePolicyService {

  private final WorkSchedulePolicyRepository policyRepository;
  private final EmployeeScheduleAssignmentRepository assignmentRepository;
  private final EmployeeShiftRosterRepository rosterRepository;
  private final WorkShiftRepository workShiftRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<WorkSchedulePolicyResponse> list(Boolean active) {
    Map<UUID, String> shiftNames = shiftNames();
    return policyRepository.findAllOrdered().stream()
        .filter(p -> active == null || p.isActive() == active)
        .map(p -> WorkSchedulePolicyResponse.from(p, shiftNames))
        .toList();
  }

  @Transactional(readOnly = true)
  public WorkSchedulePolicyResponse getDefault() {
    return policyRepository.findFirstByDefaultPolicyTrue()
        .map(p -> WorkSchedulePolicyResponse.from(p, shiftNames()))
        .orElseThrow(() -> new ResourceNotFoundException("Policy lịch mặc định"));
  }

  @Transactional(readOnly = true)
  public WorkSchedulePolicyResponse get(UUID id) {
    return WorkSchedulePolicyResponse.from(find(id), shiftNames());
  }

  @Transactional
  public WorkSchedulePolicyResponse create(WorkSchedulePolicyRequest request, String actor) {
    String name = request.name().trim();
    if (policyRepository.existsByName(name)) {
      throw new ConflictException("Tên policy lịch đã tồn tại: " + name);
    }
    WorkSchedulePolicy policy = new WorkSchedulePolicy();
    apply(policy, request, name);
    validate(policy);
    if (policy.isDefaultPolicy()) {
      clearOtherDefaults(null);
    }
    policyRepository.saveAndFlush(policy);
    auditService.success("SCHEDULE_POLICY_CREATE", "WorkSchedulePolicy", policy.getId(),
        "name=" + name + " default=" + policy.isDefaultPolicy() + " by=" + actor);
    log.info("Tạo policy lịch '{}' (default={})", name, policy.isDefaultPolicy());
    return WorkSchedulePolicyResponse.from(policy, shiftNames());
  }

  @Transactional
  public WorkSchedulePolicyResponse update(UUID id, WorkSchedulePolicyRequest request, String actor) {
    WorkSchedulePolicy policy = find(id);
    String name = request.name().trim();
    if (!name.equals(policy.getName()) && policyRepository.existsByNameAndIdNot(name, id)) {
      throw new ConflictException("Tên policy lịch đã tồn tại: " + name);
    }
    apply(policy, request, name);
    validate(policy);
    if (policy.isDefaultPolicy()) {
      clearOtherDefaults(id);
    }
    policyRepository.saveAndFlush(policy);
    auditService.success("SCHEDULE_POLICY_UPDATE", "WorkSchedulePolicy", id,
        "name=" + name + " default=" + policy.isDefaultPolicy() + " by=" + actor);
    log.info("Cập nhật policy lịch '{}' (default={})", name, policy.isDefaultPolicy());
    return WorkSchedulePolicyResponse.from(policy, shiftNames());
  }

  @Transactional
  public void delete(UUID id, String actor) {
    WorkSchedulePolicy policy = find(id);
    if (policy.isDefaultPolicy()) {
      throw new BusinessRuleException(
          "Không thể xoá policy mặc định — đặt policy khác làm mặc định trước");
    }
    if (assignmentRepository.existsByPolicyId(id) || rosterRepository.existsByPolicyId(id)) {
      throw new BusinessRuleException(
          "Policy '" + policy.getName() + "' còn bản gán/roster tham chiếu — đặt active=false thay vì xoá");
    }
    policyRepository.delete(policy);
    auditService.success("SCHEDULE_POLICY_DELETE", "WorkSchedulePolicy", id,
        "name=" + policy.getName() + " by=" + actor);
    log.info("Xoá policy lịch '{}'", policy.getName());
  }

  private void apply(WorkSchedulePolicy policy, WorkSchedulePolicyRequest request, String name) {
    policy.setName(name);
    policy.setDescription(request.description());
    policy.setMonShiftId(request.monShiftId());
    policy.setTueShiftId(request.tueShiftId());
    policy.setWedShiftId(request.wedShiftId());
    policy.setThuShiftId(request.thuShiftId());
    policy.setFriShiftId(request.friShiftId());
    policy.setSatShiftId(request.satShiftId());
    policy.setSatAlternateShiftId(request.satAlternateShiftId());
    policy.setSunShiftId(request.sunShiftId());
    policy.setRotationAnchorDate(request.rotationAnchorDate());
    policy.setDefaultPolicy(Boolean.TRUE.equals(request.defaultPolicy()));
    policy.setActive(request.active() == null || request.active());
  }

  private void clearOtherDefaults(UUID keepId) {
    policyRepository.findFirstByDefaultPolicyTrue().ifPresent(current -> {
      if (keepId == null || !current.getId().equals(keepId)) {
        current.setDefaultPolicy(false);
        policyRepository.save(current);
      }
    });
  }

  private void validate(WorkSchedulePolicy policy) {
    Map<DayOfWeek, UUID> week = new HashMap<>();
    week.put(DayOfWeek.MONDAY, policy.getMonShiftId());
    week.put(DayOfWeek.TUESDAY, policy.getTueShiftId());
    week.put(DayOfWeek.WEDNESDAY, policy.getWedShiftId());
    week.put(DayOfWeek.THURSDAY, policy.getThuShiftId());
    week.put(DayOfWeek.FRIDAY, policy.getFriShiftId());
    week.put(DayOfWeek.SATURDAY, policy.getSatShiftId());
    week.put(DayOfWeek.SUNDAY, policy.getSunShiftId());
    for (Map.Entry<DayOfWeek, UUID> entry : week.entrySet()) {
      requireActiveShift(entry.getValue(), entry.getKey());
    }
    requireActiveShift(policy.getSatAlternateShiftId(), DayOfWeek.SATURDAY);

    LocalDate anchor = policy.getRotationAnchorDate();
    if (policy.getSatAlternateShiftId() != null && anchor == null) {
      throw new BusinessRuleException(
          "Thứ 7 luân phiên cần rotationAnchorDate (mốc Thứ 7 bắt đầu chu kỳ chẵn)");
    }
    if (anchor != null && anchor.getDayOfWeek() != DayOfWeek.SATURDAY) {
      throw new BusinessRuleException("rotationAnchorDate phải là Thứ 7");
    }
    // anchor != null = bật luân phiên; tuần lẻ có thể nghỉ (satAlternateShiftId = null).
    if (anchor != null && policy.getSatShiftId() == null) {
      throw new BusinessRuleException("Thứ 7 luân phiên cần ca cho tuần chẵn (satShiftId)");
    }
  }

  private void requireActiveShift(UUID shiftId, DayOfWeek day) {
    if (shiftId == null) {
      return;
    }
    WorkShift shift = workShiftRepository.findById(shiftId)
        .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", shiftId));
    if (!shift.isActive()) {
      throw new BusinessRuleException(
          "Ca '" + shift.getName() + "' đã ngưng dùng — không thể đưa vào policy (" + day + ")");
    }
  }

  private Map<UUID, String> shiftNames() {
    Map<UUID, String> names = new HashMap<>();
    for (WorkShift shift : workShiftRepository.findAll()) {
      names.put(shift.getId(), shift.getName());
    }
    return names;
  }

  private WorkSchedulePolicy find(UUID id) {
    return policyRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Policy lịch", id));
  }
}
