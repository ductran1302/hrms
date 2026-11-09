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

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkSchedulePolicyService {

  private final WorkSchedulePolicyRepository policyRepository;
  private final EmployeeScheduleAssignmentRepository assignmentRepository;
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
    LocalDate today = LocalDate.now();
    WorkSchedulePolicy policy = policyRepository.findDefaultVersions(today).stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Policy lich mac dinh"));
    return WorkSchedulePolicyResponse.from(policy, shiftNames());
  }

  @Transactional(readOnly = true)
  public WorkSchedulePolicyResponse get(UUID id) {
    return WorkSchedulePolicyResponse.from(find(id), shiftNames());
  }

  @Transactional
  public WorkSchedulePolicyResponse create(WorkSchedulePolicyRequest request, String actor) {
    String name = request.name().trim();
    if (policyRepository.existsByName(name)) {
      throw new ConflictException("Ten policy lich da ton tai: " + name);
    }
    WorkSchedulePolicy policy = new WorkSchedulePolicy();
    apply(policy, request, name);
    policy.setPolicyGroupId(UUID.randomUUID());
    if (policy.getEffectiveFrom() == null) {
      policy.setEffectiveFrom(LocalDate.now());
    }
    policy.setVersionNo(1);
    validate(policy);
    if (policy.isDefaultPolicy()) {
      clearOtherDefaults(null);
    }
    policyRepository.saveAndFlush(policy);
    policy.setPolicyGroupId(policy.getId());
    policyRepository.saveAndFlush(policy);
    auditService.success("SCHEDULE_POLICY_CREATE", "WorkSchedulePolicy", policy.getId(),
        "name=" + name + " default=" + policy.isDefaultPolicy() + " by=" + actor);
    log.info("Tao policy lich '{}' (default={})", name, policy.isDefaultPolicy());
    return WorkSchedulePolicyResponse.from(policy, shiftNames());
  }

  @Transactional
  public WorkSchedulePolicyResponse update(UUID id, WorkSchedulePolicyRequest request, String actor) {
    WorkSchedulePolicy old = find(id);
    String name = request.name().trim();
    if (!name.equals(old.getName()) && policyRepository.existsByNameAndIdNot(name, id)) {
      throw new ConflictException("Ten policy lich da ton tai: " + name);
    }
    LocalDate newFrom = request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom();
    if (old.getEffectiveFrom() != null && !newFrom.isAfter(old.getEffectiveFrom())) {
      throw new BusinessRuleException("effectiveFrom phai sau ngay hieu luc cua version hien tai");
    }
    // overlap check among siblings
    for (WorkSchedulePolicy sibling : policyRepository.findByPolicyGroupIdOrderByEffectiveFromDesc(
        old.getPolicyGroupId())) {
      if (sibling.getId().equals(old.getId())) {
        continue;
      }
      if (overlap(sibling, newFrom, null)) {
        throw new BusinessRuleException("Khoang hieu luc trung voi version khac");
      }
    }
    WorkSchedulePolicy policy = new WorkSchedulePolicy();
    apply(policy, request, name);
    policy.setPolicyGroupId(old.getPolicyGroupId());
    policy.setEffectiveFrom(newFrom);
    policy.setEffectiveTo(null);
    policy.setVersionNo(old.getVersionNo() + 1);
    validate(policy);
    if (policy.isDefaultPolicy()) {
      old.setDefaultPolicy(false);
      clearOtherDefaults(null);
    }
    // cap old version
    LocalDate cap = newFrom.minusDays(1);
    if (old.getEffectiveTo() == null || old.getEffectiveTo().isAfter(cap)) {
      old.setEffectiveTo(cap);
    }
    policyRepository.saveAndFlush(old);
    policyRepository.saveAndFlush(policy);
    auditService.success("SCHEDULE_POLICY_UPDATE", "WorkSchedulePolicy", policy.getId(),
        "name=" + name + " default=" + policy.isDefaultPolicy() + " by=" + actor);
    log.info("Cap nhat policy lich '{}' (default={})", name, policy.isDefaultPolicy());
    return WorkSchedulePolicyResponse.from(policy, shiftNames());
  }

  @Transactional
  public void delete(UUID id, String actor) {
    WorkSchedulePolicy policy = find(id);
    if (policy.isDefaultPolicy()) {
      throw new BusinessRuleException(
          "Khong the xoa policy mac dinh - dat policy khac lam mac dinh truoc");
    }
    if (assignmentRepository.existsByPolicyId(policy.getPolicyGroupId())) {
      throw new BusinessRuleException(
          "Policy '" + policy.getName() + "' dang duoc gan nhan vien - dat active=false thay vi xoa");
    }
    policyRepository.delete(policy);
    auditService.success("SCHEDULE_POLICY_DELETE", "WorkSchedulePolicy", id,
        "name=" + policy.getName() + " by=" + actor);
    log.info("Xoa policy lich '{}'", policy.getName());
  }

  private boolean overlap(WorkSchedulePolicy sibling, LocalDate from, LocalDate to) {
    LocalDate sibFrom = sibling.getEffectiveFrom() == null ? LocalDate.MIN : sibling.getEffectiveFrom();
    LocalDate sibTo = sibling.getEffectiveTo() == null ? LocalDate.MAX : sibling.getEffectiveTo();
    LocalDate newTo = to == null ? LocalDate.MAX : to;
    return !from.isAfter(sibTo) && !newTo.isBefore(sibFrom);
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
    policy.setEffectiveFrom(request.effectiveFrom());
  }

  private void clearOtherDefaults(UUID keepId) {
    for (WorkSchedulePolicy p : policyRepository.findAllOrdered()) {
      if (p.isDefaultPolicy() && (keepId == null || !p.getId().equals(keepId))) {
        p.setDefaultPolicy(false);
        policyRepository.save(p);
      }
    }
  }

  private void validate(WorkSchedulePolicy policy) {
    // same as before
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
          "Thu 7 lu an phien can rotationAnchorDate");
    }
    if (anchor != null && anchor.getDayOfWeek() != DayOfWeek.SATURDAY) {
      throw new BusinessRuleException("rotationAnchorDate phai la Thu 7");
    }
    if (anchor != null && policy.getSatShiftId() == null) {
      throw new BusinessRuleException("Thu 7 lu an phien can ca cho tuan chan (satShiftId)");
    }
  }

  private void requireActiveShift(UUID shiftId, DayOfWeek day) {
    if (shiftId == null) {
      return;
    }
    WorkShift shift = workShiftRepository.findById(shiftId)
        .orElseThrow(() -> new ResourceNotFoundException("Ca lam viec", shiftId));
    if (!shift.isActive()) {
      throw new BusinessRuleException(
          "Ca '" + shift.getName() + "' da ngung dung - khong the dua vao policy (" + day + ")");
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
        .orElseThrow(() -> new ResourceNotFoundException("Policy lich", id));
  }
}