-- Phase 4: bỏ vật lý hóa roster — lịch suy ra on-read từ policy tuần.
-- Chỉ giữ dòng sửa tay (MANUAL_OVERRIDE); mọi nguồn khác (POLICY_AUTO/ROTATION_GEN/
-- LEGACY_ASSIGNMENT) tái tạo được từ work_schedule_policies + employee_schedule_assignments.

delete from employee_shift_rosters where source <> 'MANUAL_OVERRIDE';
