export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export type EmployeeStatusValue =
  | 'ACTIVE'
  | 'PROBATION'
  | 'ON_LEAVE'
  | 'TERMINATED';

export interface Employee {
  id: string;
  employeeCode: string;
  fullName: string;
  email: string | null;
  departmentId: string | null;
  departmentName: string | null;
  position: string | null;
  managerId: string | null;
  status: EmployeeStatusValue;
  hiredAt: string | null;
  birthDate: string | null;
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null;
  phone: string | null;
  nationalId: string | null;
  address: string | null;
  office: string | null;
  educationLevel: string | null;
  skills: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  emergencyContactRelation: string | null;
  photoUrl: string | null;
  calendarId: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface EmployeeCreate {
  employeeCode: string;
  fullName: string;
  email?: string;
  departmentId?: string;
  position?: string;
  managerId?: string;
  hiredAt?: string;
  birthDate?: string;
  gender?: string;
  phone?: string;
  nationalId?: string;
  address?: string;
  office?: string;
}

export interface EmployeeUpdate {
  fullName?: string;
  email?: string;
  departmentId?: string;
  clearDepartment?: boolean;
  position?: string;
  managerId?: string;
  clearManager?: boolean;
  status?: string;
  birthDate?: string;
  gender?: string;
  phone?: string;
  nationalId?: string;
  address?: string;
  office?: string;
  educationLevel?: string;
  skills?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  emergencyContactRelation?: string;
}

export interface EmployeeQuery {
  page?: number;
  size?: number;
  status?: string;
  departmentId?: string;
  position?: string;
  office?: string;
  q?: string;
}

export type EmployeeDocumentTypeValue =
  | 'CV'
  | 'CERTIFICATE'
  | 'ID_CARD'
  | 'CONTRACT'
  | 'MEDICAL'
  | 'OTHER';

export interface EmployeeDocument {
  id: string;
  employeeId: string;
  contractId?: string | null;
  docType: EmployeeDocumentTypeValue;
  fileName: string;
  contentType: string | null;
  fileSize: number;
  note: string | null;
  uploadedBy: string | null;
  uploadedAt: string;
}

export interface Department {
  id: string;
  code: string;
  name: string;
  parentId: string | null;
  managerId: string | null;
  managerName: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  employeeCount: number;
}

export interface DepartmentCreate {
  code: string;
  name: string;
  parentId?: string;
  managerId?: string;
}

export interface DepartmentUpdate {
  code?: string;
  name?: string;
  parentId?: string;
  managerId?: string;
  status?: string;
}

export interface DepartmentNode {
  id: string;
  code: string;
  name: string;
  status: string;
  managerName: string | null;
  children: DepartmentNode[];
}

export type ContractTypeValue =
  | 'PROBATION'
  | 'FULL_TIME'
  | 'PART_TIME'
  | 'INTERN'
  | 'OTHER';

export type ContractStatusValue = 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'TERMINATED';

/** Trạng thái hiệu lực tính theo ngày — tách khỏi status nghiệp vụ. */
export type ContractEffectiveStatusValue =
  | 'NOT_YET_EFFECTIVE'
  | 'IN_EFFECT'
  | 'ENDED'
  | 'EXPIRED'
  | 'TERMINATED';

export interface Contract {
  id: string;
  employeeId: string;
  employeeCode: string | null;
  employeeName: string | null;
  contractType: ContractTypeValue;
  startDate: string;
  endDate: string | null;
  position: string | null;
  /** null khi caller không phải ADMIN/GLOBAL_LEAD (backend che lương) */
  salary: number | null;
  signDate: string | null;
  note: string | null;
  status: ContractStatusValue;
  effectiveStatus?: ContractEffectiveStatusValue;
  replacesContractId?: string | null;
}

export interface ContractCreate {
  contractType: ContractTypeValue;
  startDate: string;
  endDate?: string;
  position?: string;
  salary?: number;
  signDate?: string;
  note?: string;
}

export interface ContractUpdate {
  contractType?: ContractTypeValue;
  startDate?: string;
  endDate?: string;
  position?: string;
  salary?: number;
  signDate?: string;
  note?: string;
  status?: ContractStatusValue;
}

export interface JobHistoryItem {
  id: string;
  employeeId: string;
  effectiveDate: string;
  field: string;
  oldValue: string | null;
  newValue: string | null;
  actorId: string | null;
  reason?: string | null;
}

export interface JobTitle {
  code: string;
  name: string;
  nameEn: string | null;
  category: string | null;
  sortOrder: number;
}

export interface OrgChartNode {
  id: string;
  employeeCode: string;
  fullName: string;
  position: string | null;
  departmentName: string | null;
  office: string | null;
  status: string;
  photoUrl: string | null;
  children: OrgChartNode[];
}

/** Thống kê đầu trang màn sơ đồ tổ chức (delta = so với 30 ngày trước). */
export interface OrgChartStats {
  totalEmployees: number;
  totalDepartments: number;
  totalManagers: number;
  activeEmployees: number;
  activePercent: number;
  employeeDelta: number;
  departmentDelta: number;
  managerDelta: number;
}

export interface AttendanceEmployee {
  employeeId: string;
  employeeCode: string;
  fullName: string;
  email: string | null;
  departmentId: string | null;
  departmentName: string | null;
  position: string | null;
  status: string;
  hiredAt: string | null;
  terminatedAt: string | null;
}

export type HolidayTypeValue = 'NATIONAL' | 'COMPANY' | 'OTHER';

export interface HolidayCalendar {
  id: string;
  code: string;
  name: string;
  country: string;
  description: string | null;
  status: string;
}

export interface HolidayCalendarCreate {
  code: string;
  name: string;
  country: string;
  description?: string;
}

export interface HolidayCalendarUpdate {
  name?: string;
  description?: string;
  status?: string;
}

export interface Holiday {
  id: string;
  calendarId: string;
  calendarCode: string;
  name: string;
  dateFrom: string;
  dateTo: string;
  year: number;
  type: HolidayTypeValue;
  halfDay: boolean;
  note: string | null;
}

export interface HolidayCreate {
  name: string;
  dateFrom: string;
  dateTo: string;
  type: HolidayTypeValue;
  halfDay?: boolean;
  note?: string;
}

export interface HolidayUpdate {
  name?: string;
  dateFrom?: string;
  dateTo?: string;
  type?: HolidayTypeValue;
  halfDay?: boolean;
  note?: string;
}

export type LeaveSourceValue = 'BASE' | 'SENIORITY' | 'POSITION' | 'BONUS' | 'CARRYOVER' | 'ADJUSTMENT';

export interface LeaveRoleQuota {
  role: string;
  bonusDays: number;
}

/** Quy tắc thâm niên: VN_TIERED = bậc luật VN, LINEAR = logic cũ (mỗi N năm). */
export type SeniorityRuleValue = 'VN_TIERED' | 'LINEAR';

/** Bậc thâm niên (VN_TIERED): áp dụng bonus khi nhân viên đã đủ minYears năm. */
export interface LeaveSeniorityTier {
  minYears: number;
  bonusDays: number;
}

export type PolicyStatusValue =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'ACTIVE'
  | 'EXPIRED'
  | 'CANCELLED';

/** Một phiên bản chính sách quỹ phép (effective-dated, 6 states). */
export interface LeavePolicy {
  id: string;
  year: number;
  versionNo: number;
  status: PolicyStatusValue;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  baseDays: number;
  seniorityStepYears: number;
  seniorityBonusDays: number;
  seniorityMaxBonus: number;
  carryoverMax: number;
  createdBy: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  activatedBy: string | null;
  activatedAt: string | null;
  roleQuotas: LeaveRoleQuota[];
  seniorityRule: SeniorityRuleValue;
  seniorityTiers: LeaveSeniorityTier[];
}

/** Tạo phiên bản mới / sửa DRAFT — null/undefined = mặc định (copy từ sourcePolicyId). */
export interface LeavePolicyCreate {
  year: number;
  baseDays?: number;
  seniorityStepYears?: number;
  seniorityBonusDays?: number;
  seniorityMaxBonus?: number;
  carryoverMax?: number;
  roleQuotas?: LeaveRoleQuota[];
  effectiveFrom?: string;
  sourcePolicyId?: string;
  seniorityRule?: SeniorityRuleValue;
  seniorityTiers?: LeaveSeniorityTier[];
}

export interface LeaveEntitlement {
  id: string;
  /** Năm sở hữu dòng (BONUS còn hạn có thể hiện ở quota năm sau — spill). */
  year: number;
  source: LeaveSourceValue;
  days: number;
  isAuto: boolean;
  reason: string | null;
  grantedBy: string | null;
  createdAt: string;
  /** Phiên bản chính sách tạo ra dòng auto (null với BONUS/ADJUSTMENT). */
  policyId: string | null;
  calculatedAt: string | null;
  /** Ngày hết hạn hiệu lực (null = dòng cũ không giới hạn). */
  expiresAt: string | null;
  /** Đã quá hạn → loại khỏi Tổng/Còn lại, chỉ hiện ở chi tiết. */
  expired: boolean;
}

export interface LeaveQuota {
  employeeId: string;
  employeeCode: string;
  fullName: string;
  departmentName: string | null;
  position: string | null;
  roles: string | null;
  status: string;
  baseDays: number;
  seniorityDays: number;
  roleDays: number;
  bonusDays: number;
  carryoverDays: number;
  adjustmentDays: number;
  totalDays: number;
  remainingDays: number;
  note: string | null;
  entitlements: LeaveEntitlement[];
  /** Range năm tài chính của year (yyyy-mm-dd) — FE label bộ chọn năm TC. */
  fyStart: string;
  fyEnd: string;
}

export interface LeaveGrant {
  source: 'BONUS' | 'CARRYOVER' | 'ADJUSTMENT';
  days: number;
  reason?: string;
  /** Chỉ BONUS: hạn tùy chỉnh (yyyy-mm-dd); bỏ trống = cuối năm tài chính. */
  expiresAt?: string;
}

export type SaturdayPolicyValue = 'OFF' | 'HALF' | 'ALTERNATING' | 'ALL';

/** Tham số global tính quỹ phép (Tab 1 màn Chính sách). */
export interface LeaveSettings {
  fyStartMonth: number;
  fyStartDay: number;
  periodStartDay: number;
  periodEndDay: number;
  saturdayPolicy: SaturdayPolicyValue;
  minWorkPercent: number;
  /** false = kích hoạt chính sách thẳng từ DRAFT, bỏ qua phê duyệt. */
  requirePolicyApproval: boolean;
}

export type WorkDaySourceValue = 'DEFAULT' | 'MANUAL';

/** Ngày công 1 kỳ — workDays null = chưa Tính lại/pre-fill. */
export interface LeaveWorkDay {
  id: string | null;
  periodStart: string;
  periodEnd: string;
  periodWorkingDays: number;
  workDays: number | null;
  source: WorkDaySourceValue | null;
}

export type ShiftTypeValue = 'FIXED' | 'FLEXIBLE';

export interface WorkShift {
  id: string;
  name: string;
  shiftType: ShiftTypeValue;
  startWorkTime: string; // 'HH:mm:ss'
  endWorkTime: string;
  startBreakTime: string | null;
  endBreakTime: string | null;
  crossesMidnight: boolean; // ca qua đêm: end < start (sang ngày hôm sau)
  workingTime: number; // phút
  breakingTime: number; // phút
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface WorkShiftUpsert {
  name: string;
  shiftType: ShiftTypeValue;
  startWorkTime: string;
  endWorkTime: string;
  startBreakTime?: string | null;
  endBreakTime?: string | null;
  workingTime: number; // phút
  breakingTime: number; // phút
  active?: boolean;
  clearBreak?: boolean; // PATCH: true = xóa khung giờ nghỉ (khi chuyển sang FLEXIBLE)
}

export interface ShiftAssignment {
  id: string;
  employeeId: string;
  employeeCode: string;
  fullName: string;
  departmentName: string | null;
  workShiftId: string;
  shiftName: string;
  shiftType: ShiftTypeValue;
  effectiveFrom: string; // 'YYYY-MM-DD'
  assignedBy: string | null;
  latest: boolean;
  createdAt: string;
}

export interface ShiftAssignmentCreate {
  employeeId: string;
  workShiftId: string;
  effectiveFrom: string;
}

// ===== Shift & Calendar (roster / schedule policy / assignment) =====

export type RosterSourceValue =
  | 'POLICY_AUTO'
  | 'ROTATION_GEN'
  | 'MANUAL_OVERRIDE'
  | 'LEGACY_ASSIGNMENT';

/** 1 ngày lịch làm việc của nhân sự. */
export interface RosterEntry {
  id: string;
  employeeId: string;
  employeeCode: string | null;
  fullName: string | null;
  workDate: string; // 'YYYY-MM-DD'
  shiftId: string | null;
  shiftName: string | null;
  shiftType: ShiftTypeValue | null;
  crossesMidnight: boolean;
  dayOff: boolean;
  source: RosterSourceValue;
  policyId: string | null;
  note: string | null;
}

export interface RosterOverride {
  shiftId?: string | null; // undefined = giữ nguyên; null = nghỉ
  note?: string | null;
}

/** Policy lịch tuần — shiftNames map UUID → tên ca (FE hiển thị không cần join). */
export interface WorkSchedulePolicy {
  id: string;
  policyGroupId: string;
  name: string;
  description: string | null;
  monShiftId: string | null;
  tueShiftId: string | null;
  wedShiftId: string | null;
  thuShiftId: string | null;
  friShiftId: string | null;
  satShiftId: string | null;
  satAlternateShiftId: string | null;
  sunShiftId: string | null;
  rotationAnchorDate: string | null; // 'YYYY-MM-DD', phải là Thứ 7 khi luân phiên
  defaultPolicy: boolean;
  active: boolean;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  shiftNames: Record<string, string>;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** PUT = thay thế toàn bộ (field null = nghỉ/không dùng). */
export interface WorkSchedulePolicyUpsert {
  name: string;
  description?: string | null;
  monShiftId?: string | null;
  tueShiftId?: string | null;
  wedShiftId?: string | null;
  thuShiftId?: string | null;
  friShiftId?: string | null;
  satShiftId?: string | null;
  satAlternateShiftId?: string | null;
  sunShiftId?: string | null;
  rotationAnchorDate?: string | null;
  defaultPolicy?: boolean;
  active?: boolean;
  effectiveFrom?: string | null;
}

/** Bản gán policy cho nhân sự — latest = đang áp dụng. */
export interface ScheduleAssignment {
  id: string;
  employeeId: string;
  employeeCode: string;
  fullName: string;
  policyId: string;
  policyName: string;
  effectiveFrom: string; // 'YYYY-MM-DD'
  assignedBy: string | null;
  latest: boolean;
  createdAt: string;
}

export interface ScheduleAssignmentCreate {
  employeeId: string;
  policyId: string;
  effectiveFrom: string;
}

export interface ApiError {
  timestamp?: string;
  status?: number;
  error?: string;
  code?: string;
  message?: string;
  path?: string;
  correlationId?: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface Account {
  username: string;
  fullName: string;
  email: string | null;
  enabled: boolean;
  roles: string[];
  createdTimestamp: number | null; // epoch ms (KC createdTimestamp)
  createdBy: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export type KeycloakSyncStatusValue = 'PENDING' | 'DONE' | 'FAILED';

export type KeycloakSyncOperationValue = 'CREATE_USER' | 'SET_ENABLED';

/** Task đồng bộ tài khoản Keycloak (màn vận hành P0-6) — ADMIN xem/retry task FAILED. */
export interface KeycloakSyncTask {
  id: string;
  operation: KeycloakSyncOperationValue;
  username: string;
  fullName: string | null;
  email: string | null;
  roleName: string | null;
  enabled: boolean | null;
  status: KeycloakSyncStatusValue;
  attempts: number;
  nextAttemptAt: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

// ========== OT ==========
export type OTRequestStatusValue =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'PENDING_CANCEL'
  | 'CANCELLED_APPROVED';

export interface OTConfig {
  id: string;
  dayStartTime: string;
  nightStartTime: string;
  postShiftBufferMinutes: number;
  preShiftBufferMinutes: number;
  breakDurationMinutes: number;
  breakDeductionThresholdMinutes: number;
  rateWeekday: number;
  rateWeekdayNight: number;
  rateWeekend: number;
  rateWeekendNight: number;
  rateHoliday: number;
  rateHolidayNight: number;
  status: string;
  version?: number;
}

export interface OTConfigUpdate {
  dayStartTime?: string;
  nightStartTime?: string;
  postShiftBufferMinutes?: number;
  preShiftBufferMinutes?: number;
  breakDurationMinutes?: number;
  breakDeductionThresholdMinutes?: number;
  rateWeekday?: number;
  rateWeekdayNight?: number;
  rateWeekend?: number;
  rateWeekendNight?: number;
  rateHoliday?: number;
  rateHolidayNight?: number;
}

export interface OTConfigHistory {
  id: string;
  configId: string;
  action: string;
  changesJson: string;
  performedBy: string;
  performedAt: string;
  dayStartTime: string | null;
  nightStartTime: string | null;
  postShiftBufferMinutes: number | null;
  preShiftBufferMinutes: number | null;
  breakDurationMinutes: number | null;
  breakDeductionThresholdMinutes: number | null;
  rateWeekday: string | null;
  rateWeekdayNight: string | null;
  rateWeekend: string | null;
  rateWeekendNight: string | null;
  rateHoliday: string | null;
  rateHolidayNight: string | null;
}

export interface OTRequestItem {
  id: string;
  requestId: string;
  otDate: string;
  startTime: string;
  endTime: string;
  dayType: string;
  dayHours: number;
  nightHours: number;
  actualStartTime: string | null;
  actualEndTime: string | null;
  approvedHours: number;
  version: number;
}

export interface OTRequest {
  id: string;
  requestCode: string;
  employeeId: string;
  employeeCode: string;
  fullName: string;
  departmentId: string | null;
  reason: string;
  totalHours: number;
  status: OTRequestStatusValue;
  approverId: string | null;
  approverName: string | null;
  approverEmployeeCode: string | null;
  approvedAt: string | null;
  rejectionReason: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  items: OTRequestItem[];
}

export interface OTRequestItemCreate {
  startTime: string;
  endTime: string;
}

export interface OTRequestCreate {
  reason: string;
  items?: OTRequestItemCreate[];
  startDate?: string;
  endDate?: string;
  defaultStartTime?: string;
  defaultEndTime?: string;
}

/** Người duyệt trực tiếp (manager) của 1 nhân viên. */
export interface OTApproverInfo {
  fullName: string;
  employeeCode: string;
  position: string | null;
}
export interface AuditLog {
  id: string;
  occurredAt: string;
  actor: string;
  action: string;
  targetType: string | null;
  targetId: string | null;
  outcome: string;
  detail: string | null;
  correlationId: string | null;
  clientIp: string | null;
}